package com.example.myapplication

import android.Manifest
import android.bluetooth.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import no.nordicsemi.android.support.v18.scanner.*
import kotlinx.coroutines.Job
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import android.net.Uri

import kotlinx.coroutines.delay

class MainActivity : AppCompatActivity() {

    // ─── UI State ─────────────────────────────────────────────────────────────
    private lateinit var ui: UiComponents
    private var isScanning = false
    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingRefresh = false
    private var delayedStatusRunnable: Runnable? = null
    
    private val bleDeviceList = mutableListOf<BleDeviceItem>()
    private val bleDeviceMap = mutableMapOf<String, BluetoothDevice>()
    private val classicDeviceList = mutableListOf<ClassicDeviceItem>()
    private val classicDeviceMap = mutableMapOf<String, BluetoothDevice>()
    private lateinit var classicUiController: ClassicUiController
    private var activeTab = ActiveTab.BLE
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

    private val classicScanReceiver = ClassicScanReceiver(
        classicDeviceList = classicDeviceList,
        classicDeviceMap = classicDeviceMap,
        permissionChecker = { checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED },
        onDeviceListChanged = { runOnUiThread { ui.classicAdapter.notifyDataSetChanged() } },
        onStatusUpdate = { msg -> runOnUiThread { ui.classicStatusText.text = msg } }
    )

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            bluetoothService = (binder as BluetoothService.LocalBinder).getService()
            isBound = true
            if (!isScanning) startBleScan()
            
            bluetoothService?.onStateChanged = { state, address ->
                runOnUiThread { bleUiController.updateStatusUi(state, address) }
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
                            val msg = when (val state = info.state) {
                                AudioProfileState.IDLE -> "Audio: Idle"
                                AudioProfileState.CONNECTING -> "🎧 Audio Connecting..."
                                AudioProfileState.CONNECTED -> "🎧 Audio Connected: ${info.deviceName}"
                                AudioProfileState.PLAYING -> "▶ Playing on ${info.deviceName}" + if (info.codecName.isNotEmpty()) " · ${info.codecName}" else ""
                                AudioProfileState.DISCONNECTED -> "🔇 Audio Disconnected"
                                is AudioProfileState.RECONNECTING -> "🔄 Audio Reconnecting (${state.attempt}/3)"
                                is AudioProfileState.FAILED -> "❌ Audio Failed: ${state.reason}"
                            }
                            bleUiController.showDataBottomSheet("[A2DP] $msg")
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
                if (activeTab == ActiveTab.BLE) {
                    pendingRefresh = true
                    stopBleScan()
                    bluetoothService?.disconnect()
                } else {
                    stopClassicScan()
                    this.startClassicScan()
                }
            },
            onStopScan = { if (activeTab == ActiveTab.BLE) stopBleScan() else stopClassicScan() },
            onDisconnect = {
                if (activeTab == ActiveTab.BLE) bluetoothService?.disconnect()
                else classicService?.connectionManager?.disconnect()
            },
            onTabBle = { activeTab = ActiveTab.BLE },
            onTabClassic = {
                activeTab = ActiveTab.CLASSIC
                stopBleScan()
                this.startClassicScan()
            },
            onFeatures = { classicUiController.showClassicFeaturesSheet() },
            connectBleCallback = { device -> connectToDevice(device) },
            connectClassicCallback = { device -> classicService?.connectionManager?.connect(device) }
        )

        checkPermissionsAndStartService()
        
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        registerReceiver(classicScanReceiver, filter)
        bleUiController = BleUiController(
            activity = this,
            statusText = ui.statusText,
            backgroundView = ui.backgroundView,
            onStartBleScan = { startBleScan() },
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
    }

    private fun checkPermissionsAndStartService() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            startBluetoothService()
            startClassicBluetoothService()
            startBleScan()
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

    private val requestPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.all { it }) {
            startBluetoothService()
            startClassicBluetoothService()
            startBleScan()
        } else {
            Toast.makeText(this, "Permissions denied. Cannot scan.", Toast.LENGTH_SHORT).show()
        }
    }
    // ─── Scanning ────────────────────────────────────────────────────────────
    private val scanner = BluetoothLeScannerCompat.getScanner()
    private val scanCallback = BleScanCallback(

        onDeviceFound = { item, device ->

            runOnUiThread {

                val index = bleDeviceList.indexOfFirst {
                    it.address == item.address
                }

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

                if (changed) {
                    ui.deviceAdapter.notifyDataSetChanged()
                }
            }
        },



        permissionChecker = {
            checkSelfPermission(
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        }
    )

    private fun startBleScan() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        if (!bluetoothManager.adapter.isEnabled) {
            Toast.makeText(this, "Enable Bluetooth first", Toast.LENGTH_SHORT).show()
            return
        }
        stopBleScan()
        bleDeviceList.clear()
        bleDeviceMap.clear()
        runOnUiThread { ui.deviceAdapter.notifyDataSetChanged() }
        
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner.startScan(null, settings, scanCallback)
            isScanning = true
            Toast.makeText(this, "Scanning BLE...", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {}
    }

    private fun stopBleScan() {
        try {
            scanner.stopScan(scanCallback)
            isScanning = false
        } catch (_: SecurityException) {}
    }

    private fun startClassicScan() {
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (!adapter.isEnabled) {
            Toast.makeText(this, "Enable Bluetooth first", Toast.LENGTH_SHORT).show()
            return
        }

        classicDeviceList.clear()
        classicDeviceMap.clear()
        ui.classicAdapter.notifyDataSetChanged()

        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                adapter.bondedDevices?.forEach { device ->
                    val type = device.type
                    if (type != BluetoothDevice.DEVICE_TYPE_CLASSIC && type != BluetoothDevice.DEVICE_TYPE_DUAL) return@forEach
                    classicDeviceMap[device.address] = device
                    classicDeviceList.add(ClassicDeviceItem(device.name ?: "Unknown", device.address, type))
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
        stopBleScan()
        ui.statusText.text = getString(R.string.button_has_been_clicked)
        ui.listView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        bluetoothService?.connect(device)
    }

    // ─── UI Helpers ──────────────────────────────────────────────────────────
    override fun onDestroy() {
        super.onDestroy()
        delayedStatusRunnable?.let { uiHandler.removeCallbacks(it) }
        try { unregisterReceiver(classicScanReceiver) } catch (_: Exception) {}
        classicCollectorJob?.cancel()
        stopBleScan()
        if (isBound) { unbindService(serviceConnection); isBound = false }
        if (isClassicBound) { unbindService(classicConnection); isClassicBound = false }
    }

    override fun onStart() {
        super.onStart()
        if (!isBound) startBluetoothService()
        if (!isClassicBound) startClassicBluetoothService()
    }
}
