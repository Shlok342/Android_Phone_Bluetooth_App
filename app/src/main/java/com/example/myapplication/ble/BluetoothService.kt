package com.example.myapplication.ble

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.example.myapplication.ble.characteristics.BleCharacteristicWriter
import com.example.myapplication.ble.characteristics.BleEnvironment
import com.example.myapplication.classic.helpers.ConnectionSecurity
import com.example.myapplication.insights.DeviceInsightManager
import com.example.myapplication.models.BleConnectionInfo
import java.util.UUID
import com.example.myapplication.util.BluetoothPermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean


enum class BleState {
    IDLE,
    CONNECTING,
    BONDING,
    DISCOVERING_SERVICES,
    READY,
    DISCONNECTED,
    FAILED
}

class BluetoothService : Service() {

    // ─── Binder ───────────────────────────────────────────────────────────────
    inner class LocalBinder : Binder() {
        fun getService(): BluetoothService = this@BluetoothService
    }
    private val binder = LocalBinder()
    private val _batteryLevel =
        MutableStateFlow<Int?>(null)

    val batteryLevel =
        _batteryLevel.asStateFlow()
    private val batteryCharacteristicUuid =
        UUID.fromString(
            "00002a19-0000-1000-8000-00805f9b34fb"
        )
    override fun onBind(intent: Intent): IBinder = binder
    // ─── Coroutines ──────────────────────────────────────────────────────────
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _connectionInfo = MutableStateFlow(BleConnectionInfo(BleState.IDLE, ""))
    val connectionInfo: StateFlow<BleConnectionInfo> = _connectionInfo.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    // ─── State ────────────────────────────────────────────────────────────────
    private var bluetoothGatt: BluetoothGatt? = null
    @Volatile private var isDisconnecting = false
    private var lastConnectedDevice: BluetoothDevice? = null
    private var gatt133Attempts = 0
    private val MAX133RETRIES = 3
    var currentState = BleState.IDLE
        private set(value) {
            field = value
            _connectionInfo.value = BleConnectionInfo(value, connectedDeviceAddress ?: "")
        }

    var connectedDeviceAddress: String? = null
        set(value) {
            field = value
            _connectionInfo.value = BleConnectionInfo(currentState, value ?: "")
        }
    var connectedDeviceName: String? = null
    private var connectionSecurity =
        ConnectionSecurity.UNKNOWN

    fun getConnectionSecurity(): ConnectionSecurity {
        return connectionSecurity
    }







        private val bleEnvironment = object : BleEnvironment {
            override val bluetoothGatt get() = this@BluetoothService.bluetoothGatt
            override val currentState get() = this@BluetoothService.currentState

            // --- Provide the new properties ---
            override val subscribedCharacteristics get() = this@BluetoothService.subscribedCharacteristics
            override val batteryCharacteristicUuid get() = this@BluetoothService.batteryCharacteristicUuid
            // ----------------------------------
            override val cccdUuid get() = this@BluetoothService.cccdUuid
            override fun addSubscribedCharacteristic(uuid: String) {
                subscribedCharacteristics.add(uuid)
            }
            override fun updateBatteryLevel(percent: Int) {
                _batteryLevel.value = percent
            }
            override fun enqueue(action: () -> Unit) = this@BluetoothService.enqueue(action)
            override fun emitMessage(message: String) { _messages.tryEmit(message) }
            override fun gattOperationComplete() = this@BluetoothService.gattOperationComplete()
            @SuppressLint("MissingPermission")
            override fun requestBondIfNeeded(status: Int) {
                if (status != 5 && status != 15) return // not GATT_INSUFFICIENT_AUTHENTICATION/ENCRYPTION
                val device = bluetoothGatt?.device ?: return
                if (device.bondState != BluetoothDevice.BOND_NONE) return
                this@BluetoothService.currentState = BleState.BONDING
                bleNotificationManager.updateNotification("Pairing required for this device...")
                startTimeout("Bonding timed out", 30000L)
                try { device.createBond() } catch (_: SecurityException) {
                    this@BluetoothService.currentState = BleState.FAILED
                }
            }
        }

        // Instantiate your new setup class
        private val deviceSetup = BleCharacteristicWriter(bleEnvironment)

    private lateinit var bleNotificationManager: BleNotificationManager
    private val subscribedCharacteristics = mutableSetOf<String>()
    private val _events = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private fun logEvent(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            .format(Date())
        _events.tryEmit("[$time] $msg")
    }
    fun forgetDevice(device: BluetoothDevice): Boolean {
        return try {

            if (connectedDeviceAddress == device.address) {
                disconnect()
            }

            val method = device.javaClass.getMethod("removeBond")
            method.invoke(device)

            true

        } catch (e: Exception) {

            logEvent("Failed to forget device: ${e.message}")

            false
        }
    }
    // ─── Timeout Management ───────────────────────────────────────────────────
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    private fun startTimeout(message: String, delay: Long = 15000L) {
        cancelTimeout()
        timeoutRunnable = Runnable {
            if (currentState != BleState.READY) {
                bleNotificationManager.updateNotification("Connection Timeout: $message")
                currentState = BleState.FAILED
                connectionSecurity =
                    ConnectionSecurity.UNKNOWN
                disconnect()
            }
        }
        timeoutHandler.postDelayed(timeoutRunnable!!, delay)
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { timeoutHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    // ─── Bond Receiver ────────────────────────────────────────────────────────
    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                
                if (device?.address != connectedDeviceAddress) return

                val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                when (bondState) {
                    BluetoothDevice.BOND_BONDING -> {
                        if (currentState == BleState.BONDING) {
                            cancelTimeout()
                            startTimeout("Bonding timed out", 60000L)
                        }
                    }
                    BluetoothDevice.BOND_BONDED -> {
                        if (currentState == BleState.BONDING) {
                            cancelTimeout()
                            val bondedDevice = device ?: lastConnectedDevice ?: run {
                                currentState = BleState.FAILED
                                connectionSecurity =
                                    ConnectionSecurity.UNKNOWN
                                bleNotificationManager.updateNotification("Bond complete but device lost")
                                return
                            }
                            if (bluetoothGatt == null) {
                                // GATT dropped during bonding (normal Android behaviour) — reconnect
                                currentState = BleState.CONNECTING
                                bleNotificationManager.updateNotification("Reconnecting after bond...")
                                startTimeout("Post-bond reconnect timed out")
                                try {
                                    bluetoothGatt = bondedDevice.connectGatt(
                                        this@BluetoothService, false,
                                        gattCallback, BluetoothDevice.TRANSPORT_LE
                                    )
                                } catch (_: SecurityException) {
                                    currentState = BleState.FAILED
                                    connectionSecurity =
                                        ConnectionSecurity.UNKNOWN
                                    bleNotificationManager.updateNotification("Permission error after bond")
                                }
                            } else {
                                proceedAfterBonding(bondedDevice)
                            }
                        }
                    }
                    BluetoothDevice.BOND_NONE -> {
                        if (currentState == BleState.BONDING || currentState == BleState.CONNECTING) {
                            currentState = BleState.FAILED
                            connectionSecurity =
                                ConnectionSecurity.UNKNOWN
                            bleNotificationManager.updateNotification("Pairing failed. Verify PIN/passkey.")
                            disconnect()
                        }
                    }
                }
            }
        }
    }

    // ─── GATT Operation Queue ─────────────────────────────────────────────────
    private val gattQueue = ArrayDeque<() -> Unit>()

    private val isGattBusy = AtomicBoolean(false)

    private fun enqueue(operation: () -> Unit) {
        synchronized(gattQueue) { gattQueue.addLast(operation) }
        if (!isGattBusy.get()) processNextGattOperation()
    }

    private fun processNextGattOperation() {
        if (!isGattBusy.compareAndSet(false, true)) return
        val next = synchronized(gattQueue) {
            if (gattQueue.isEmpty()) { isGattBusy.set(false); return }
            gattQueue.removeFirst()
        }
        next.invoke()
    }

    private fun gattOperationComplete() {
        isGattBusy.set(false)
        processNextGattOperation()
    }

    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    // ADD near cccdUuid definition
    private fun refreshGattCache(gatt: BluetoothGatt): Boolean {
        return try {
            val method = gatt.javaClass.getMethod("refresh")
            method.invoke(gatt) as? Boolean ?: false
        } catch (_: Exception) {
            false
        }
    }
    // ─── GATT Callback ────────────────────────────────────────────────────────
    private val gattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val address = gatt.device.address
            DeviceInsightManager.onAppEvent("BLE GATT: Connection state changed. Status: $status, NewState: $newState")

            if (status != BluetoothGatt.GATT_SUCCESS) {
                val gattStatusLabel = when (status) {
                    133  -> "GATT_ERROR (transient transport/cache mismatch)"
                    5    -> "GATT_INSUFFICIENT_AUTHENTICATION"
                    8    -> "GATT_CONN_TIMEOUT"
                    19   -> "GATT_CONN_TERMINATE_PEER_USER (remote closed)"
                    22   -> "GATT_CONN_TERMINATE_LOCAL_HOST"
                    257  -> "GATT_CONN_FAIL_ESTABLISH"
                    else -> "GATT_STATUS_$status (undocumented)"
                }
                // AFTER
                DeviceInsightManager.onAppEvent(
                    "BLE GATT error on $address → status=$status | $gattStatusLabel | appState=$currentState"
                )
                if (status == 133) refreshGattCache(gatt)
                try { gatt.close() } catch (_: SecurityException) {}
                bluetoothGatt = null
                try { gatt.close() } catch (_: SecurityException) {}
                bluetoothGatt = null
                if (status == 133 && gatt133Attempts < MAX133RETRIES && !isDisconnecting) {
                    gatt133Attempts++
                    val delay = when (gatt133Attempts) { 1 -> 600L; 2 -> 1000L; else -> 1500L }
                    bleNotificationManager.updateNotification("GATT 133 – retry $gatt133Attempts/$MAX133RETRIES in ${delay}ms…")
                    DeviceInsightManager.onAppEvent(
                        "BLE 133 retry $gatt133Attempts/$MAX133RETRIES → reconnecting to $address in ${delay}ms"
                    )
                    currentState = BleState.CONNECTING
                    val device = lastConnectedDevice ?: run { currentState = BleState.FAILED; return }
                    connectionSecurity =
                        ConnectionSecurity.UNKNOWN
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!isDisconnecting && currentState == BleState.CONNECTING) {
                            try {
                                bluetoothGatt = device.connectGatt(this@BluetoothService, false,
                                    gattCallback, BluetoothDevice.TRANSPORT_LE)
                            } catch (_: SecurityException) { currentState = BleState.FAILED }

                        }
                    }, delay)
                    return
                }
                if (currentState == BleState.BONDING) return
                cleanUp()
                val failureMessage = when (status) {

                    5 ->
                        "Authentication failed. Verify PIN/passkey."

                    8 ->
                        "Connection timed out."

                    19 ->
                        "Device disconnected unexpectedly."

                    22 ->
                        "Connection closed."

                    133 ->
                        "Bluetooth connection error."

                    else ->
                        "Connection failed."
                }
                val retriesNote = if (status == 133) " | all $MAX133RETRIES retries exhausted" else ""
                DeviceInsightManager.onAppEvent(
                    "BLE terminal failure on $address → $failureMessage (status=$status$retriesNote)"
                )

                bleNotificationManager.updateNotification(
                    failureMessage
                )
                currentState = BleState.FAILED
                connectionSecurity =
                    ConnectionSecurity.UNKNOWN


                _connectionInfo.value = BleConnectionInfo(BleState.FAILED, address, failureMessage)
                return

            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    gatt133Attempts = 0
                    DeviceInsightManager.onDeviceConnected(gatt.device, "BLE", this@BluetoothService)
                    DeviceInsightManager.addDeviceEvent(address, "GATT Layer Connected")

                    connectedDeviceName = try {
                        if (!BluetoothPermissionUtils.hasBluetoothConnectPermission(this@BluetoothService)) "Unknown Device"
                        else gatt.device.name ?: "Unknown Device"
                    } catch (_: SecurityException) { "Unknown Device" }

                    connectedDeviceAddress = address

                    val bondState = try { gatt.device.bondState } catch (_: SecurityException) { BluetoothDevice.BOND_BONDED }

                    when (bondState) {

                        BluetoothDevice.BOND_BONDING -> {
                            currentState = BleState.BONDING
                            bleNotificationManager.updateNotification("Bonding in progress...")
                            startTimeout("Bonding timed out", 30000L)
                        }
                        else -> {
                            currentState = BleState.DISCOVERING_SERVICES
                            bleNotificationManager.updateNotification("Connected. Discovering services...")
                            startTimeout("Service discovery timed out")
                            Handler(Looper.getMainLooper()).postDelayed({
                                try { gatt.discoverServices() } catch (_: SecurityException) {
                                    currentState = BleState.FAILED
                                    connectionSecurity =
                                        ConnectionSecurity.UNKNOWN
                                    bleNotificationManager.updateNotification("Failed discovering services")
                                }
                            }, 500)
                        }
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    DeviceInsightManager.onDisconnected(address, "GATT Disconnected (Status $status)")

                    if (isDisconnecting) return
                    if (currentState == BleState.BONDING) {
                        try { gatt.close() } catch (_: SecurityException) {}
                        bluetoothGatt = null
                        return  // Don't clean up — let bondReceiver handle reconnect
                    }

                    cancelTimeout()
                    currentState = BleState.DISCONNECTED
                    bleNotificationManager.updateNotification("Disconnected")
                    try { gatt.close() } catch (_: SecurityException) {}
                    cleanUp()
                    bluetoothGatt = null
                    connectedDeviceName = null
                    connectedDeviceAddress = null
                }
            }
        }

        override fun onServicesDiscovered (gatt: BluetoothGatt, status: Int) {
            DeviceInsightManager.onAppEvent("BLE GATT: Services Discovered. Status: $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                DeviceInsightManager.onGattServicesDiscovered(gatt.device, gatt)
                DeviceInsightManager.addDeviceEvent(gatt.device.address, "Services Discovered: ${gatt.services.size} services")
            }
            cancelTimeout()
            if (status != BluetoothGatt.GATT_SUCCESS) {
                currentState = BleState.FAILED
                connectionSecurity =
                    ConnectionSecurity.UNKNOWN
                bleNotificationManager.updateNotification("Service discovery failed")
                return
            }

            bleNotificationManager.updateNotification("Setting up characteristics...")
            enqueue {
                try { gatt.requestMtu(512) } catch (_: SecurityException) { gattOperationComplete() }
            }
            deviceSetup.setupCharacteristics(gatt)
            currentState = BleState.READY
            connectionSecurity =
                ConnectionSecurity.SECURE
            bleNotificationManager.updateNotification("Ready: $connectedDeviceName")
            try { gatt.readRemoteRssi() } catch (_: SecurityException) {}
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            deviceSetup.onCharacteristicRead(gatt,characteristic,value,status)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            @Suppress("DEPRECATION")
            deviceSetup.onCharacteristicRead(gatt,characteristic, status)
        }
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                DeviceInsightManager.updateMtu(gatt.device, mtu)
                _messages.tryEmit("[MTU] Negotiated: $mtu bytes")
            }
            gattOperationComplete()
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                DeviceInsightManager.updateRssi(gatt.device.address, rssi)
                _messages.tryEmit("[RSSI] $rssi dBm")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            deviceSetup.onDescriptorWrite(gatt,descriptor,status)
        }


        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            deviceSetup.onCharacteristicWrite(gatt,characteristic,status)
        }
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            deviceSetup.onCharacteristicChanged(gatt,characteristic,value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            deviceSetup.onCharacteristicChanged(gatt,characteristic)
        }
    }

    private fun proceedAfterBonding(device: BluetoothDevice) {
        currentState = BleState.DISCOVERING_SERVICES
        bleNotificationManager.updateNotification("Discovering services...")
        startTimeout("Service discovery timed out", 30000L)

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val started = bluetoothGatt?.discoverServices() == true
                if (!started) {
                    currentState = BleState.FAILED
                    connectionSecurity =
                        ConnectionSecurity.UNKNOWN
                    bleNotificationManager.updateNotification("Service discovery failed to start")
                    disconnect()
                }
            } catch (_: SecurityException) {
                currentState = BleState.FAILED
                connectionSecurity =
                    ConnectionSecurity.UNKNOWN
                bleNotificationManager.updateNotification("Permission error discovering services")
                disconnect()
            }
        }, 600)
    }



    fun connect(device: BluetoothDevice) {
        DeviceInsightManager.onAppEvent("BLE: Initiating connection to ${device.address}")
        if (currentState == BleState.CONNECTING || currentState == BleState.BONDING || currentState == BleState.DISCOVERING_SERVICES) return

        disconnectInternal()
        cleanUp()

        connectedDeviceAddress = device.address
        connectedDeviceName = try {
            if (!BluetoothPermissionUtils.hasBluetoothConnectPermission(this)) "Unknown Device"
            else device.name ?: "Unknown Device"
        } catch (_: SecurityException) { "Unknown Device" }

        currentState = BleState.CONNECTING
        bleNotificationManager.updateNotification("Connecting to ${connectedDeviceName ?: device.address}...")
        startTimeout("Initial connection timed out")

        try {
            lastConnectedDevice = device
            gatt133Attempts = 0
            bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (_: SecurityException) {
            currentState = BleState.FAILED
            connectionSecurity =
                ConnectionSecurity.UNKNOWN
            bleNotificationManager.updateNotification("Bluetooth permission denied")
        }
    }

    fun disconnect() {
        isDisconnecting = true
        cancelTimeout()
        synchronized(gattQueue) { gattQueue.clear() }
        isGattBusy.set(false)
        bleNotificationManager.updateNotification("Disconnecting...")
        val gatt = bluetoothGatt
        try { gatt?.disconnect() } catch (_: SecurityException) {}
        Handler(Looper.getMainLooper()).postDelayed({
            try { gatt?.close() } catch (_: SecurityException) {}
            bluetoothGatt = null
            connectedDeviceName = null
            connectedDeviceAddress = null
            connectionSecurity =
                ConnectionSecurity.UNKNOWN
            currentState = BleState.DISCONNECTED
            bleNotificationManager.updateNotification("Disconnected")
            isDisconnecting = false
        }, 500)
    }

    private fun disconnectInternal() {
        cancelTimeout()
        synchronized(gattQueue) { gattQueue.clear() }
        isGattBusy.set(false)
        val gatt = bluetoothGatt ?: return
        try { gatt.disconnect() } catch (_: SecurityException) {}
        Handler(Looper.getMainLooper()).postDelayed({
            try { gatt.close() } catch (_: SecurityException) {}
        }, 300)
        bluetoothGatt = null
    }

    fun resetToIdle() {
        if (currentState == BleState.DISCONNECTED || currentState == BleState.FAILED) {
            currentState = BleState.IDLE
        }
    }

    private fun cleanUp() {
        _batteryLevel.value = null
        connectionSecurity =
            ConnectionSecurity.UNKNOWN
        synchronized(gattQueue) { gattQueue.clear() }
        isGattBusy.set(false)
        subscribedCharacteristics.clear()
        cancelTimeout()
        connectedDeviceName = null
        connectedDeviceAddress = null
        gatt133Attempts = 0
        lastConnectedDevice = null
    }


    override fun onCreate() {
        super.onCreate()
        bleNotificationManager = BleNotificationManager(this)
        startForeground(BleNotificationManager.NOTIFICATION_ID, bleNotificationManager.buildNotification("Service Active"))
        
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bondReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(bondReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(bondReceiver) } catch (_: IllegalArgumentException) {}
        cleanUp()
        try { bluetoothGatt?.close() } catch (_: SecurityException) {}
        bluetoothGatt = null
    }
}
