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

class ClassicConnectionManager(private val appContext: Context) {

    // ─── Timeout / Reconnect Constants ─────────────────────
    companion object {
        private const val CONNECTION_TIMEOUT_MS    = 10_000L
        private const val READ_INACTIVITY_MS       = 30_000L
        private const val READ_INACTIVITY_CHECK_MS =  5_000L
        private const val WRITE_TIMEOUT_MS         =  5_000L
        private const val RECONNECT_BASE_DELAY_MS  =  1_000L
        private const val RECONNECT_MAX_DELAY_MS   = 30_000L
        private const val RECONNECT_MAX_ATTEMPTS   =  5
        private const val FAILURE_COOLDOWN_MS      = 60_000L
    }

    // ─── State ─────────────────────────────────────────────
    @Volatile var currentState = ClassicState.IDLE
        private set

    var connectedDeviceAddress: String? = null
        private set
    var connectedDeviceName: String? = null
        private set

    var onStateChanged: ((ClassicState, String) -> Unit)? = null
    var onMessageReceived: ((ClassicMessage) -> Unit)? = null

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
    @Volatile var reconnectAttempts = 0
        private set
    @Volatile private var consecutiveFailures = 0
    @Volatile private var lastFailureTime     = 0L

    // ─── Read State ────────────────────────────────────────
    @Volatile private var lastReadTime = 0L
    private val messageBuffer = StringBuilder()
    private val sppUUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    // ─── State Helper ──────────────────────────────────────
    private fun updateState(state: ClassicState) {
        currentState = state
        onStateChanged?.invoke(state, connectedDeviceAddress ?: "")
    }

    private fun forceDisconnect(state: ClassicState) {
        disconnectInternal()
        updateState(state)
    }

    // ─── Public: Manual Connect ────────────────────────────
    @Synchronized
    fun connect(device: BluetoothDevice) {
        isIntentionalDisconnect = false
        reconnectAttempts  = 0
        consecutiveFailures = 0
        cancelReconnect()
        doConnect(device)
    }

    // ─── Internal Connect (also used by reconnect) ─────────
    private fun doConnect(device: BluetoothDevice) {
        if (currentState == ClassicState.CONNECTING ||
            currentState == ClassicState.CONNECTED) return

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
                    updateState(ClassicState.FAILED)
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
                if (isActive) handleConnectionFailure()
            }
        }
    }

    private fun createSocket(device: BluetoothDevice): BluetoothSocket {
        return try {
            val s = device.createRfcommSocketToServiceRecord(sppUUID)
            s.connect()
            s
        } catch (_: IOException) {
            @Suppress("UNCHECKED_CAST")
            val fallback = device.javaClass
                .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                .invoke(device, 1) as BluetoothSocket
            fallback.connect()
            fallback
        }
    }

    private fun onConnected() {
        cancelConnectionTimeout()
        consecutiveFailures = 0
        reconnectAttempts   = 0
        lastReadTime        = System.currentTimeMillis()

        parser.reset()
        parser.onMessageParsed = { onMessageReceived?.invoke(it) }

        writeQueue = WriteQueue(managerScope, WRITE_TIMEOUT_MS).also { q ->
            q.onWriteError = {
                if (currentState == ClassicState.CONNECTED) {
                    forceDisconnect(ClassicState.TIMEOUT)
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
            delay(CONNECTION_TIMEOUT_MS)
            if (currentState == ClassicState.CONNECTING) {
                forceDisconnect(ClassicState.TIMEOUT)
                handleConnectionFailure()
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
            while (isActive && currentState == ClassicState.CONNECTED) {
                delay(READ_INACTIVITY_CHECK_MS)
                if (System.currentTimeMillis() - lastReadTime >= READ_INACTIVITY_MS) {
                    forceDisconnect(ClassicState.TIMEOUT)
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
            while (isActive && currentState == ClassicState.CONNECTED) {
                try {
                    val bytes = inputStream?.read(buffer) ?: break
                    if (bytes > 0) {
                        lastReadTime = System.currentTimeMillis()
                        parser.feed(buffer, bytes)        // ← replaces all messageBuffer logic
                    }
                } catch (_: IOException) {
                    if (!isIntentionalDisconnect &&
                        currentState == ClassicState.CONNECTED) {
                        forceDisconnect(ClassicState.DISCONNECTED)
                        scheduleReconnect()
                    }
                    break
                }
            }
        }
    }

    // ─── Send with Write Timeout ───────────────────────────
    @Suppress("unused")
    fun sendData(data: ByteArray, onResult: ((WriteQueue.WriteResult) -> Unit)? = null) {
        if (writeQueue?.enqueue(data, onResult) == false) {
            onResult?.invoke(WriteQueue.WriteResult.Failure("Queue full"))
        }
    }


    // ─── Reconnect ─────────────────────────────────────────
    private fun handleConnectionFailure() {
        consecutiveFailures++
        lastFailureTime = System.currentTimeMillis()
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (isIntentionalDisconnect)         return
        if (reconnectJob?.isActive == true)  return
        if (reconnectAttempts >= RECONNECT_MAX_ATTEMPTS) return

        // Cooldown after repeated failure bursts
        if (consecutiveFailures >= RECONNECT_MAX_ATTEMPTS &&
            System.currentTimeMillis() - lastFailureTime < FAILURE_COOLDOWN_MS) return

        val device = lastDevice ?: return

        reconnectJob = managerScope.launch {
            reconnectAttempts++
            val delay = minOf(
                RECONNECT_BASE_DELAY_MS * (1L shl (reconnectAttempts - 1)),
                RECONNECT_MAX_DELAY_MS
            )
            updateState(ClassicState.RECONNECTING)
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
        forceDisconnect(ClassicState.DISCONNECTED)
    }

    fun destroy() {
        isIntentionalDisconnect = true
        managerScope.cancel()
    }

    // ─── Internal Cleanup ──────────────────────────────────
    private fun disconnectInternal() {
        cancelConnectionTimeout()
        inactivityTimeoutJob?.cancel(); inactivityTimeoutJob = null
        connectJob?.cancel();           connectJob           = null
        readJob?.cancel();              readJob              = null

        try { inputStream?.close()    } catch (_: IOException) {}
        try { outputStream?.close()   } catch (_: IOException) {}
        try { bluetoothSocket?.close()} catch (_: IOException) {}

        bluetoothSocket = null
        inputStream     = null
        outputStream    = null
        connectedDeviceAddress = null
        connectedDeviceName    = null
        messageBuffer.clear()
        writeQueue?.stop()
        writeQueue = null
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

    fun isConnected() = currentState == ClassicState.CONNECTED
}