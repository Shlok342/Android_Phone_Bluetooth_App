package com.example.myapplication

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
import androidx.core.app.NotificationCompat
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
    fun disconnect() {
        isDisconnecting = true
        cancelTimeout()
        synchronized(gattQueue) { gattQueue.clear() }
        isGattBusy = false
        updateNotification("Disconnecting...")
        val gatt = bluetoothGatt
        try { gatt?.disconnect() } catch (_: SecurityException) {}
        Handler(Looper.getMainLooper()).postDelayed({
            try { gatt?.close() } catch (_: SecurityException) {}
            bluetoothGatt = null
            connectedDeviceName = null
            connectedDeviceAddress = null
            currentState = BleState.DISCONNECTED
            updateNotification("Disconnected")
            isDisconnecting = false
        }, 500)
    }
    // REPLACE disconnectInternal() with this:
    private fun disconnectInternal() {
        cancelTimeout()
        gattQueue.clear()
        isGattBusy = false
        val gatt = bluetoothGatt ?: return
        try {
            gatt.disconnect()
        } catch (_: SecurityException) {}
        // close() is intentionally deferred to onConnectionStateChange(STATE_DISCONNECTED)
        // If already disconnected, close() immediately to avoid leak
        Handler(Looper.getMainLooper()).postDelayed({
            try { gatt.close() } catch (_: SecurityException) {}
        }, 300)
        bluetoothGatt = null
    }
    private val binder = LocalBinder()
    override fun onBind(intent: Intent): IBinder = binder

    // ─── State ────────────────────────────────────────────────────────────────

    @Volatile private var isDisconnecting = false
    private var bluetoothGatt: BluetoothGatt? = null
    var currentState = BleState.IDLE
        private set(value) {
            field = value
            onStateChanged?.invoke(value, connectedDeviceAddress ?: "")
        }

    var connectedDeviceAddress: String? = null
    var connectedDeviceName: String? = null
    
    var onStateChanged: ((BleState, String) -> Unit)? = null


    var onDataReceived: ((String) -> Unit)? = null

    // ─── Timeout Management ───────────────────────────────────────────────────
    private val subscribedCharacteristics = mutableSetOf<String>()
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    private fun startTimeout(message: String, delay: Long = 15000L) {
        cancelTimeout()
        timeoutRunnable = Runnable {
            if (currentState != BleState.READY) {
                updateNotification("Connection Timeout: $message")
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
                        // Pairing dialog is now showing — keep state as BONDING, reset timeout
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
                            updateNotification("Bonding failed")
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
            if (isGattBusy) return   // another path already picked it up
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

    // ─── CCCD UUID ────────────────────────────────────────────────────────────
    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    
    // ─── GATT Callback ────────────────────────────────────────────────────────
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val address = gatt.device.address

            if (status != BluetoothGatt.GATT_SUCCESS) {
                updateNotification("Connection failed (status $status)")
                cleanUp()
                try { bluetoothGatt?.close() } catch (_: SecurityException) {}
                bluetoothGatt = null
                currentState = BleState.FAILED // Only set once, skip disconnect()
                return
            }

            when (newState) {

                BluetoothProfile.STATE_CONNECTED -> {

                    connectedDeviceName = try {
                        if (
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                            != PackageManager.PERMISSION_GRANTED
                        ) {
                            "Unknown Device"
                        } else {
                            gatt.device.name ?: "Unknown Device"
                        }
                    } catch (_: SecurityException) {
                        "Unknown Device"
                    }

                    connectedDeviceAddress = address

                    val bondState = try {
                        gatt.device.bondState
                    } catch (_: SecurityException) {
                        BluetoothDevice.BOND_BONDED
                    }

                    when (bondState) {
                        BluetoothDevice.BOND_NONE -> {
                            currentState = BleState.BONDING
                            updateNotification("Waiting for pairing...")
                            startTimeout("Bonding timed out", 30000L)
                            // Explicitly trigger the system pairing dialog
                            try {
                                val initiated = gatt.device.createBond()
                                if (!initiated) {
                                    // createBond() returns false if bonding is already in progress
                                    // or was already initiated by the device — that's fine, just wait
                                    updateNotification("Pairing in progress...")
                                }
                            } catch (_: SecurityException) {
                                currentState = BleState.FAILED
                                updateNotification("Permission error during bonding")
                            }
                        }

                        BluetoothDevice.BOND_BONDING -> {
                            // Pairing already in progress (e.g. initiated by the device)
                            currentState = BleState.BONDING
                            updateNotification("Bonding in progress...")
                            startTimeout("Bonding timed out", 3000)
                        }

                        else -> {
                            // Already bonded — proceed directly to service discovery
                            currentState = BleState.DISCOVERING_SERVICES
                            updateNotification("Connected. Discovering services...")
                            startTimeout("Service discovery timed out")

                            Handler(Looper.getMainLooper()).postDelayed({
                                try {
                                    gatt.discoverServices()
                                } catch (_: SecurityException) {
                                    currentState = BleState.FAILED
                                    updateNotification("Failed discovering services")
                                }
                            }, 500)
                        }
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (isDisconnecting) return
                    cancelTimeout()
                    currentState = BleState.DISCONNECTED

                    updateNotification("Disconnected")

                    try {
                        gatt.close()
                    } catch (_: SecurityException) {}

                    cleanUp()

                    bluetoothGatt = null

                    connectedDeviceName = null
                    connectedDeviceAddress = null
                }
            }


        }

        override fun onServicesDiscovered (gatt: BluetoothGatt, status: Int) {
            cancelTimeout()
            if (status != BluetoothGatt.GATT_SUCCESS) {
                currentState = BleState.FAILED
                updateNotification("Service discovery failed")
                return
            }

            updateNotification("Setting up characteristics...")

            setupCharacteristics(gatt)

            currentState = BleState.READY


            updateNotification("Ready: $connectedDeviceName")
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
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

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {

            val uuid =
                descriptor.characteristic.uuid.toString()

            if (status == BluetoothGatt.GATT_SUCCESS) {

                subscribedCharacteristics.add(uuid)

                onDataReceived?.invoke(
                    "[Subscribed] ${descriptor.characteristic.uuid}"
                )

            } else {

                onDataReceived?.invoke(
                    "[Subscribe Failed] ${descriptor.characteristic.uuid}"
                )
            }

            gattOperationComplete()
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            val uuid =
                characteristic.uuid.toString().lowercase()

// Heart Rate Measurement UUID
            if (uuid == "00002a37-0000-1000-8000-00805f9b34fb") {

                val parsed =
                    parseHeartRate(value)

                updateNotificationThrottled(parsed)

                onDataReceived?.invoke(
                    "[Notify] $parsed"
                )

                return
            }

            val hex =
                value.joinToString(" ") {
                    "%02X".format(it)
                }

            val text = try {

                String(value, Charsets.UTF_8)
                    .filter {
                        it.isLetterOrDigit() || it.isWhitespace()
                    }

            } catch (_: Exception) {

                "Binary"
            }

            updateNotificationThrottled(
                "📡 ${characteristic.uuid.toString().take(4)}: $text"
            )

            onDataReceived?.invoke(
                "[Notify] ${characteristic.uuid} → Hex: $hex | Text: $text"
            )
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")


            val value =
                characteristic.value ?: return

            val uuid =
                characteristic.uuid.toString().lowercase()

            if (uuid == "00002a37-0000-1000-8000-00805f9b34fb") {

                val parsed =
                    parseHeartRate(value)

                updateNotificationThrottled(parsed)

                onDataReceived?.invoke(
                    "[Notify] $parsed"
                )

                return
            }

            val hex =
                value.joinToString(" ") {
                    "%02X".format(it)
                }

            val text = try {

                String(value, Charsets.UTF_8)
                    .filter {
                        it.isLetterOrDigit() || it.isWhitespace()
                    }

            } catch (_: Exception) {

                "Binary"
            }

            updateNotificationThrottled(
                "📡 ${characteristic.uuid.toString().take(4)}: $text"
            )

            onDataReceived?.invoke(
                "[Notify] ${characteristic.uuid} → Hex: $hex | Text: $text"
            )
        }
    }

    private fun proceedAfterBonding(device: BluetoothDevice) {
        currentState = BleState.DISCOVERING_SERVICES
        updateNotification("Discovering services...")
        startTimeout("Service discovery timed out")

        Handler(Looper.getMainLooper()).postDelayed({

            try {

                val started = bluetoothGatt?.discoverServices() == true

                if (!started) {

                    currentState = BleState.FAILED
                    updateNotification("Service discovery failed to start")
                    disconnect()

                }

            } catch (_: SecurityException) {

                currentState = BleState.FAILED
                updateNotification("Permission error discovering services")
                disconnect()

            }

        }, 600) // Small delay helps stability
    }

    private fun setupCharacteristics(gatt: BluetoothGatt) {
        val services = gatt.services ?: return
        onDataReceived?.invoke(
            "[System] Found ${services.size} services"
        )
        for (service in services) {
            onDataReceived?.invoke(
                "[Service] ${identifyService(service.uuid.toString())}\n${service.uuid}"
            )
            for (characteristic in service.characteristics) {
                onDataReceived?.invoke(
                    "[Characteristic] ${
                        identifyCharacteristic(characteristic.uuid.toString())
                    }\n${characteristic.uuid}"
                )
                val props = characteristic.properties
                val uuid = characteristic.uuid.toString().lowercase()

                if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ||
                    props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                ) {
                    if (shouldAutoSubscribe(characteristic)) {
                        enqueue { enableNotifications(characteristic) }
                    }
                }

                if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                    if (shouldAutoRead(uuid)) {
                        enqueue {
                            try { gatt.readCharacteristic(characteristic) } catch (_: SecurityException) { gattOperationComplete() }
                        }
                    }
                }
            }
        }
    }

    private fun shouldAutoSubscribe(
        characteristic: BluetoothGattCharacteristic
    ): Boolean {

        val uuid = characteristic.uuid.toString().lowercase()

        val props = characteristic.properties

        // Must support notify or indicate
        val supportsNotify =
            props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0

        val supportsIndicate =
            props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0

        if (!supportsNotify && !supportsIndicate) {
            return false
        }

        // Skip known useless spam characteristics
        val blocked = listOf(
            "2902",
            "2901"
        )

        if (blocked.any { uuid.contains(it) }) {
            return false
        }

        // ALWAYS allow standard important BLE profiles
        val important = listOf(

            // Heart Rate
            "2a37",

            // Battery
            "2a19",

            // Cycling
            "2a5b",

            // Glucose
            "2a18",

            // Pulse Oximeter
            "2a5f",

            // Custom/common vendor
            "fff",
            "ffe",
            "ffb",
            "fe"
        )

        if (important.any { uuid.contains(it) }) {
            return true
        }

        // Allow UNKNOWN custom characteristics too
        // if they support notify/indicate

        return uuid.startsWith("0000").not()
    }

    private fun shouldAutoRead(uuid: String): Boolean {
        return uuid.contains("2a19") || uuid.contains("2a29") || 
               uuid.contains("2a24") || uuid.contains("2a26")
    }
    private fun identifyService(uuid: String): String {

        return when (uuid.lowercase()) {

            // Standard BLE Services
            "0000180f-0000-1000-8000-00805f9b34fb" ->
                "🔋 Battery Service"

            "0000180d-0000-1000-8000-00805f9b34fb" ->
                "❤️ Heart Rate Service"

            "0000180a-0000-1000-8000-00805f9b34fb" ->
                "📱 Device Information Service"

            "00001808-0000-1000-8000-00805f9b34fb" ->
                "🌡️ Glucose Service"

            "00001816-0000-1000-8000-00805f9b34fb" ->
                "🚴 Cycling Speed & Cadence"

            "0000181a-0000-1000-8000-00805f9b34fb" ->
                "🩺 Environmental Sensing"

            "00001810-0000-1000-8000-00805f9b34fb" ->
                "🩸 Blood Pressure"

            "00001812-0000-1000-8000-00805f9b34fb" ->
                "🧍 Human Interface Device"

            "00001814-0000-1000-8000-00805f9b34fb" ->
                "🏃 Running Speed & Cadence"

            else ->
                "❓ Unknown Service"
        }
    }
    private fun identifyCharacteristic(uuid: String): String {

        return when (uuid.lowercase()) {

            // Battery
            "00002a19-0000-1000-8000-00805f9b34fb" ->
                "🔋 Battery Level"

            // Heart Rate
            "00002a37-0000-1000-8000-00805f9b34fb" ->
                "❤️ Heart Rate Measurement"

            "00002a38-0000-1000-8000-00805f9b34fb" ->
                "❤️ Body Sensor Location"

            // Device Info
            "00002a29-0000-1000-8000-00805f9b34fb" ->
                "🏭 Manufacturer Name"

            "00002a24-0000-1000-8000-00805f9b34fb" ->
                "📦 Model Number"

            "00002a26-0000-1000-8000-00805f9b34fb" ->
                "🧠 Firmware Revision"

            // Glucose
            "00002a18-0000-1000-8000-00805f9b34fb" ->
                "🩸 Glucose Measurement"

            // Pulse Oximeter
            "00002a5f-0000-1000-8000-00805f9b34fb" ->
                "🫁 Pulse Oximeter"

            else ->
                "❓ Unknown Characteristic"
        }
    }
    private fun parseHeartRate(value: ByteArray): String {

        if (value.isEmpty()) {
            return "Invalid Heart Rate"
        }

        val flags = value[0].toInt()

        val is16Bit =
            flags and 0x01 != 0

        val bpm = if (is16Bit) {

            if (value.size >= 3) {
                ((value[2].toInt() and 0xFF) shl 8) or
                        (value[1].toInt() and 0xFF)
            } else {
                return "Invalid Heart Rate"
            }

        } else {

            if (value.size >= 2) {
                value[1].toInt() and 0xFF
            } else {
                return "Invalid Heart Rate"
            }
        }

        return "❤️ Heart Rate: $bpm BPM"
    }

    // ─── Enable Notifications ────────────────────────────────────────────────
    private fun enableNotifications(
        characteristic: BluetoothGattCharacteristic
    ) {

        val uuid = characteristic.uuid.toString()

        if (subscribedCharacteristics.contains(uuid)) {

            gattOperationComplete()

            return
        }

        val gatt = bluetoothGatt ?: run {

            gattOperationComplete()

            return
        }

        try {

            val notificationEnabled =
                gatt.setCharacteristicNotification(
                    characteristic,
                    true
                )

            if (!notificationEnabled) {

                gattOperationComplete()

                return
            }

            onDataReceived?.invoke(
                "[Trying Notify] ${characteristic.uuid}"
            )

            val descriptor =
                characteristic.getDescriptor(cccdUuid)

            if (descriptor == null) {

                gattOperationComplete()

                return
            }

            val value = when {

                characteristic.properties and
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ->

                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

                characteristic.properties and
                        BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 ->

                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE

                else -> {

                    gattOperationComplete()

                    return
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                gatt.writeDescriptor(
                    descriptor,
                    value
                )

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

    // ─── Public API ───────────────────────────────────────────────────────────
    fun connect(device: BluetoothDevice) {

        // Prevent duplicate connection attempts
        if (
            currentState == BleState.CONNECTING ||
            currentState == BleState.BONDING ||
            currentState == BleState.DISCOVERING_SERVICES
        ) {
            return
        }

        // Fully cleanup previous state
        disconnectInternal()

        cleanUp()

        connectedDeviceAddress = device.address

        connectedDeviceName = try {

            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                "Unknown Device"
            } else {
                device.name ?: "Unknown Device"
            }

        } catch (_: SecurityException) {
            "Unknown Device"
        }

        currentState = BleState.CONNECTING

        updateNotification(
            "Connecting to ${connectedDeviceName ?: device.address}..."
        )

        startTimeout(
            "Initial connection timed out"
        )

        try {
        
            bluetoothGatt = device.connectGatt(
                this,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )

        } catch (_: SecurityException) {

            currentState = BleState.FAILED

            updateNotification(
                "Bluetooth permission denied"
            )
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

    // ─── Notification Management ─────────────────────────────────────────────
    private val channelId = "ble_channel"
    private val notifId = 1
    private var lastNotifTime = 0L

    private fun updateNotificationThrottled(text: String) {
        val now = System.currentTimeMillis()
        if (now - lastNotifTime > 1500) {
            lastNotifTime = now
            updateNotification(text)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(notifId, buildNotification("Service Active"))
        
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bondReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(bondReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {

            unregisterReceiver(bondReceiver)

        } catch (_: IllegalArgumentException) {}
        cleanUp()
        try { bluetoothGatt?.close() } catch (_: SecurityException) {}
        bluetoothGatt = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(channelId, "Bluetooth Connection", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("BLE Status")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(notifId, buildNotification(text))
    }
}
