package com.example.myapplication
import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

import kotlinx.coroutines.channels.BufferOverflow

data class ConnectionInfo(
    val state: ClassicState,
    val address: String = "",
    val deviceName: String = ""
)

class ClassicConnectionManager(private val appContext: Context) {

    // ─── Timeout / Reconnect Constants ─────────────────────
    companion object {
        private const val CONNECTION_TIMEOUT_MS    = 10_000L
        private const val READ_INACTIVITY_MS       = 30_000L
        private const val READ_INACTIVITY_CHECK_MS =  5_000L
        private const val WRITE_TIMEOUT_MS         =  5_000L
        const val RECONNECT_MAX_ATTEMPTS   =  3
        private const val FAILURE_COOLDOWN_MS      = 60_000L
    }

    // ─── State ─────────────────────────────────────────────


    private val _state =
        MutableStateFlow<ClassicState>(
            ClassicState.IDLE
        )

    val state: StateFlow<ClassicState> =
        _state.asStateFlow()

    var connectedDeviceAddress: String? = null
        private set
    var connectedDeviceName: String? = null
        private set

    private val _connectionInfo = MutableStateFlow(ConnectionInfo(ClassicState.IDLE))
    val connectionInfo: StateFlow<ConnectionInfo> = _connectionInfo.asStateFlow()

    private val _messages = MutableSharedFlow<ClassicMessage>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages: SharedFlow<ClassicMessage> = _messages.asSharedFlow()
    private val _events = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<String> = _events.asSharedFlow()
    // ─── Raw bytes (used by FileTransferManager) ────────────────────────
    // ─── Raw byte stream (consumed by ClassicFileTransferManager) ──────────
    private val _rawBytes = MutableSharedFlow<ByteArray>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val rawBytes: SharedFlow<ByteArray> = _rawBytes.asSharedFlow()

    @Volatile private var isTransferMode = false

    fun setTransferMode(enabled: Boolean) { isTransferMode = enabled }

    private fun logEvent(msg: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        _events.tryEmit("[$time] $msg")
    }
    // ─── Sockets / Streams ─────────────────────────────────
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    // ─── Parser / Write Queue ──────────────────────────────
    private val parser: MessageParser = NewlineMessageParser()
    private var writeQueue: WriteQueue? = null

    // ─── Coroutine Jobs ────────────────────────────────────
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connectJob:           Job? = null
    private var readJob:              Job? = null
    private var connectionTimeoutJob: Job? = null
    private var inactivityTimeoutJob: Job? = null
    private var reconnectJob:         Job? = null

    // ─── Reconnect State ───────────────────────────────────
    private var lastDevice:            BluetoothDevice? = null
    @Volatile private var isIntentionalDisconnect = false
    private val _reconnectAttempts = java.util.concurrent.atomic.AtomicInteger(0)
    val reconnectAttempts: Int get() = _reconnectAttempts.get()
    private val consecutiveFailures = java.util.concurrent.atomic.AtomicInteger(0)
    @Volatile private var lastFailureTime     = 0L

    // ─── Read State ────────────────────────────────────────
    @Volatile private var lastReadTime = 0L

    private val sppUUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")



    // ─── State Helper ──────────────────────────────────────
    private fun updateState(state: ClassicState) {

        _state.value = state

        _connectionInfo.value = ConnectionInfo(
            state      = state,
            address    = connectedDeviceAddress ?: "",
            deviceName = connectedDeviceName ?: ""
        )
    }

    private fun forceDisconnect(

        reason: FailureReason? = null

    ) {
        logEvent(if (reason == null) "Disconnected" else "Failed: $reason")
        disconnectInternal()

        updateState(
            if (reason == null) {
                ClassicState.DISCONNECTED
            } else {
                ClassicState.FAILED(reason)
            }
        )
    }

    // ─── Public: Manual Connect ────────────────────────────
    @Synchronized
    fun connect(device: BluetoothDevice) {
        isIntentionalDisconnect = false
        _reconnectAttempts.set(0)
        consecutiveFailures.set(0)
        cancelReconnect()
        doConnect(device)
    }

    // ─── Internal Connect (also used by reconnect) ─────────
    private fun doConnect(device: BluetoothDevice) {
        if (_state.value== ClassicState.CONNECTING ||
            _state.value == ClassicState.CONNECTED) return

        disconnectInternal()
        lastDevice = device
        connectedDeviceAddress = resolveAddress(device)
        connectedDeviceName    = resolveDeviceName(device)
        updateState(ClassicState.CONNECTING)
        startConnectionTimeout()

        connectJob = managerScope.launch {
            try {
                if (!hasConnectPermission()) {
                    cancelConnectionTimeout()
                    updateState(ClassicState.FAILED(FailureReason.PermissionDenied))
                    return@launch
                }
                cancelDiscoveryIfActive()
                val socket = createSocket(device)
                bluetoothSocket = socket
                inputStream  = socket.inputStream
                outputStream = socket.outputStream
                onConnected()
            } catch (_: Exception) {
                cancelConnectionTimeout()
                if (isActive &&
                    _state.value !is ClassicState.RECONNECTING &&
                    _state.value !is ClassicState.FAILED) {
                    handleConnectionFailure()
                }
            }
        }
    }
    private fun log(message: String) {
        android.util.Log.d(
            "ClassicConnectionManager",
            message
        )
    }
    private fun createSocket(
        device: BluetoothDevice
    ): BluetoothSocket {

        return try {

            val socket =
                device.createRfcommSocketToServiceRecord(sppUUID)

            socket.connect()

            socket

        } catch (primaryError: IOException) {
            log("Primary socket failed: ${primaryError.message}")
            try { device.createRfcommSocketToServiceRecord(sppUUID).close() }
            catch (_: IOException) {}                    // ← close the leaked socket
            val fallbackSocket = createFallbackSocket(device)
            fallbackSocket.connect()
            fallbackSocket
        }
    }

    private fun createFallbackSocket(
        device: BluetoothDevice
    ): BluetoothSocket {

        return device.javaClass
            .getMethod(
                "createRfcommSocket",
                Int::class.javaPrimitiveType
            )
            .invoke(device, 1) as BluetoothSocket
    }

    private fun onConnected() {
        logEvent("Connected to $connectedDeviceName ($connectedDeviceAddress)")
        cancelConnectionTimeout()
        consecutiveFailures.set(0)
        _reconnectAttempts.set(0)
        lastReadTime        = System.currentTimeMillis()

        parser.reset()
        parser.onMessageParsed = { msg ->
            _messages.tryEmit(msg)
        }

        writeQueue = WriteQueue(managerScope, WRITE_TIMEOUT_MS).also { q ->
            q.onWriteError = {
                if (_state.value == ClassicState.CONNECTED) {
                    forceDisconnect(FailureReason.Timeout)
                    scheduleReconnect()
                }
            }
            q.start { outputStream }
        }

        updateState(ClassicState.CONNECTED)
        startReading()
        startInactivityTimeout()
    }

    // ─── Timeout: Connection ───────────────────────────────
    private fun startConnectionTimeout() {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = managerScope.launch {
            logEvent("Connection timed out after ${CONNECTION_TIMEOUT_MS}ms")

            delay(CONNECTION_TIMEOUT_MS)
            if (_state.value == ClassicState.CONNECTING) {
                disconnectInternal()        // clean up silently
                handleConnectionFailure()   // schedules reconnect; only reaches FAILED when attempts exhausted
            }
        }
    }

    private fun cancelConnectionTimeout() {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = null
    }

    // ─── Timeout: Read Inactivity ──────────────────────────
    private fun startInactivityTimeout() {
        inactivityTimeoutJob?.cancel()
        inactivityTimeoutJob = managerScope.launch {
            while (isActive && _state.value == ClassicState.CONNECTED) {
                delay(READ_INACTIVITY_CHECK_MS)
                if (System.currentTimeMillis() - lastReadTime >= READ_INACTIVITY_MS) {
                    forceDisconnect(FailureReason.Timeout)
                    scheduleReconnect()
                    break
                }
            }
        }
    }

    // ─── Read Loop ─────────────────────────────────────────
    private fun startReading() {
        readJob = managerScope.launch {
            val buffer = ByteArray(1024)
            while (isActive && _state.value == ClassicState.CONNECTED) {
                try {
                    val bytes = inputStream?.read(buffer) ?: break
                    if (bytes > 0) {
                        lastReadTime = System.currentTimeMillis()
                        _rawBytes.tryEmit(buffer.copyOfRange(0, bytes))
                        if (!isTransferMode) parser.feed(buffer, bytes)
                    }
                } catch (_: IOException) {
                    if (!isIntentionalDisconnect &&
                        _state.value == ClassicState.CONNECTED) {
                        forceDisconnect()
                        scheduleReconnect()
                    }
                    break
                }
            }
        }
    }

    // ─── Send with Write Timeout ───────────────────────────
    // Change return type from Unit to Boolean:
    fun sendData(data: ByteArray, onResult: ((WriteQueue.WriteResult) -> Unit)? = null): Boolean {
        return writeQueue?.enqueue(data, onResult) ?: false
    }


    // ─── Reconnect ─────────────────────────────────────────
    private fun handleConnectionFailure() {
        consecutiveFailures.incrementAndGet()
        lastFailureTime = System.currentTimeMillis()
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (isIntentionalDisconnect)         return
        if (reconnectJob?.isActive == true)  return
        if (reconnectAttempts >= RECONNECT_MAX_ATTEMPTS) {

            updateState(
                ClassicState.FAILED(
                    FailureReason.MaxReconnectAttempts
                )
            )

            return
        }

        // Cooldown after repeated failure bursts
        if (
            consecutiveFailures.get() >= RECONNECT_MAX_ATTEMPTS &&
            System.currentTimeMillis() - lastFailureTime < FAILURE_COOLDOWN_MS
        ) {

            updateState(
                ClassicState.FAILED(
                    FailureReason.MaxReconnectAttempts
                )
            )

            return
        }

        val device = lastDevice ?: return

        reconnectJob = managerScope.launch {
            delay(600)
            val attempt= _reconnectAttempts.incrementAndGet()
            val delay = when (attempt) {
                1 -> 800L
                2 -> 1600L
                else -> 3000L
            }
            updateState(ClassicState.RECONNECTING(attempt))
            logEvent("Reconnecting… attempt $reconnectAttempts/$RECONNECT_MAX_ATTEMPTS")
            delay(delay)
            if (!isIntentionalDisconnect && isActive) doConnect(device)
        }
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    // ─── Public: Manual Disconnect ─────────────────────────
    @Synchronized
    fun disconnect() {
        isIntentionalDisconnect = true
        cancelReconnect()
        cancelDiscoveryIfActive()
        forceDisconnect()
    }

    fun destroy() {
        isIntentionalDisconnect = true
        disconnectInternal()
        managerScope.cancel()
    }

    // ─── Internal Cleanup ──────────────────────────────────
    private fun disconnectInternal() {
        cancelConnectionTimeout()
        inactivityTimeoutJob?.cancel(); inactivityTimeoutJob = null
        try { bluetoothSocket?.close() } catch (_: IOException) {}
        connectJob?.cancel()
        readJob?.cancel()
        try { inputStream?.close()    } catch (_: IOException) {}
        try { outputStream?.close()   } catch (_: IOException) {}


        bluetoothSocket = null
        inputStream     = null
        outputStream    = null
        connectedDeviceAddress = null
        connectedDeviceName    = null

        writeQueue?.stop()
        writeQueue = null
        parser.onMessageParsed = null
        parser.reset()
    }

    // ─── Permission / Device Helpers ───────────────────────
    private fun hasConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun cancelDiscoveryIfActive() {
        try {
            val adapter = (appContext.getSystemService(Context.BLUETOOTH_SERVICE)
                    as BluetoothManager).adapter
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(
                    appContext, Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED) {
                if (adapter.isDiscovering) adapter.cancelDiscovery()
            }
        } catch (_: SecurityException) {}
    }

    private fun resolveAddress(device: BluetoothDevice): String = device.address

    private fun resolveDeviceName(device: BluetoothDevice): String = try {
        if (!hasConnectPermission()) "Unknown" else device.name ?: "Unknown"
    } catch (_: SecurityException) { "Unknown" }

    fun isConnected() = _state.value == ClassicState.CONNECTED

}