package com.example.myapplication.ble

import android.Manifest
import android.app.*
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.example.myapplication.insights.DeviceInsightManager
import java.util.UUID

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

    // ─── State ────────────────────────────────────────────────────────────────
    private var bluetoothGatt: BluetoothGatt? = null
    @Volatile private var isDisconnecting = false
    
    var currentState = BleState.IDLE
        private set(value) {
            field = value
            onStateChanged?.invoke(value, connectedDeviceAddress ?: "")
        }

    var connectedDeviceAddress: String? = null
    var connectedDeviceName: String? = null
    
    var onStateChanged: ((BleState, String) -> Unit)? = null
    var onDataReceived: ((String) -> Unit)? = null

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
                            startTimeout("Bonding timed out", 30000L)
                        }
                    }
                    BluetoothDevice.BOND_BONDED -> {
                        if (currentState == BleState.BONDING) {
                            cancelTimeout()
                            device?.let { proceedAfterBonding(it) }
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
    @Volatile private var isGattBusy = false

    private fun enqueue(operation: () -> Unit) {
        val shouldProcess = synchronized(gattQueue) {
            gattQueue.addLast(operation)
            !isGattBusy
        }
        if (shouldProcess) processNextGattOperation()
    }

    private fun processNextGattOperation() {
        val next = synchronized(gattQueue) {
            if (isGattBusy) return
            if (gattQueue.isEmpty()) return
            isGattBusy = true
            gattQueue.removeFirst()
        }
        next.invoke()
    }

    private fun gattOperationComplete() {
        synchronized(gattQueue) { isGattBusy = false }
        processNextGattOperation()
    }

    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    
    // ─── GATT Callback ────────────────────────────────────────────────────────
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val address = gatt.device.address
            DeviceInsightManager.onAppEvent("BLE GATT: Connection state changed. Status: $status, NewState: $newState")
            
            if (status != BluetoothGatt.GATT_SUCCESS) {
                bleNotificationManager.updateNotification("Connection failed (status $status)")
                cleanUp()
                try { bluetoothGatt?.close() } catch (_: SecurityException) {}
                bluetoothGatt = null
                currentState = BleState.FAILED
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    DeviceInsightManager.onDeviceConnected(gatt.device, "BLE", this@BluetoothService)
                    DeviceInsightManager.addDeviceEvent(address, "GATT Layer Connected")

                    connectedDeviceName = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                        ) "Unknown Device"
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
            setupCharacteristics(gatt)
            currentState = BleState.READY
            bleNotificationManager.updateNotification("Ready: $connectedDeviceName")
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                DeviceInsightManager.addDeviceEvent(gatt.device.address, "Read Characteristic: ${characteristic.uuid}")
                val hex = value.joinToString(" ") { "%02X".format(it) }
                val text = try { String(value, Charsets.UTF_8) } catch (_: Exception) { "Unreadable" }
                onDataReceived?.invoke("[Read] ${characteristic.uuid} → Hex: $hex | Text: $text")
            }
            gattOperationComplete()
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            val hex = value.joinToString(" ") { "%02X".format(it) }
            val text = try { String(value, Charsets.UTF_8) } catch (_: Exception) { "Unreadable" }
            onDataReceived?.invoke("[Read] ${characteristic.uuid} → Hex: $hex | Text: $text")
            gattOperationComplete()
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            val uuid = descriptor.characteristic.uuid.toString()
            if (status == BluetoothGatt.GATT_SUCCESS) {
                subscribedCharacteristics.add(uuid)
                onDataReceived?.invoke("[Subscribed] ${descriptor.characteristic.uuid}")
            } else {
                onDataReceived?.invoke("[Subscribe Failed] ${descriptor.characteristic.uuid}")
            }
            gattOperationComplete()
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            val uuid = characteristic.uuid.toString().lowercase()
            
            if (uuid == "00002a37-0000-1000-8000-00805f9b34fb") {
                val parsed = BleDataParser.parseHeartRate(value)
                updateNotificationThrottled(parsed)
                onDataReceived?.invoke("[Notify] $parsed")
                return
            }

            val hex = value.joinToString(" ") { "%02X".format(it) }
            val text = BleDataParser.parseText(value)
            updateNotificationThrottled("📡 ${characteristic.uuid.toString().take(4)}: $text")
            onDataReceived?.invoke("[Notify] ${characteristic.uuid} → Hex: $hex | Text: $text")
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            val uuid = characteristic.uuid.toString().lowercase()

            if (uuid == "00002a37-0000-1000-8000-00805f9b34fb") {
                val parsed = BleDataParser.parseHeartRate(value)
                updateNotificationThrottled(parsed)
                onDataReceived?.invoke("[Notify] $parsed")
                return
            }

            val hex = value.joinToString(" ") { "%02X".format(it) }
            val text = BleDataParser.parseText(value)
            updateNotificationThrottled("📡 ${characteristic.uuid.toString().take(4)}: $text")
            onDataReceived?.invoke("[Notify] ${characteristic.uuid} → Hex: $hex | Text: $text")
        }
    }

    private fun proceedAfterBonding(device: BluetoothDevice) {
        currentState = BleState.DISCOVERING_SERVICES
        bleNotificationManager.updateNotification("Discovering services...")
        startTimeout("Service discovery timed out")

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
        onDataReceived?.invoke("[System] Found ${services.size} services")
        for (service in services) {
            onDataReceived?.invoke("[Service] ${BleGattRegistry.identifyService(service.uuid.toString())}\n${service.uuid}")
            for (characteristic in service.characteristics) {
                onDataReceived?.invoke("[Characteristic] ${BleGattRegistry.identifyCharacteristic(characteristic.uuid.toString())}\n${characteristic.uuid}")
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

            onDataReceived?.invoke("[Trying Notify] ${characteristic.uuid}")
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) "Unknown Device"
            else device.name ?: "Unknown Device"
        } catch (_: SecurityException) { "Unknown Device" }

        currentState = BleState.CONNECTING
        bleNotificationManager.updateNotification("Connecting to ${connectedDeviceName ?: device.address}...")
        startTimeout("Initial connection timed out")

        try {
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
        isGattBusy = false
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

    private fun disconnectInternal() {
        cancelTimeout()
        synchronized(gattQueue) { gattQueue.clear() }
        isGattBusy = false
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
        isGattBusy = false
        subscribedCharacteristics.clear()
        cancelTimeout()
        connectedDeviceName = null
        connectedDeviceAddress = null
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
