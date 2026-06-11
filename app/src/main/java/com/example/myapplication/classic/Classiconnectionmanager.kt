package com.example.myapplication.classic
import android.Manifest

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.example.myapplication.classic.helpers.BatteryErrorProfile
import com.example.myapplication.classic.helpers.BluetoothBatteryMonitor
import com.example.myapplication.models.ClassicState
import com.example.myapplication.classic.helpers.ConnectionSecurity
import com.example.myapplication.models.FailureReason
import com.example.myapplication.classic.messages.ClassicMessage
import com.example.myapplication.classic.messages.MessageParser
import com.example.myapplication.classic.messages.NewlineMessageParser
import com.example.myapplication.classic.messages.ReconnectScheduler
import com.example.myapplication.classic.messages.SocketFactory
import com.example.myapplication.classic.messages.SocketResult
import com.example.myapplication.classic.messages.WriteQueue
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
    val deviceName: String = "",
    val batteryLevel: Int = -1,
    val batteryError: BatteryErrorProfile = BatteryErrorProfile.None
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

    // Add this alongside your other properties inside ClassicConnectionManager
    private val batteryMonitor = BluetoothBatteryMonitor(appContext)

    // Expose the raw battery stream externally if needed


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
    private var connectionSecurity =
        ConnectionSecurity.UNKNOWN
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
    private val bluetoothReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED == intent?.action) {
                val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                val prevBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)

                // User hit 'Cancel' or 'Reject' on the OS pairing window popup
                if (bondState == BluetoothDevice.BOND_NONE && prevBondState == BluetoothDevice.BOND_BONDING) {
                    Log.d("BLE_RECONNECT_BUG", "Pairing rejected by user system window. Killing threads.")
                    disconnectInternal()
                    updateState(ClassicState.DISCONNECTED)
                }
            }
        }
    }

    fun registerPairingReceiver() {
        val filter = android.content.IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        appContext.registerReceiver(bluetoothReceiver, filter)
    }

    fun unregisterPairingReceiver() {
        try {
            appContext.unregisterReceiver(bluetoothReceiver)
        } catch (_: java.lang.IllegalArgumentException) {}
    }

    @Volatile private var isIntentionalDisconnect = false





    // ─── Read State ────────────────────────────────────────
    @Volatile private var lastReadTime = 0L





    // ─── State Helper ──────────────────────────────────────
    private fun updateState(state: ClassicState) {

        _state.value = state

        _connectionInfo.value = ConnectionInfo(
            state      = state,
            address    = connectedDeviceAddress ?: "",
            deviceName = connectedDeviceName ?: "",
            batteryLevel = batteryMonitor.batteryLevel.value,
            batteryError = batteryMonitor.errorProfile.value
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
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Synchronized

    fun connect(device: BluetoothDevice) {
        Log.d("CLASSIC_RECONNECT_BUG", "connect() triggered for Device: ${device.address}, BondState: ${device.bondState}")

        // Reset all disconnect states and schedulers clean before attempting a fresh run
        isIntentionalDisconnect = false
        reconnectScheduler.reset()
        reconnectScheduler.cancelReconnect()

        // Clean execution: Let both paired (12) and unpaired (10) devices hit the socket engine!
        doConnect(device)
    }




    // ─── Internal Connect (also used by reconnect) ─────────
    private fun doConnect(device: BluetoothDevice) {
        if (_state.value == ClassicState.CONNECTING ||
            _state.value == ClassicState.CONNECTED) return

        // 1. Clean up old resources (this sets isIntentionalDisconnect = true internally)
        disconnectInternal()

        // 🛡️ FIX A: RESET the flag here because this is a deliberate, manual connection attempt!
        isIntentionalDisconnect = false

        lastDevice = device
        connectedDeviceAddress = resolveAddress(device)
        connectedDeviceName    = resolveDeviceName(device)
        updateState(ClassicState.CONNECTING)
        startConnectionTimeout()

        connectJob = managerScope.launch {
            val initialBondState = try {
                if (hasConnectPermission()) device.bondState else BluetoothDevice.BOND_NONE
            } catch (_: SecurityException) {
                BluetoothDevice.BOND_NONE
            }

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
                    e.printStackTrace()
                }

                val hasPermission = ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED

                val socketResult = if (hasPermission) {
                    SocketFactory.createSocket(device, appContext)
                } else {
                    SocketResult(
                        socket = null,
                        security = ConnectionSecurity.UNKNOWN,
                        userErrorMessage = "Connection failed because the app lacks Bluetooth permissions.",
                        technicalLog = "SecurityException blocked: Manifest.permission.BLUETOOTH_CONNECT missing."
                    )
                }

                bluetoothSocket = socketResult.socket
                connectionSecurity = socketResult.security ?: ConnectionSecurity.UNKNOWN
                inputStream = socketResult.socket?.inputStream
                outputStream = socketResult.socket?.outputStream

                if (socketResult.socket == null) {
                    cancelConnectionTimeout()

                    // FIX B: Only abort retry loops if the user actually clicked 'Cancel' on the system pop-up dialog
                    val currentBond = try { if (hasConnectPermission()) device.bondState else BluetoothDevice.BOND_NONE } catch (_: SecurityException) { BluetoothDevice.BOND_NONE }
                    val isUserRejection = isIntentionalDisconnect || currentBond == BluetoothDevice.BOND_NONE

                    if (isUserRejection) {
                        Log.d("BLE_RECONNECT_BUG", "Socket is null due to pairing rejection/cancellation. Blocking retry.")
                        reconnectScheduler.cancelReconnect()
                        reconnectScheduler.reset()
                        updateState(ClassicState.FAILED(FailureReason.PairingRejected))
                    } else {
                        updateState(ClassicState.FAILED(FailureReason.Unknown(socketResult.userErrorMessage ?: "Connection failed")))
                        if (isActive) reconnectScheduler.handleConnectionFailure(device)
                    }
                    return@launch
                }

                onConnected()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (isIntentionalDisconnect) {
                    reconnectScheduler.cancelReconnect()
                    return@launch
                }

                val currentBondState = try {
                    if (hasConnectPermission()) device.bondState else BluetoothDevice.BOND_NONE
                } catch (_: SecurityException) {
                    BluetoothDevice.BOND_NONE
                }

                val errorMessage = e.message?.lowercase() ?: ""

                if (currentBondState == BluetoothDevice.BOND_BONDING ||
                    (initialBondState != BluetoothDevice.BOND_BONDED && currentBondState == BluetoothDevice.BOND_NONE)
                ) {
                    cancelConnectionTimeout()
                    reconnectScheduler.cancelReconnect()
                    reconnectScheduler.reset()
                    updateState(ClassicState.FAILED(FailureReason.BondingFailed))
                    logEvent("Pairing/Bonding failed before connection could be established.")
                    return@launch
                }

                val failureReason = when {
                    "authentication" in errorMessage || "incorrect pin" in errorMessage || "pin" in errorMessage ->
                        FailureReason.AuthenticationFailed
                    "bond" in errorMessage -> FailureReason.BondingFailed
                    "pair" in errorMessage || "cancel" in errorMessage -> FailureReason.PairingRejected
                    "refused" in errorMessage -> FailureReason.DeviceRefusedConnection
                    else -> FailureReason.Unknown(e.message ?: "Unknown connection error")
                }

                cancelConnectionTimeout()
                updateState(ClassicState.FAILED(failureReason))

                if (isActive) {
                    val isTerminal = failureReason == FailureReason.PairingRejected ||
                            failureReason == FailureReason.AuthenticationFailed ||
                            failureReason == FailureReason.BondingFailed ||
                            failureReason == FailureReason.DeviceRefusedConnection ||
                            currentBondState == BluetoothDevice.BOND_NONE

                    if (isTerminal) {
                        Log.d("BLE_RECONNECT_BUG", "Terminal failure hit ($failureReason). Killing schedulers permanently.")
                        reconnectScheduler.cancelReconnect()
                        reconnectScheduler.reset()
                    } else {
                        reconnectScheduler.handleConnectionFailure(device)
                    }
                }

                logEvent(
                    when (failureReason) {
                        FailureReason.AuthenticationFailed -> "Authentication failed. Possible incorrect PIN/passkey."
                        FailureReason.PairingRejected -> "Pairing request was rejected."
                        FailureReason.BondingFailed -> "Bluetooth bonding failed."
                        FailureReason.DeviceRefusedConnection -> "Remote device refused the connection."
                        is FailureReason.Unknown -> "Connection failed: ${failureReason.message}"
                        else -> "Connection failed."
                    }
                )
            }
        }
    }







    private fun onConnected() {
        logEvent("Connected to $connectedDeviceName ($connectedDeviceAddress)")
        cancelConnectionTimeout()
        lastDevice?.let { device ->
            batteryMonitor.startMonitoring(device)

            // Also fire off a quick lifecycle collection to sync the internal flow with connection info
            managerScope.launch {
                batteryMonitor.batteryLevel.collect {
                    // Forces the UI state model to update whenever a new percentage lands
                    updateState(_state.value)
                }

            }
            managerScope.launch {
                // Triggers state rebuild if an API block or stale profile modifies the state
                batteryMonitor.errorProfile.collect { _ -> updateState(_state.value) }
            }
        }
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
                if (!isTransferMode && System.currentTimeMillis() - lastReadTime >= READ_INACTIVITY_MS) {
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

    // new
    fun notifyPairingUserCancellation(unbondReason: Int) {
        isIntentionalDisconnect = true
        disconnectInternal()
        reconnectScheduler.cancelReconnect()
        reconnectScheduler.reset()
        cancelConnectionTimeout()
        connectJob?.cancel()
        val reason = if (unbondReason == 3) FailureReason.PairingCancelledByUser
        else FailureReason.PairingRejectedLocally
        updateState(ClassicState.FAILED(reason))
        logEvent(if (unbondReason == 3) "Pairing cancelled by user" else "Pairing rejected locally")
    }

    fun destroy() {
        isIntentionalDisconnect = true
        disconnectInternal()
        managerScope.cancel()
    }

    // ─── Internal Cleanup ──────────────────────────────────
    private fun disconnectInternal() {
        // 🛡️ FIX 1: Explicitly flag this as an intentional disconnect to block auto-retries
        isIntentionalDisconnect = true
        reconnectScheduler.cancelReconnect()

        batteryMonitor.stopMonitoring()
        cancelConnectionTimeout()
        inactivityTimeoutJob?.cancel(); inactivityTimeoutJob = null
        try { bluetoothSocket?.close() } catch (_: IOException) {}
        connectJob?.cancel()
        readJob?.cancel()
        try { inputStream?.close()    } catch (_: IOException) {}
        try { outputStream?.close()   } catch (_: IOException) {}

        bluetoothSocket = null
        connectionSecurity = ConnectionSecurity.UNKNOWN
        inputStream     = null
        outputStream    = null
        connectedDeviceAddress = null
        connectedDeviceName    = null

        writeQueue?.stop()
        writeQueue = null
        parser.onMessageParsed = null
        parser.reset()
    }

    fun forgetDevice(device: BluetoothDevice): Boolean {
        return try {

            if (
                connectedDeviceAddress == device.address
            ) {
                disconnect()
            }

            val method =
                device.javaClass.getMethod("removeBond")

            method.invoke(device)

            true

        } catch (_: SecurityException) {
            logEvent("SecurityException checking bond state before connect")
        } as Boolean
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
    fun getConnectionSecurity(): ConnectionSecurity {
        return connectionSecurity
    }
    fun isConnected() = _state.value == ClassicState.CONNECTED
    fun resetToIdle() {
        if (_state.value == ClassicState.DISCONNECTED || _state.value is ClassicState.FAILED) {
            updateState(ClassicState.IDLE)
        }
    }


}