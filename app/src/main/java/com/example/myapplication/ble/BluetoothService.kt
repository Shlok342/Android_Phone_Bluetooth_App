package com.example.myapplication.ble

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
import com.example.myapplication.insights.DeviceInsightManager
import com.example.myapplication.models.BleConnectionInfo
import java.util.UUID
import com.example.myapplication.util.BluetoothPermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private lateinit var bleNotificationManager: BleNotificationManager
    private val subscribedCharacteristics = mutableSetOf<String>()

    // ─── Timeout Management ───────────────────────────────────────────────────
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    private fun startTimeout(message: String, delay: Long = 15000L) {
        cancelTimeout()
        timeoutRunnable = Runnable {
            if (currentState != BleState.READY) {
                bleNotificationManager.updateNotification("Connection Timeout: $message")
                currentState = BleState.FAILED
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
                            bleNotificationManager.updateNotification("Bonding failed")
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
    
    // ─── GATT Callback ────────────────────────────────────────────────────────
    private val gattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val address = gatt.device.address
            DeviceInsightManager.onAppEvent("BLE GATT: Connection state changed. Status: $status, NewState: $newState")

            if (status != BluetoothGatt.GATT_SUCCESS) {
                try { gatt.close() } catch (_: SecurityException) {}
                bluetoothGatt = null
                if (status == 133 && gatt133Attempts < MAX133RETRIES && !isDisconnecting) {
                    gatt133Attempts++
                    val delay = when (gatt133Attempts) { 1 -> 600L; 2 -> 1000L; else -> 1500L }
                    bleNotificationManager.updateNotification("GATT error 133, retrying ($gatt133Attempts/$MAX133RETRIES)...")
                    currentState = BleState.CONNECTING
                    val device = lastConnectedDevice ?: run { currentState = BleState.FAILED; return }
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
                bleNotificationManager.updateNotification("Connection failed (status $status)")
                currentState = BleState.FAILED
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
                        BluetoothDevice.BOND_NONE -> {
                            currentState = BleState.BONDING
                            bleNotificationManager.updateNotification("Waiting for pairing...")
                            startTimeout("Bonding timed out", 30000L)
                            try { gatt.device.createBond() } catch (_: SecurityException) {
                                currentState = BleState.FAILED
                                bleNotificationManager.updateNotification("Permission error during bonding")
                            }
                        }
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
                bleNotificationManager.updateNotification("Service discovery failed")
                return
            }

            bleNotificationManager.updateNotification("Setting up characteristics...")
            enqueue {
                try { gatt.requestMtu(512) } catch (_: SecurityException) { gattOperationComplete() }
            }
            setupCharacteristics(gatt)
            currentState = BleState.READY
            bleNotificationManager.updateNotification("Ready: $connectedDeviceName")
            try { gatt.readRemoteRssi() } catch (_: SecurityException) {}
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                DeviceInsightManager.addDeviceEvent(gatt.device.address, "Read Characteristic: ${characteristic.uuid}")
                val hex = value.joinToString(" ") { "%02X".format(it) }
                val text = try { String(value, Charsets.UTF_8) } catch (_: Exception) { "Unreadable" }
                _messages.tryEmit("[Read] ${characteristic.uuid} → Hex: $hex | Text: $text")
            }
            gattOperationComplete()
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            val hex = value.joinToString(" ") { "%02X".format(it) }
            val text = try { String(value, Charsets.UTF_8) } catch (_: Exception) { "Unreadable" }
            _messages.tryEmit("[Read] ${characteristic.uuid} → Hex: $hex | Text: $text")
            gattOperationComplete()
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
            val uuid = descriptor.characteristic.uuid.toString()
            if (status == BluetoothGatt.GATT_SUCCESS) {
                subscribedCharacteristics.add(uuid)
                _messages.tryEmit("[Subscribed] ${descriptor.characteristic.uuid}")
            } else {
                _messages.tryEmit("[Subscribe Failed] ${descriptor.characteristic.uuid}")
            }
            gattOperationComplete()
        }


        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val uuid = characteristic.uuid.toString()
            if (status == BluetoothGatt.GATT_SUCCESS) {
                DeviceInsightManager.addDeviceEvent(gatt.device.address, "Write OK: $uuid")
                _messages.tryEmit("[Write OK] $uuid")
            } else {
                DeviceInsightManager.addDeviceEvent(gatt.device.address, "Write Failed: $uuid (status $status)")
                _messages.tryEmit("[Write Failed] $uuid (status $status)")
            }
            gattOperationComplete()
        }
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            val uuid = characteristic.uuid.toString().lowercase()
            
            if (uuid == "00002a37-0000-1000-8000-00805f9b34fb") {
                val parsed = BleDataParser.parseHeartRate(value)
                updateNotificationThrottled(parsed)
                _messages.tryEmit("[Notify] $parsed")
                return
            }

            val hex = value.joinToString(" ") { "%02X".format(it) }
            val text = BleDataParser.parseText(value)
            updateNotificationThrottled("📡 ${characteristic.uuid.toString().take(4)}: $text")
            _messages.tryEmit("[Notify] ${characteristic.uuid} → Hex: $hex | Text: $text")
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            val uuid = characteristic.uuid.toString().lowercase()

            if (uuid == "00002a37-0000-1000-8000-00805f9b34fb") {
                val parsed = BleDataParser.parseHeartRate(value)
                updateNotificationThrottled(parsed)
                _messages.tryEmit("[Notify] $parsed")
                return
            }

            val hex = value.joinToString(" ") { "%02X".format(it) }
            val text = BleDataParser.parseText(value)
            updateNotificationThrottled("📡 ${characteristic.uuid.toString().take(4)}: $text")
            _messages.tryEmit("[Notify] ${characteristic.uuid} → Hex: $hex | Text: $text")
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
                    bleNotificationManager.updateNotification("Service discovery failed to start")
                    disconnect()
                }
            } catch (_: SecurityException) {
                currentState = BleState.FAILED
                bleNotificationManager.updateNotification("Permission error discovering services")
                disconnect()
            }
        }, 600)
    }

    private fun setupCharacteristics(gatt: BluetoothGatt) {
        val services = gatt.services ?: return
        _messages.tryEmit("[System] Found ${services.size} services")
        for (service in services) {
            _messages.tryEmit("[Service] ${BleGattRegistry.identifyService(service.uuid.toString())}\n${service.uuid}")
            for (characteristic in service.characteristics) {
                _messages.tryEmit("[Characteristic] ${BleGattRegistry.identifyCharacteristic(characteristic.uuid.toString())}\n${characteristic.uuid}")
                val props = characteristic.properties
                val uuid = characteristic.uuid.toString().lowercase()

                if (BlePeripheralPolicy.shouldAutoSubscribe(characteristic)) {
                    enqueue { enableNotifications(characteristic) }
                }

                if (BlePeripheralPolicy.shouldAutoRead(uuid)) {
                    enqueue {
                        try { gatt.readCharacteristic(characteristic) } catch (_: SecurityException) { gattOperationComplete() }
                    }
                }
            }
        }
    }

    private fun enableNotifications(characteristic: BluetoothGattCharacteristic) {
        val uuid = characteristic.uuid.toString()
        if (subscribedCharacteristics.contains(uuid)) {
            gattOperationComplete()
            return
        }

        val gatt = bluetoothGatt ?: run { gattOperationComplete(); return }

        try {
            val notificationEnabled = gatt.setCharacteristicNotification(characteristic, true)
            if (!notificationEnabled) {
                gattOperationComplete()
                return
            }

            _messages.tryEmit("[Trying Notify] ${characteristic.uuid}")
            val descriptor = characteristic.getDescriptor(cccdUuid)
            if (descriptor == null) {
                gattOperationComplete()
                return
            }

            val value = when {
                characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                else -> { gattOperationComplete(); return }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, value)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = value
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        } catch (_: SecurityException) {
            gattOperationComplete()
        }
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
            currentState = BleState.DISCONNECTED
            bleNotificationManager.updateNotification("Disconnected")
            isDisconnecting = false
        }, 500)
    }
    /**
     * Queues a write to [uuid]. Auto-selects WRITE vs WRITE_NO_RESPONSE from
     * the characteristic's declared properties. Returns false if the
     * characteristic is not found or the connection isn't READY.
     */
    fun writeCharacteristic(uuid: String, value: ByteArray): Boolean {
        val gatt = bluetoothGatt ?: return false
        if (currentState != BleState.READY) return false

        val characteristic = gatt.services
            ?.flatMap { it.characteristics }
            ?.firstOrNull { it.uuid.toString().equals(uuid, ignoreCase = true) }
            ?: return false

        val writeType = when {
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ->
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0 ->
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else -> return false
        }
        val noAck = writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

        enqueue {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(characteristic, value, writeType)
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = value
                    @Suppress("DEPRECATION")
                    characteristic.writeType = writeType
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(characteristic)
                }
                // WRITE_NO_RESPONSE never fires onCharacteristicWrite, so drain the queue now
                if (noAck) {
                    _messages.tryEmit("[Write] ${characteristic.uuid} (no-ack)")
                    gattOperationComplete()
                }
            } catch (_: SecurityException) {
                gattOperationComplete()
            }
        }
        return true
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
        synchronized(gattQueue) { gattQueue.clear() }
        isGattBusy.set(false)
        subscribedCharacteristics.clear()
        cancelTimeout()
        connectedDeviceName = null
        connectedDeviceAddress = null
        gatt133Attempts = 0
        lastConnectedDevice = null
    }

    private var lastNotifTime = 0L
    private fun updateNotificationThrottled(text: String) {
        val now = System.currentTimeMillis()
        if (now - lastNotifTime > 1500) {
            lastNotifTime = now
            bleNotificationManager.updateNotification(text)
        }
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
