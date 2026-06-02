package com.example.myapplication.classic
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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.BufferOverflow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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


        fun cancelDiscovery(context: Context) {
            try {
                val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
                val adapter = mgr.adapter ?: return
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED) {
                    if (adapter.isDiscovering) adapter.cancelDiscovery()
                }
            } catch (_: Exception) {}
        }
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

    private val _rawBytes = MutableSharedFlow<ByteArray>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val rawBytes: SharedFlow<ByteArray> = _rawBytes.asSharedFlow()

    @Volatile private var isTransferMode = false

    fun setTransferMode(enabled: Boolean) { isTransferMode = enabled }

    private fun logEvent(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            .format(Date())
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
    private val reconnectScheduler = ReconnectScheduler(
        scope = managerScope,
        maxAttempts = RECONNECT_MAX_ATTEMPTS,
        getIsIntentionalDisconnect = { isIntentionalDisconnect },
        onUpdateState = ::updateState,
        onLogEvent = ::logEvent,
        onDoConnect = ::doConnect
    )

    private var connectJob:           Job? = null
    private var readJob:              Job? = null
    private var connectionTimeoutJob: Job? = null
    private var inactivityTimeoutJob: Job? = null


    // ─── Reconnect State ───────────────────────────────────
    private var lastDevice:            BluetoothDevice? = null
    @Volatile private var isIntentionalDisconnect = false





    // ─── Read State ────────────────────────────────────────
    @Volatile private var lastReadTime = 0L





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
        reconnectScheduler.reset()
        reconnectScheduler.cancelReconnect()
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
                cancelDiscovery(appContext)
                delay(500)

                try {
                    if (device.bondState == BluetoothDevice.BOND_BONDED) {
                        delay(1200)
                    }
                } catch (e: SecurityException) {
                    // Permission was denied or missing.
                    // Log the error or gracefully degrade the feature.
                    e.printStackTrace()
                }
                val socket = SocketFactory.createSocket(device)
                bluetoothSocket = socket
                inputStream  = socket.inputStream
                outputStream = socket.outputStream
                onConnected()
            } catch (_: Exception) {
                cancelConnectionTimeout()
                if (isActive &&
                    _state.value !is ClassicState.RECONNECTING &&
                    _state.value !is ClassicState.FAILED) {
                    reconnectScheduler.handleConnectionFailure(device)
                }
            }
        }
    }





    private fun onConnected() {
        logEvent("Connected to $connectedDeviceName ($connectedDeviceAddress)")
        cancelConnectionTimeout()
        reconnectScheduler.reset()
        lastReadTime        = System.currentTimeMillis()

        parser.reset()
        parser.onMessageParsed = { msg ->
            _messages.tryEmit(msg)
        }

        writeQueue = WriteQueue(managerScope, WRITE_TIMEOUT_MS).also { q ->
            q.onWriteError = {
                if (_state.value == ClassicState.CONNECTED) {
                    forceDisconnect(FailureReason.Timeout)
                    lastDevice?.let { reconnectScheduler.scheduleReconnect(it) }
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


            delay(CONNECTION_TIMEOUT_MS)
            logEvent("Connection timed out after ${CONNECTION_TIMEOUT_MS}ms")
            if (_state.value == ClassicState.CONNECTING) {
                disconnectInternal()        // clean up silently
                val dev = lastDevice ?: return@launch
                reconnectScheduler.handleConnectionFailure(dev)
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
                    lastDevice?.let { reconnectScheduler.scheduleReconnect(it) }
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
                        lastDevice?.let { reconnectScheduler.scheduleReconnect(it) }
                    }
                    break
                }
            }
        }
    }

    // ─── Send with Write Timeout ───────────────────────────

    fun sendData(data: ByteArray, onResult: ((WriteQueue.WriteResult) -> Unit)? = null): Boolean {
        return writeQueue?.enqueue(data, onResult) ?: false
    }


    // ─── Reconnect ─────────────────────────────────────────

    // ─── Public: Manual Disconnect ─────────────────────────
    @Synchronized
    fun disconnect() {
        isIntentionalDisconnect = true
        reconnectScheduler.cancelReconnect()
        cancelDiscovery(appContext)
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



    private fun resolveAddress(device: BluetoothDevice): String = device.address

    private fun resolveDeviceName(device: BluetoothDevice): String = try {
        if (!hasConnectPermission()) "Unknown" else device.name ?: "Unknown"
    } catch (_: SecurityException) { "Unknown" }

    fun isConnected() = _state.value == ClassicState.CONNECTED
    fun resetToIdle() {
        if (_state.value == ClassicState.DISCONNECTED || _state.value is ClassicState.FAILED) {
            updateState(ClassicState.IDLE)
        }
    }
}