package com.example.myapplication.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
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
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.classic.ClassicAudioProfileManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.myapplication.ble.BleState
import com.example.myapplication.ble.BluetoothService
import com.example.myapplication.ble.scanners.BleScanManager
import com.example.myapplication.classic.ClassicBluetoothService
import com.example.myapplication.classic.ClassicScanReceiver
import com.example.myapplication.classic.helpers.ConnectionSecurity
import com.example.myapplication.insights.DeviceInsightManager
import com.example.myapplication.insights.FunctionsGet
import com.example.myapplication.main_activity_helpers.BluetoothBlockerOverlay
import com.example.myapplication.main_activity_helpers.BluetoothManagerWrapper
import com.example.myapplication.models.ActiveTab
import com.example.myapplication.models.BleDeviceItem
import com.example.myapplication.models.ClassicDeviceItem
import com.example.myapplication.models.FilterType
import com.example.myapplication.main_activity_helpers.BluetoothPermissionHandler
import com.example.myapplication.main_activity_helpers.Hybridization
import com.example.myapplication.ui.controllers.BleUiController
import com.example.myapplication.ui.controllers.ClassicUiController
import com.example.myapplication.ui.sheets.DeviceFilterSheet
import com.example.myapplication.util.GlobalUiStateManager
import com.example.myapplication.util.SystemTimeline
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.weishu.reflection.Reflection
import com.example.myapplication.main_activity_helpers.hasConnectPermission
import com.example.myapplication.main_activity_helpers.hasScanPermission
import com.example.myapplication.main_activity_helpers.Hybridization_On_Connected
import androidx.activity.viewModels



class MainActivity : AppCompatActivity() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        Reflection.unseal(base) // Bypasses hidden API blocks for the runtime session
    }
    private lateinit var btManager: BluetoothManagerWrapper
    private lateinit var getFunction: FunctionsGet
    private val permissionHandler = BluetoothPermissionHandler(
        activity = this,
        onPermissionsGranted = {
            // Runs only if permissions are valid AND Bluetooth hardware is ON
            startBluetoothService()
            startClassicBluetoothService()
        },
        onShowBlocker = {
            // Runs if permissions are valid BUT Bluetooth hardware is OFF
            showBluetoothBlocker()
        }
    )
    // ─── UI State ─────────────────────────────────────────────────────────────
    private lateinit var ui: UiComponents
    private var btBlocker: BluetoothBlockerOverlay? = null
    private val uiHandler = Handler(Looper.getMainLooper())

    private var pendingRefresh = false

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

    private var bleCollectorJob: Job? = null

    private var bleBatteryLevel: Int? = null
    // ─── Bottom Sheet for Live Data ───────────────────────────────────────────
    private var bottomSheetDialog: BottomSheetDialog? = null
    private var bottomSheetList: LinearLayout? = null
    private val requestBluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // User tapped "Allow"
            Toast.makeText(this, "Bluetooth is now ON", Toast.LENGTH_SHORT).show()
        } else {
            // User tapped "Deny" or closed the dialog
            Toast.makeText(this, "Bluetooth activation was declined", Toast.LENGTH_SHORT).show()
        }
    }
    private fun showBluetoothBlocker() {
        // If already created, make it visible and prompt user
        if (btBlocker != null) {
            btBlocker?.visibility = View.VISIBLE
            requestBluetoothEnable()
            return
        }

        // Instantiating the custom view using the single-argument constructor
        btBlocker = BluetoothBlockerOverlay(this).apply {
            // Set the callback code to execute when the button is clicked
            onEnableBtClick = {
                requestBluetoothEnable()
            }
        }

        // Add the view to your root layout hierarchy
        ui.rootFrame.addView(btBlocker)

        // Show system dialog immediately on first show
        requestBluetoothEnable()
    }
    private fun promptToTurnOnBluetooth() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Device doesn't support Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        // Trigger the system dialog only if Bluetooth is currently OFF
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestBluetoothEnableLauncher.launch(enableBtIntent)
        } else {
            Toast.makeText(this, "Bluetooth is already ON", Toast.LENGTH_SHORT).show()
        }
    }
    private fun checkBluetoothOnEntry() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            // If they just opened the app (or came back to it) and BT is off -> Show blocker
            btBlocker?.visibility = View.VISIBLE
        } else {
            hideBlockerAndStartServices()
        }
    }

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
    private val hybridization: Hybridization by viewModels()
    private val hybridizationForConnect : Hybridization_On_Connected by viewModels ()
    private var bluetoothService: BluetoothService? = null
    private lateinit var bleUiController: BleUiController
    private var isBound = false
    private var classicService: ClassicBluetoothService? =null
    private var classicAudioProfileManager: ClassicAudioProfileManager? = null
    private var isClassicBound = false
    private var classicCollectorJob: Job? = null





    private var serviceStarted = false
    private var classicServiceStarted = false

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* STATE_ON receiver handles re-entry; denial leaves blocker visible */ }

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return

            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)

            // While the app is actively running, we ONLY care if they turn Bluetooth ON.
            // We intentionally ignore STATE_OFF here so the blocker never interrupts mid-use.
            if (state == BluetoothAdapter.STATE_ON) {
                hideBlockerAndStartServices()
            }
        }
    }
    private fun hideBlockerAndStartServices() {
        btBlocker?.visibility = View.GONE
        if (!serviceStarted) startBluetoothService()
        if (!classicServiceStarted) startClassicBluetoothService()
        uiHandler.postDelayed({
            if (!bleScanManager.isScanning) bleScanManager.start()
        }, 1200)
    }
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
        // new
        onStatusUpdate = { msg ->
            runOnUiThread {
                ui.classicStatusText.text = msg
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        },
        onPairingCancelled = { reason ->
            classicService?.connectionManager?.notifyPairingUserCancellation(reason)
        }
    )


    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val service = (binder as BluetoothService.LocalBinder).getService()
            bluetoothService = service
            isBound = true

            // Add a small delay to ensure the UI is fully stable before the first scan results arrive
            uiHandler.postDelayed({
                if (!bleScanManager.isScanning) bleScanManager.start()
            }, 200)

            bleCollectorJob = lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    launch {
                        service.connectionInfo.collect { info ->
                            // Pass info.failureMessage as the fifth argument to updateStatusUi
                            bleUiController.updateStatusUi(
                                info.state,
                                info.address,
                                bluetoothService?.getConnectionSecurity() ?: ConnectionSecurity.UNKNOWN,
                                bleBatteryLevel,
                                info.failureMessage
                            )
                            when (info.state) {
                                BleState.CONNECTING    -> SystemTimeline.log("🔄 BLE connecting to ${service.connectedDeviceName ?: info.address}")
                                BleState.READY         -> SystemTimeline.log("🟢 BLE connected: ${service.connectedDeviceName ?: info.address}")
                                BleState.DISCONNECTED  -> SystemTimeline.log("🔇 BLE disconnected")
                                // Logs the specific failure message to the timeline if available
                                BleState.FAILED        -> SystemTimeline.log("❌ BLE connection failed: ${info.failureMessage ?: "Unknown error"}")
                                BleState.BONDING       -> SystemTimeline.log("🔐 BLE bonding...")
                                else -> {}
                            }
                        }
                    }
                    launch {
                        service.messages.collect { data ->
                            bleUiController.showDataBottomSheet(data)
                        }
                    }
                }
            }

        }

        override fun onServiceDisconnected(name: ComponentName) {
            bleCollectorJob?.cancel()
            bleCollectorJob = null
            bluetoothService = null
            isBound = false
        }
    }

    private val classicConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val service = (binder as ClassicBluetoothService.LocalBinder).getService()

            classicService = service
            isClassicBound = true

            // 1. Hand off the service reference to your ViewModel to kick off all background data collections
            hybridizationForConnect.onServiceConnected(service)
            hybridizationForConnect.observeBleBattery(bluetoothService)

            // 2. DRIVE THE UI REACTIVELY: Collect the public flows/channels exposed by your ViewModel
            classicCollectorJob = lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {

                    // Job A: Listen for persistent UI state updates (State, Address, Security, Battery)
                    launch {
                        hybridizationForConnect.classicUiState.collect { uiState ->
                            uiState?.let {
                                classicUiController.updateClassicStatusUi(
                                    it.state,
                                    it.address,
                                    it.security
                                )
                                // Pro-tip: You can now also access it.batteryLevel and it.batteryError here if your UI needs them!
                            }
                        }
                    }

                    // Job B: Listen for live transient events (Text, File Transfers, A2DP Logs) for the Bottom Sheet
                    launch {
                        hybridizationForConnect.bottomSheetMessages.collect { message ->
                            // Route this text stream directly into your UI bottom sheet controller
                            // e.g., bleUiController.showDataBottomSheet(message)
                        }
                    }
                }
            }

            // 3. Keep immediate fallback logic for tab navigation
            if (activeTab == ActiveTab.CLASSIC) {
                triggerClassicScan()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            // Safe teardown: stop collecting background jobs when service drops out
            classicCollectorJob?.cancel()
            classicCollectorJob = null
            classicService = null
            isClassicBound = false
        }
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────
    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        btManager = BluetoothManagerWrapper(
            context = this,
            scope = lifecycleScope,
            checkConnectPermission = { hasConnectPermission() }, // Calls your existing Activity method
            checkScanPermission = { hasScanPermission() }       // Calls your existing Activity method
        )
        getFunction = FunctionsGet(btManager)
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
                    getFunction.stopClassicScan()
                    this.triggerClassicScan()
                }
            },
            onStopScan = {
                DeviceInsightManager.onAppEvent("UI: Stop scan requested")
                if (activeTab == ActiveTab.BLE) bleScanManager.stop() else getFunction.stopClassicScan()
            },
            onDisconnect = {
                when (activeTab) {
                    ActiveTab.CLASSIC -> {
                        // ✅ ONLY disconnect Classic when the user is on the Classic tab
                        DeviceInsightManager.onAppEvent("UI: Classic Only Disconnect requested")
                        SystemTimeline.log("⏏ Initiating Classic service disconnect")

                        // Block auto-reconnect loops for Classic audio
                        classicAudioProfileManager?.setIntentionalDisconnect(true)

                        // Safely tear down Classic, leaving BLE completely untouched
                        classicService?.fullDisconnect()
                    }
                    ActiveTab.BLE -> {
                        // ✅ ONLY disconnect BLE when the user is on the BLE tab
                        DeviceInsightManager.onAppEvent("UI: BLE Only Disconnect requested")
                        SystemTimeline.log("⏏ Initiating BLE service disconnect")

                        // Safely tear down BLE, leaving Classic completely untouched
                        bluetoothService?.disconnect()
                    }
                }},
            onTabBle = {
                DeviceInsightManager.onAppEvent("UI: Switched to BLE tab")
                activeTab = ActiveTab.BLE
                SystemTimeline.log("📑 Switched to BLE tab")
                if (!bleScanManager.isScanning) bleScanManager.start()
            },
            onTabClassic = {
                DeviceInsightManager.onAppEvent("UI: Switched to Classic tab")
                activeTab = ActiveTab.CLASSIC
                SystemTimeline.log("📑 Switched to Classic tab")
                this.triggerClassicScan()
            },
            onFeatures = {
                DeviceInsightManager.onAppEvent("UI: Launching Features Sheet from $activeTab tab")
                classicUiController.showFeaturesSheet()
            },
            connectBleCallback = { device ->
                DeviceInsightManager.onAppEvent("UI: Connecting to BLE device ${device.address}")
                connectToDevice(device)
            },
            onForgetBleDevice = onForgetBleDevice@{ device ->

                if (device.bondState != BluetoothDevice.BOND_BONDED) {
                    Toast.makeText(
                        this,
                        "Device not bonded or does not require pairing",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@onForgetBleDevice
                }

                val success =
                    bluetoothService?.forgetDevice(device)
                        ?: false

                if (success) {
                    runOnUiThread {

                        synchronized(bleDeviceList) {
                            bleDeviceList.removeAll {
                                it.address == device.address
                            }

                            bleDeviceMap.remove(device.address)
                        }

                        ui.deviceAdapter.notifyDataSetChanged()
                    }
                } else {
                    Toast.makeText(
                        this,
                        "Failed to forget device",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            connectClassicCallback =  { device ->
                DeviceInsightManager.onAppEvent("UI: Connecting to Classic device ${device.address}")
                classicService?.connectionManager?.connect(device)
            },
            onForgetClassicDevice = { device ->
                val success = classicService?.connectionManager?.forgetDevice(device) ?: false
                if (success) {
                    runOnUiThread {
                        synchronized(classicDeviceList) {
                            classicDeviceList.removeAll { it.address == device.address }
                            classicDeviceMap.remove(device.address)
                        }
                        ui.classicAdapter.notifyDataSetChanged()
                    }
                } else {
                    Toast.makeText(this, "Failed to forget device", Toast.LENGTH_SHORT).show()
                }
            }
        )


        permissionHandler.checkPermissionsAndStartService()

        // Force an adapter sync right after UI creation to ensure visibility
        uiHandler.post { ui.deviceAdapter.notifyDataSetChanged() }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        registerReceiver(classicScanReceiver, filter)
        registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        checkBluetoothOnEntry()
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

        val globalUiStateManager = GlobalUiStateManager(ui.backgroundView)

        bleUiController = BleUiController(
            activity = this,
            statusText = ui.statusText,
            globalUiStateManager = globalUiStateManager,
            onStartBleScan = { bleScanManager.start() },
            getConnectedDeviceName = { bluetoothService?.connectedDeviceName },

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
            globalUiStateManager = globalUiStateManager,
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
                        FilterType.SAVED -> ui.deviceAdapter.applyFilterType(
                            type,
                            saved = getFunction.getSavedBleDevices()
                        )

                        FilterType.NONE -> ui.deviceAdapter.applyFilterType(type)
                        else -> ui.deviceAdapter.applyFilterType(
                            type,
                            bonded = getFunction.getBondedBleAddresses()
                        )
                    }
                    if (ui.deviceAdapter.count == 0 && type != FilterType.NONE)
                        Toast.makeText(
                            this,
                            "No Devices Found after Filtration.",
                            Toast.LENGTH_SHORT
                        ).show()
                    ui.bleClearFilterBtn.visibility =
                        if (type != FilterType.NONE) View.VISIBLE else View.GONE
                }
            ).show()
        }
        ui.bleBluetoothBtn.setOnClickListener {
            promptToTurnOnBluetooth()
        }
// Classic filter button
        ui.classicFilterBtn.setOnClickListener {
            DeviceFilterSheet(
                context = this,
                currentFilter = activeClassicFilter,
                onFilterSelected = { type ->
                    activeClassicFilter = type
                    ui.classicAdapter.applyFilterType(type, bonded = getFunction.getBondedClassicAddresses())
                    if (ui.classicAdapter.count == 0 && type != FilterType.NONE)
                        Toast.makeText(
                            this,
                            "No Devices Found after Filtration.",
                            Toast.LENGTH_SHORT
                        ).show()
                    ui.classicClearFilterBtn.visibility =
                        if (type != FilterType.NONE) View.VISIBLE else View.GONE
                }
            ).show()
        }
        ui.classicBluetoothBtn.setOnClickListener {
            promptToTurnOnBluetooth()
        }
    }
    // FROM: startClassicScan() (UI Side elements)
    @SuppressLint("MissingPermission")
    private fun triggerClassicScan() {
        btManager.startClassicScan(
            onPreScan = {
                synchronized(classicDeviceList) {
                    classicDeviceList.clear()
                    classicDeviceMap.clear()
                }
                ui.classicAdapter.notifyDataSetChanged()
            },
            onDeviceFound = { device ->
                synchronized(classicDeviceList) {
                    classicDeviceMap[device.address] = device
                    classicDeviceList.add(
                        ClassicDeviceItem(device.name ?: "Unknown", device.address, device.type)
                    )
                }
                ui.classicAdapter.notifyDataSetChanged()
            },
            onScanStarted = {
                SystemTimeline.log("📡 Classic discovery started")
            }
        )
    }
    private fun connectToDevice(device: BluetoothDevice) {
        bleScanManager.stop()

        // Pass execution control over to the ViewModel
        hybridization.connect(device, isBound, bluetoothService)
    }

    private fun startBluetoothService() {
        if (serviceStarted) return
        serviceStarted = true
        val intent = Intent(this, BluetoothService::class.java).apply { `package` = packageName }

        // Uses wrapper helper to launch the foreground service safely
        btManager.startService(intent)

        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    private fun startClassicBluetoothService() {
        if (classicServiceStarted) return
        // Offloads the hardware status check to the wrapper class
        if (!btManager.isBluetoothEnabled()) return
        classicServiceStarted = true
        val intent = Intent(this, ClassicBluetoothService::class.java).apply { `package` = packageName }

        // Uses wrapper helper to launch the foreground service safely
        btManager.startService(intent)

        bindService(intent, classicConnection, BIND_AUTO_CREATE)
    }




    private fun requestBluetoothEnable() {
        enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }
    override fun onDestroy() {
        super.onDestroy()
        delayedStatusRunnable?.let { uiHandler.removeCallbacks(it) }
        try { unregisterReceiver(classicScanReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(bluetoothStateReceiver) } catch (_: Exception) {}
        classicCollectorJob?.cancel()
        bleCollectorJob?.cancel()
        bleScanManager.stop()
        if (isBound) { unbindService(serviceConnection); isBound = false }
        if (isClassicBound) { unbindService(classicConnection); isClassicBound = false }
    }
    override fun onResume() {
        super.onResume()
        checkBluetoothOnEntry()
        registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
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