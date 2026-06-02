package com.example.myapplication.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.myapplication.R
import com.example.myapplication.util.SystemTimeline
import com.example.myapplication.ble.BleScanManager
import com.example.myapplication.ble.BleState
import com.example.myapplication.ble.BluetoothService
import com.example.myapplication.classic.AudioProfileState
import com.example.myapplication.classic.ClassicBluetoothService
import com.example.myapplication.classic.ClassicConnectionManager
import com.example.myapplication.classic.ClassicMessage
import com.example.myapplication.classic.ClassicScanReceiver
import com.example.myapplication.classic.ClassicState
import com.example.myapplication.classic.FileTransferState
import com.example.myapplication.classic.TransferDirection
import com.example.myapplication.insights.DeviceInsightManager
import com.example.myapplication.models.ActiveTab
import com.example.myapplication.models.BleDeviceItem
import com.example.myapplication.models.ClassicDeviceItem
import android.view.View
import com.example.myapplication.models.FilterType
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    // ─── UI State ─────────────────────────────────────────────────────────────
    private lateinit var ui: UiComponents

    private val uiHandler = Handler(Looper.getMainLooper())
    private var pendingRefresh = false
    private var hasAudioEverConnected = false
    private var delayedStatusRunnable: Runnable? = null
    private var notifyScheduled = false
    private val notifyRunnable = Runnable {
        notifyScheduled = false
        ui.deviceAdapter.notifyDataSetChanged()
    }
    private val bleDeviceList = mutableListOf<BleDeviceItem>()
    private val bleDeviceMap = mutableMapOf<String, BluetoothDevice>()
    private val classicDeviceList = mutableListOf<ClassicDeviceItem>()
    private val classicDeviceMap = mutableMapOf<String, BluetoothDevice>()

    private lateinit var classicUiController: ClassicUiController
    private var activeTab = ActiveTab.BLE
    private var activeBleFilter     = FilterType.NONE
    private var activeClassicFilter = FilterType.NONE
    private var serviceStarted = false
    private var classicServiceStarted = false

    // ─── Bottom Sheet for Live Data ───────────────────────────────────────────
    private var bottomSheetDialog: BottomSheetDialog? = null
    private var bottomSheetList: LinearLayout? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        val svc = classicService ?: run {
            Toast.makeText(this, "Classic service not ready", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        if (svc.connectionManager.isConnected()) {
            svc.fileTransferManager.sendFile(uri)
        } else {
            Toast.makeText(this, "Not connected to a Classic device", Toast.LENGTH_SHORT).show()
        }
    }

    // ─── Service Binding ──────────────────────────────────────────────────────
    private var bluetoothService: BluetoothService? = null
    private lateinit var bleUiController: BleUiController
    private var isBound = false
    private var classicService: ClassicBluetoothService? = null
    private var isClassicBound = false
    private var classicCollectorJob: Job? = null
    private lateinit var bleScanManager: BleScanManager



    private val classicScanReceiver = ClassicScanReceiver(
        classicDeviceList = classicDeviceList,
        classicDeviceMap = classicDeviceMap,
        permissionChecker = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                TODO("VERSION.SDK_INT < S")
            }
        },
        onDeviceListChanged = { runOnUiThread { ui.classicAdapter.notifyDataSetChanged() } },
        onStatusUpdate = { msg -> runOnUiThread { ui.classicStatusText.text = msg } }
    )

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            bluetoothService = (binder as BluetoothService.LocalBinder).getService()
            isBound = true

            // Add a small delay to ensure the UI is fully stable before the first scan results arrive
            uiHandler.postDelayed({
                if (!bleScanManager.isScanning) bleScanManager.start()
            }, 200)

            bluetoothService?.onStateChanged = { state, address ->
                runOnUiThread {
                    bleUiController.updateStatusUi(state, address)
                    when (state) {
                        BleState.CONNECTING    -> SystemTimeline.log("🔄 BLE connecting to ${bluetoothService?.connectedDeviceName ?: address}")
                        BleState.READY         -> SystemTimeline.log("🟢 BLE connected: ${bluetoothService?.connectedDeviceName ?: address}")
                        BleState.DISCONNECTED  -> SystemTimeline.log("🔇 BLE disconnected")
                        BleState.FAILED        -> SystemTimeline.log("❌ BLE connection failed")
                        BleState.BONDING       -> SystemTimeline.log("🔐 BLE bonding...")
                        else -> {}
                    }
                }
            }

            bluetoothService?.onDataReceived = { data ->
                runOnUiThread { bleUiController.showDataBottomSheet(data) }
            }

            bluetoothService?.let { bleUiController.updateStatusUi(it.currentState, it.connectedDeviceAddress ?: "") }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            bluetoothService = null
            isBound = false
        }
    }

    private val classicConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val service = (binder as ClassicBluetoothService.LocalBinder).getService()
            classicService = service
            isClassicBound = true

            val manager = service.connectionManager
            classicCollectorJob = lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    launch {
                        manager.connectionInfo.collect { info ->
                            classicUiController.updateClassicStatusUi(info.state, info.address)
                            when (val s = info.state) {
                                ClassicState.CONNECTING   -> SystemTimeline.log("🔄 Classic connecting to ${info.deviceName.ifBlank { info.address }}")
                                ClassicState.CONNECTED    -> SystemTimeline.log("🟢 Classic connected: ${info.deviceName}")
                                ClassicState.DISCONNECTED -> SystemTimeline.log("🔇 Classic disconnected")
                                is ClassicState.RECONNECTING -> SystemTimeline.log("🔄 Classic reconnecting (${s.attempt}/${ClassicConnectionManager.RECONNECT_MAX_ATTEMPTS})")
                                is ClassicState.FAILED    -> SystemTimeline.log("❌ Classic failed: ${s.reason}")
                                else -> {}
                            }
                        }
                    }
                    launch {
                        manager.messages.collect { message ->
                            val display = when (message) {
                                is ClassicMessage.Text -> "[Classic] ${message.raw}  |  ${message.hex}"
                                is ClassicMessage.Binary -> "[Classic Binary] ${message.bytes.size} bytes"
                                is ClassicMessage.ParseError -> "[Parse Error] ${message.reason}"
                            }
                            bleUiController.showDataBottomSheet(display)
                        }
                    }
                    launch {
                        manager.events.collect { event -> bleUiController.showDataBottomSheet("[Log] $event") }
                    }
                    launch {
                        service.fileTransferManager.state.collect { state ->
                            val msg = when (state) {
                                is FileTransferState.Idle        -> null
                                is FileTransferState.Sending     -> "⬆ ${state.filename}: ${(state.progress * 100).toInt()}%"
                                is FileTransferState.Receiving   -> "⬇ ${state.filename}: ${(state.progress * 100).toInt()}%"
                                is FileTransferState.Done        -> if (state.direction == TransferDirection.SEND) "✅ Sent: ${state.filename}" else "✅ Saved: ${state.filename}"
                                is FileTransferState.Failed      -> "❌ ${state.reason}"
                                is FileTransferState.Cancelled -> "⚠ Transfer cancelled"
                            }
                            if (msg != null) bleUiController.showDataBottomSheet("[Transfer] $msg")
                            classicUiController.updateTransferUi(state)
                        }
                    }
                    launch {
                        service.audioProfileManager.connectionInfo.collect { info ->
                            if (
                                info.state == AudioProfileState.CONNECTED ||
                                info.state == AudioProfileState.PLAYING
                            ) {
                                hasAudioEverConnected = true
                            }
                            val msg = when (val state = info.state) {
                                AudioProfileState.IDLE ->
                                    if (hasAudioEverConnected)
                                        "Audio: Idle"
                                    else
                                        ""
                                AudioProfileState.CONNECTING -> "🎧 Audio Connecting..."
                                AudioProfileState.CONNECTED -> "🎧 Audio Connected: ${info.deviceName}"
                                AudioProfileState.PLAYING -> "▶ Playing on ${info.deviceName}" + if (info.codecName.isNotEmpty()) " · ${info.codecName}" else ""
                                AudioProfileState.DISCONNECTED -> "🔇 Audio Disconnected"
                                is AudioProfileState.RECONNECTING -> "🔄 Audio Reconnecting (${state.attempt}/3)"
                                is AudioProfileState.FAILED -> "❌ Audio Failed: ${state.reason}"
                            }
                            if (msg.isNotBlank()) {
                                bleUiController.showDataBottomSheet("[A2DP] $msg")
                            }
                        }
                    }
                }
            }

            classicUiController.updateClassicStatusUi(manager.state.value, manager.connectedDeviceAddress ?: "")
            if (activeTab == ActiveTab.CLASSIC) startClassicScan()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            classicCollectorJob?.cancel()
            classicCollectorJob = null
            classicService = null
            isClassicBound = false
        }
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ui = MainUiFactory.build(
            activity = this,
            bleDeviceList = bleDeviceList,
            bleDeviceMap = bleDeviceMap,
            classicDeviceList = classicDeviceList,
            classicDeviceMap = classicDeviceMap,
            onRefresh = {
                DeviceInsightManager.onAppEvent("UI: Refresh requested")
                if (activeTab == ActiveTab.BLE) {
                    SystemTimeline.log("🔄 BLE refresh triggered")
                    bleScanManager.stop()
                    bluetoothService?.disconnect()
                    uiHandler.postDelayed({
                        bleScanManager.lastScanStartTime = 0L          // bypass 10s cooldown for explicit refresh
                        bleDeviceList.clear()
                        bleDeviceMap.clear()
                        ui.deviceAdapter.notifyDataSetChanged()
                        bleScanManager.start()
                    }, 900)
                } else {
                    SystemTimeline.log("🔄 Classic refresh triggered")
                    stopClassicScan()
                    this.startClassicScan()
                }
            },
            onStopScan = {
                DeviceInsightManager.onAppEvent("UI: Stop scan requested")
                if (activeTab == ActiveTab.BLE) bleScanManager.stop() else stopClassicScan()
            },
            onDisconnect = {
                DeviceInsightManager.onAppEvent("UI: Disconnect requested")
                if (activeTab == ActiveTab.BLE) {
                    SystemTimeline.log("⏏ BLE disconnect requested")
                    bluetoothService?.disconnect()
                } else {
                    SystemTimeline.log("⏏ Classic disconnect requested")
                    classicService?.connectionManager?.disconnect()
                }
            },
            onTabBle = {
                DeviceInsightManager.onAppEvent("UI: Switched to BLE tab")
                activeTab = ActiveTab.BLE
                SystemTimeline.log("📑 Switched to BLE tab")
                stopClassicScan()
                if (!bleScanManager.isScanning) bleScanManager.start()
            },
            onTabClassic = {
                DeviceInsightManager.onAppEvent("UI: Switched to Classic tab")
                activeTab = ActiveTab.CLASSIC
                SystemTimeline.log("📑 Switched to Classic tab")
                bleScanManager.stop()
                this.startClassicScan()
            },
            onFeatures = {
                DeviceInsightManager.onAppEvent("UI: Launching Features Sheet")
                classicUiController.showClassicFeaturesSheet()
            },
            connectBleCallback = { device ->
                DeviceInsightManager.onAppEvent("UI: Connecting to BLE device ${device.address}")
                connectToDevice(device)
            },
            connectClassicCallback = { device ->
                DeviceInsightManager.onAppEvent("UI: Connecting to Classic device ${device.address}")
                classicService?.connectionManager?.connect(device)
            }
        )

        checkPermissionsAndStartService()

        // Force an adapter sync right after UI creation to ensure visibility
        uiHandler.post { ui.deviceAdapter.notifyDataSetChanged() }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        registerReceiver(classicScanReceiver, filter)
        bleScanManager = BleScanManager(
            context = this,
            permissionChecker = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                } else {
                    TODO("VERSION.SDK_INT < S")
                }
            },
            onDeviceFound = { item, device ->
                runOnUiThread {
                    synchronized(bleDeviceList) {
                        val index = bleDeviceList.indexOfFirst { it.address == item.address }
                        var changed = false
                        if (index == -1) {
                            bleDeviceList.add(item)
                            changed = true
                        } else {
                            val old = bleDeviceList[index]
                            if (old.rssi != item.rssi || old.name != item.name) {
                                bleDeviceList[index] = item
                                changed = true
                            }
                        }
                        bleDeviceMap[item.address] = device
                        if (changed && !notifyScheduled) {
                            notifyScheduled = true
                            uiHandler.postDelayed(notifyRunnable, 250)
                        }
                    }
                }
            },
            onClearDevices = {
                uiHandler.removeCallbacks(notifyRunnable)
                notifyScheduled = false
                synchronized(bleDeviceList) {
                    bleDeviceList.clear()
                    bleDeviceMap.clear()
                }
                runOnUiThread { ui.deviceAdapter.notifyDataSetChanged() }
            },
            onScanStopped = {
                uiHandler.removeCallbacks(notifyRunnable)
                notifyScheduled = false
            }
        )
        bleUiController = BleUiController(
            activity = this,
            statusText = ui.statusText,
            backgroundView = ui.backgroundView,
            onStartBleScan = { bleScanManager.start() },
            getConnectedDeviceName = { bluetoothService?.connectedDeviceName },
            getCurrentBleState = { bluetoothService?.currentState },
            isPendingRefresh = { pendingRefresh },
            clearPendingRefresh = { pendingRefresh = false },
            getActiveTab = { activeTab },
            onDismissDataSheet = {
                bottomSheetDialog?.dismiss()
                bottomSheetDialog = null
                bottomSheetList = null
            }
        )
        classicUiController = ClassicUiController(
            activity = this,
            classicStatusText = ui.classicStatusText,
            transferStatusText = ui.transferStatusText,
            backgroundView = ui.backgroundView,
            onSendFile = { filePickerLauncher.launch("*/*") },
            isClassicConnected = { classicService?.connectionManager?.isConnected() == true },
            getActiveTab = { activeTab },
            onDismissDataSheet = { bleUiController.dismissDataSheet() },
            getConnectedDeviceName = { classicService?.connectionManager?.connectedDeviceName }
        )
        // Override clear-filter buttons so they also reset FilterType
        ui.bleClearFilterBtn.setOnClickListener {
            activeBleFilter = FilterType.NONE
            ui.deviceAdapter.clearFilter()
            ui.deviceAdapter.applyFilterType(FilterType.NONE)
            it.visibility = View.GONE
        }
        ui.classicClearFilterBtn.setOnClickListener {
            activeClassicFilter = FilterType.NONE
            ui.classicAdapter.clearFilter()
            ui.classicAdapter.applyFilterType(FilterType.NONE)
            it.visibility = View.GONE
        }

// BLE filter button
        ui.bleFilterDBtn.setOnClickListener {
            DeviceFilterSheet(
                context = this,
                currentFilter = activeBleFilter,
                onFilterSelected = { type ->
                    activeBleFilter = type
                    when (type) {
                        FilterType.SAVED -> ui.deviceAdapter.applyFilterType(type, saved = getSavedBleDevices())
                        FilterType.NONE  -> ui.deviceAdapter.applyFilterType(type)
                        else             -> ui.deviceAdapter.applyFilterType(type, bonded = getBondedBleAddresses())
                    }
                    if (ui.deviceAdapter.count == 0 && type != FilterType.NONE)
                        Toast.makeText(this, "No Devices Found after Filtration.", Toast.LENGTH_SHORT).show()
                    ui.bleClearFilterBtn.visibility = if (type != FilterType.NONE) View.VISIBLE else View.GONE
                }
            ).show()
        }

// Classic filter button
        ui.classicFilterBtn.setOnClickListener {
            DeviceFilterSheet(
                context = this,
                currentFilter = activeClassicFilter,
                onFilterSelected = { type ->
                    activeClassicFilter = type
                    ui.classicAdapter.applyFilterType(type, bonded = getBondedClassicAddresses())
                    if (ui.classicAdapter.count == 0 && type != FilterType.NONE)
                        Toast.makeText(this, "No Devices Found after Filtration.", Toast.LENGTH_SHORT).show()
                    ui.classicClearFilterBtn.visibility = if (type != FilterType.NONE) View.VISIBLE else View.GONE
                }
            ).show()
        }
    }

    private fun checkPermissionsAndStartService() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                TODO("VERSION.SDK_INT < TIRAMISU")
            }
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            startBluetoothService()
            startClassicBluetoothService()

        } else {
            requestPermissionsLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startBluetoothService() {
        if (serviceStarted) return
        serviceStarted = true
        val intent = Intent(this, BluetoothService::class.java).apply { `package` = packageName }
        startForegroundService(intent)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    private fun startClassicBluetoothService() {
        if (classicServiceStarted) return
        classicServiceStarted = true
        val intent = Intent(this, ClassicBluetoothService::class.java).apply { `package` = packageName }
        startForegroundService(intent)
        bindService(intent, classicConnection, BIND_AUTO_CREATE)
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val bluetoothGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_SCAN] == true &&
                    permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        }

        if (bluetoothGranted) {
            startBluetoothService()
            startClassicBluetoothService()

        } else {
            Toast.makeText(this, "Bluetooth permissions required to scan.", Toast.LENGTH_SHORT).show()
        }
        // Notification permission denial is silently ignored — non-critical
    }

    private fun startClassicScan() {
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (!adapter.isEnabled) {
            Toast.makeText(this, "Enable Bluetooth first", Toast.LENGTH_SHORT).show()
            return
        }

        synchronized(classicDeviceList) {
            classicDeviceList.clear()
            classicDeviceMap.clear()
        }
        ui.classicAdapter.notifyDataSetChanged()

        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                adapter.bondedDevices?.forEach { device ->
                    val type = device.type
                    if (type != BluetoothDevice.DEVICE_TYPE_CLASSIC && type != BluetoothDevice.DEVICE_TYPE_DUAL) return@forEach
                    
                    synchronized(classicDeviceList) {
                        classicDeviceMap[device.address] = device
                        classicDeviceList.add(
                            ClassicDeviceItem(
                                device.name ?: "Unknown",
                                device.address,
                                type
                            )
                        )
                    }
                }
                ui.classicAdapter.notifyDataSetChanged()
            }
        } catch (_: SecurityException) {}

        lifecycleScope.launch {

            if (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
            ) {

                if (adapter.isDiscovering) {
                    adapter.cancelDiscovery()
                    delay(500)
                }

                try {
                    adapter.startDiscovery()
                    SystemTimeline.log("📡 Classic discovery started")
                    Toast.makeText(
                        this@MainActivity,
                        "Classic scan started",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (_: SecurityException) {}
            }
        }
    }

    private fun stopClassicScan() {
        try { (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter.cancelDiscovery() } catch (_: SecurityException) {}
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (!isBound) return
        bleScanManager.stop()
        val name = try { device.name ?: device.address } catch (_: SecurityException) { device.address }
        SystemTimeline.log("🔗 BLE connect attempt: $name")
        ui.statusText.text = getString(R.string.button_has_been_clicked)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ui.listView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
        bluetoothService?.connect(device)
    }

    // ─── UI Helpers ──────────────────────────────────────────────────────────
    private fun getSavedBleDevices(): List<BleDeviceItem> = try {
        val bt = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) emptyList()
        else bt.bondedDevices
            ?.filter { it.type == BluetoothDevice.DEVICE_TYPE_LE || it.type == BluetoothDevice.DEVICE_TYPE_DUAL }
            ?.onEach { bleDeviceMap[it.address] = it }
            ?.map { BleDeviceItem(try { it.name ?: "Unknown" } catch (_: SecurityException) { "Unknown" }, it.address, 0) }
            ?: emptyList()
    } catch (_: Exception) { emptyList() }

    private fun getBondedBleAddresses(): Set<String> = try {
        val bt = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) emptySet()
        else bt.bondedDevices
            ?.filter { it.type == BluetoothDevice.DEVICE_TYPE_LE || it.type == BluetoothDevice.DEVICE_TYPE_DUAL }
            ?.map { it.address }?.toSet() ?: emptySet()
    } catch (_: Exception) { emptySet() }

    private fun getBondedClassicAddresses(): Set<String> = try {
        val bt = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) emptySet()
        else bt.bondedDevices
            ?.filter { it.type == BluetoothDevice.DEVICE_TYPE_CLASSIC || it.type == BluetoothDevice.DEVICE_TYPE_DUAL }
            ?.map { it.address }?.toSet() ?: emptySet()
    } catch (_: Exception) { emptySet() }
    override fun onDestroy() {
        super.onDestroy()
        delayedStatusRunnable?.let { uiHandler.removeCallbacks(it) }
        try { unregisterReceiver(classicScanReceiver) } catch (_: Exception) {}
        classicCollectorJob?.cancel()
        bleScanManager.stop()
        if (isBound) { unbindService(serviceConnection); isBound = false }
        if (isClassicBound) { unbindService(classicConnection); isClassicBound = false }
    }
    override fun onResume() {
        super.onResume()
        bluetoothService?.let {
            if (it.currentState == BleState.DISCONNECTED || it.currentState == BleState.FAILED) {
                it.resetToIdle()
            }
        }
        classicService?.connectionManager?.resetToIdle()
    }
    override fun onStart() {
        super.onStart()
        if (!isBound) startBluetoothService()
        if (!isClassicBound) startClassicBluetoothService()
    }}