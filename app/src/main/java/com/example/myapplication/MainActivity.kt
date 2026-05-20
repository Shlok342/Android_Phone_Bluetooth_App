package com.example.myapplication
import android.Manifest
import android.bluetooth.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import no.nordicsemi.android.support.v18.scanner.*
import androidx.core.graphics.toColorInt
import android.content.Context
data class BleDeviceItem(
    val name: String,
    val address: String,
    var rssi: Int
)
data class ClassicDeviceItem(
    val name: String,
    val address: String,
    val type: Int
)
class MainActivity : AppCompatActivity() {

    // ─── UI State ─────────────────────────────────────────────────────────────
    private lateinit var listView: ListView
    private lateinit var statusText: TextView
    private lateinit var deviceAdapter: DeviceAdapter
    private var isScanning = false
    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingRefresh = false
    private var delayedStatusRunnable: Runnable? = null
    private val deviceList = mutableListOf<BleDeviceItem>()
    private val deviceMap = mutableMapOf<String, BluetoothDevice>()
    // ─── Classic State ────────────────────────────────────────────────────────
    private var activeTab = "BLE" // "BLE" or "CLASSIC"
    private var classicServiceStarted = false
    private val classicDeviceList = mutableListOf<ClassicDeviceItem>()
    private val classicDeviceMap = mutableMapOf<String, BluetoothDevice>()
    private lateinit var classicAdapter: ClassicDeviceAdapter
    private lateinit var classicStatusText: TextView
    private lateinit var bleTabBtn: Button
    private lateinit var classicTabBtn: Button
    private lateinit var classicListView: ListView

    // ─── Classic Service Binding ──────────────────────────────────────────────
    private var classicService: ClassicBluetoothService? = null
    private var isClassicBound = false

    private val classicConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            classicService = (binder as ClassicBluetoothService.LocalBinder).getService()
            isClassicBound = true
            classicService?.connectionManager?.onStateChanged = { state, address ->
                runOnUiThread { updateClassicStatusUi(state, address) }
            }

            classicService?.connectionManager?.onMessageReceived = { message ->
                val display = when (message) {
                    is ClassicMessage.Text ->
                        "[Classic] ${message.raw}  |  ${message.hex}"
                    is ClassicMessage.Binary ->
                        "[Classic Binary] ${message.bytes.size} bytes"
                    is ClassicMessage.ParseError ->
                        "[Parse Error] ${message.reason}"
                }
                runOnUiThread { showDataBottomSheet(display) }
            }

            classicService?.connectionManager?.let {
                updateClassicStatusUi(
                    it.currentState,
                    it.connectedDeviceAddress ?: ""
                )
            }
            if (activeTab == "CLASSIC") startClassicScan()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            classicService = null
            isClassicBound = false
        }
    }

    // ─── Classic Scan (BroadcastReceiver) ────────────────────────────────────
    @Suppress("UnusedPrivateMember")
    private val classicScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

                    device ?: return

                    // ✅ Filter: only Classic or Dual, never BLE-only
                    val type = device.type
                    if (type != BluetoothDevice.DEVICE_TYPE_CLASSIC && type != BluetoothDevice.DEVICE_TYPE_DUAL) return

                    val address = device.address
                    if (classicDeviceMap.containsKey(address)) return

                    val name = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                        ) "Unknown" else device.name ?: "Unknown"
                    } catch (_: SecurityException) { "Unknown" }

                    classicDeviceMap[address] = device
                    classicDeviceList.add(ClassicDeviceItem(name, address, type))
                    runOnUiThread { classicAdapter.notifyDataSetChanged() }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    runOnUiThread { Toast.makeText(this@MainActivity, "Classic scan done", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    // ─── Bottom Sheet for Live Data ───────────────────────────────────────────
    private var bottomSheetDialog: BottomSheetDialog? = null
    private var bottomSheetList: LinearLayout? = null
    private var serviceStarted = false
    private fun showDataBottomSheet(data: String) {

        // Create sheet ONLY once
        if (bottomSheetDialog == null) {

            bottomSheetDialog = BottomSheetDialog(this)

            val scrollView = ScrollView(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 24, 32, 32)
            }

            // Header row
            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val title = TextView(this).apply {
                text = getString(R.string.live_data)
                textSize = 18f
                setTypeface(null, Typeface.BOLD)

                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val clearBtn = Button(this).apply {
                text = getString(R.string.clear)

                setOnClickListener {
                    bottomSheetList?.removeAllViews()
                }
            }

            headerRow.addView(title)
            headerRow.addView(clearBtn)

            container.addView(headerRow)

            // Data container
            bottomSheetList = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            container.addView(bottomSheetList)

            scrollView.addView(container)

            bottomSheetDialog?.setContentView(scrollView)

            bottomSheetDialog?.setOnDismissListener {
                bottomSheetDialog = null
                bottomSheetList = null
            }
        }

        // Create row
        val row = TextView(this).apply {

            text = data

            textSize = 13f

            setPadding(0, 8, 0, 8)

            setTextColor(
                when {

                    data.startsWith("[Notify]") ->
                        "#7BC8F6".toColorInt()

                    data.startsWith("[Read]") ->
                        "#A8D5A2".toColorInt()

                    data.startsWith("[Subscribed]") ->
                        "#F6C97B".toColorInt()

                    else ->
                        Color.WHITE
                }
            )
        }

        // Add newest at top
        bottomSheetList?.addView(row, 0)

        // Show sheet
        if (bottomSheetDialog?.isShowing != true) {
            bottomSheetDialog?.show()
        }
    }

    // ─── Service Binding ──────────────────────────────────────────────────────
    private var bluetoothService: BluetoothService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            bluetoothService = (binder as BluetoothService.LocalBinder).getService()
            isBound = true
            if (!isScanning) {
                startBleScan()
            }
            bluetoothService?.onStateChanged = { state, address ->
                runOnUiThread {
                    updateStatusUi(state, address)
                }
            }

            bluetoothService?.onDataReceived = { data ->
                runOnUiThread { showDataBottomSheet(data) }
            }

            // Sync initial state
            bluetoothService?.let { updateStatusUi(it.currentState, it.connectedDeviceAddress ?: "") }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            bluetoothService = null
            isBound = false
        }
    }

    private fun updateStatusUi(state: BleState, address: String) {
        delayedStatusRunnable?.let {
            uiHandler.removeCallbacks(it)
        }
        val name = bluetoothService?.connectedDeviceName ?: "Device"
        statusText.text = when (state) {
            BleState.IDLE                 -> "Status: Idle"
            BleState.CONNECTING -> {

                delayedStatusRunnable = Runnable {

                    if (bluetoothService?.currentState == BleState.CONNECTING) {

                        statusText.text =
                            getString(R.string.connection_taking_longer_than_expected)

                    }
                }

                uiHandler.postDelayed(delayedStatusRunnable!!, 5000)

                "Status: Connecting to $name..."
            }
            BleState.BONDING -> {

                delayedStatusRunnable = Runnable {

                    if (bluetoothService?.currentState == BleState.BONDING) {

                        statusText.text =
                            getString(R.string.taking_longer_than_expected_may_disconnect)

                    }
                }

                uiHandler.postDelayed(delayedStatusRunnable!!, 3000)

                getString(R.string.pairing_new)  + name
            }
            BleState.DISCOVERING_SERVICES -> getString(R.string.paired_connecting)
            BleState.READY                -> "🟢 Connected: $name ($address)"
            BleState.DISCONNECTED -> {

                if (pendingRefresh) {

                    pendingRefresh = false

                    uiHandler.postDelayed({
                        startBleScan()
                    }, 700)
                }

                "🔴 Disconnected"
            }
            BleState.FAILED               -> "❌ Connection Failed"
        }

        if (state == BleState.DISCONNECTED || state == BleState.FAILED) {
            bottomSheetDialog?.dismiss()
            bottomSheetDialog = null
            bottomSheetList = null
        }
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupUi()
        checkPermissionsAndStartService()
        val filter = IntentFilter().apply {

            addAction(BluetoothDevice.ACTION_FOUND)

            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }

        registerReceiver(classicScanReceiver, filter)

    }

    private fun setupUi() {
        listView = ListView(this)
        statusText = TextView(this).apply {
            text = getString(R.string.not_connected)
            textSize = 16f
            setPadding(20, 10, 20, 10)
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = getString(R.string.nearby_ble_devices)
                textSize = 22f
                setPadding(20, 120, 20, 20)
            })
            addView(statusText)
        }

        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val refreshBtn = Button(this).apply {
            text = getString(R.string.refresh)
            setOnClickListener {
                if (activeTab == "BLE") { pendingRefresh = true; stopBleScan(); bluetoothService?.disconnect() }
                else { stopClassicScan(); startClassicScan() }
            }
        }
        val stopBtn = Button(this).apply {
            text = getString(R.string.stop_scan)
            setOnClickListener {
                if (activeTab == "BLE") stopBleScan() else stopClassicScan()
            }
        }
        val disconnectBtn = Button(this).apply {
            text = getString(R.string.disconnect)
            setOnClickListener {
                if (activeTab == "BLE") bluetoothService?.disconnect()
                else classicService?.connectionManager?.disconnect()
            }
        }

        btnRow.addView(refreshBtn, LinearLayout.LayoutParams(0, -2, 1f))
        btnRow.addView(stopBtn, LinearLayout.LayoutParams(0, -2, 1f))
        btnRow.addView(disconnectBtn, LinearLayout.LayoutParams(0, -2, 1f))

        layout.addView(btnRow)
        layout.addView(listView, LinearLayout.LayoutParams(-1, 0, 1f))
        // ─── Tab buttons ──────────────────────────────────────────────────────────
        bleTabBtn = Button(this).apply { text = context.getString(R.string.BLETABBUTTON) }
        classicTabBtn = Button(this).apply { text = context.getString(R.string.CLASSICTABBUTTON) }

        val tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tabRow.addView(bleTabBtn, LinearLayout.LayoutParams(0, -2, 1f))
        tabRow.addView(classicTabBtn, LinearLayout.LayoutParams(0, -2, 1f))

// ─── Classic status + list ────────────────────────────────────────────────
        classicStatusText = TextView(this).apply {
            text = context.getString(R.string.state_of_classic_devices)
            textSize = 16f
            setPadding(20, 10, 20, 10)
            visibility = View.GONE
        }
        classicListView = ListView(this).apply { visibility = View.GONE }
        classicAdapter = ClassicDeviceAdapter(classicDeviceList, classicDeviceMap) { device ->
            classicService?.connectionManager?.connect(device)
        }
        classicListView.adapter = classicAdapter

// Insert tab row BEFORE the existing btnRow in layout
// Add these views to layout:
        layout.addView(tabRow)
        layout.addView(classicStatusText)
        layout.addView(classicListView, LinearLayout.LayoutParams(-1, 0, 1f))

// ─── Tab switching logic ──────────────────────────────────────────────────
        bleTabBtn.setOnClickListener {
            activeTab = "BLE"
            statusText.visibility = View.VISIBLE
            listView.visibility = View.VISIBLE
            classicStatusText.visibility = View.GONE
            classicListView.visibility = View.GONE
        }
        classicTabBtn.setOnClickListener {
            activeTab = "CLASSIC"
            statusText.visibility = View.GONE
            listView.visibility = View.GONE
            classicStatusText.visibility = View.VISIBLE
            classicListView.visibility = View.VISIBLE
            startClassicScan()
        }

        setContentView(layout)
        ViewCompat.setOnApplyWindowInsetsListener(layout) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        deviceAdapter = DeviceAdapter(
            devices = deviceList,
            deviceMap = deviceMap,
            onStopScanRequested = { stopBleScan() }, //
            connectCallback = { device -> connectToDevice(device) }
        )
        listView.adapter = deviceAdapter
    }

    private fun checkPermissionsAndStartService() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            // CASE 1: Permissions are already good!
            startBluetoothService()
            startClassicBluetoothService()
            startBleScan()
        } else {
            // CASE 2: Show the permission pop-up dialog
            requestPermissionsLauncher.launch(missing.toTypedArray())
        }
    }


    private fun startBluetoothService() {
        if (serviceStarted) return
        serviceStarted = true
        val intent = Intent(this, BluetoothService::class.java).apply {
            `package` = packageName
        }
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

    private fun startClassicScan() {

        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (!adapter.isEnabled) { Toast.makeText(this, "Enable Bluetooth first", Toast.LENGTH_SHORT).show(); return }
        classicDeviceList.clear()
        classicDeviceMap.clear()
        classicAdapter.notifyDataSetChanged()
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        ) {

            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }

        }
        try {
            val started = adapter.startDiscovery()
            Toast.makeText(this, if (started) "Classic scan started ✅" else "Classic scan failed to start ❌", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Permission missing for Classic scan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopClassicScan() {
        try { (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter.cancelDiscovery() } catch (_: SecurityException) {}
    }

    private fun updateClassicStatusUi(state: ClassicState, address: String) {
        val name =
            classicService
                ?.connectionManager
                ?.connectedDeviceName
                ?: "Device"
        classicStatusText.text = when (state) {
            ClassicState.IDLE         -> "Classic: Idle"
            ClassicState.CONNECTING   -> "Classic: Connecting to $name..."
            ClassicState.CONNECTED    -> "🟢 Classic: Connected $name ($address)"
            ClassicState.DISCONNECTED -> "🔴 Classic: Disconnected"
            ClassicState.FAILED       -> "❌ Classic: Failed"
            ClassicState.RECONNECTING ->
                "🔄 Classic: Reconnecting… (${classicService?.connectionManager?.reconnectAttempts}/5)"
            ClassicState.TIMEOUT ->
                "⏱ Classic: Timed out"
        }
        if (state == ClassicState.DISCONNECTED || state == ClassicState.FAILED) {
            bottomSheetDialog?.dismiss()
            bottomSheetDialog = null
            bottomSheetList = null
        }
    }
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Check if all requested permissions were allowed by the user
        val allGranted = permissions.values.all { it }

        if (allGranted) {
            startBluetoothService()
            startBleScan()
        } else {
            Toast.makeText(this, "Permissions denied. Cannot scan.", Toast.LENGTH_SHORT).show()
        }
    }


    // ─── BLE Scan ────────────────────────────────────────────────────────────
    private val scanner = BluetoothLeScannerCompat.getScanner()
    private val scanCallback = object : ScanCallback() {

        // 1. Handles Modern Phones (Processes whole batches smoothly)
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            var listUpdated = false

            // Cache permission status once per batch to avoid checking it hundreds of times
            val hasConnectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            for (result in results) {
                val isChanged = processSingleResult(result, hasConnectPermission)
                if (isChanged) listUpdated = true
            }

            // ONLY refresh the UI thread once per batch window (e.g., every 500ms)
            if (listUpdated) {
                runOnUiThread {
                    deviceAdapter.notifyDataSetChanged()
                }
            }
        }

        // 2. Fallback for Older Phones
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val hasConnectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            val isChanged = processSingleResult(result, hasConnectPermission)
            if (isChanged) {
                runOnUiThread {
                    deviceAdapter.notifyDataSetChanged()
                }
            }
        }

        // 3. Shared parsing logic - COMPLETELY background thread-safe
        private fun processSingleResult(result: ScanResult, hasPermission: Boolean): Boolean {
            val device = result.device
            val address = device.address

            // Safely determine name without crashing or overloading the CPU
            val name = if (hasPermission) {
                try { device.name ?: "Unknown" } catch (_: SecurityException) { "No Permission" }
            } else {
                "No Permission"
            }

            val newEntry = BleDeviceItem(
                name = name,
                address = address,
                rssi = result.rssi
            )

            // OPTIMIZATION: Use your deviceMap to instantly check if a device exists (O(1) speed)
            // instead of slow string searching (it.contains(address))
            val isNewDevice = !deviceMap.containsKey(address)

            deviceMap[address] = device

            if (isNewDevice) {
                android.util.Log.d(
                    "BLE_SCAN",
                    "ADDING DEVICE: $name $address"
                )
                deviceList.add(newEntry)
                return true

            } else {

                val index = deviceList.indexOfFirst {
                    it.address == address
                }

                if (index != -1) {

                    val old = deviceList[index]

                    if (old.rssi != result.rssi || old.name != name) {

                        deviceList[index] = newEntry
                        return true
                    }
                }
            }

            return false

        }
    }


    private fun startBleScan() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Enable Bluetooth first", Toast.LENGTH_SHORT).show()
            return
        }
        stopBleScan()
        deviceList.clear()
        deviceMap.clear()

        runOnUiThread {
            deviceAdapter.notifyDataSetChanged()
        }
        if (::deviceAdapter.isInitialized) {
            deviceAdapter.notifyDataSetChanged()
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            scanner.startScan(
                null, // Passing null scans for all devices efficiently
                settings,
                scanCallback
            )
            isScanning = true

            Toast.makeText(this, "Scanning...", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Location/Bluetooth permissions missing", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopBleScan() {
        try {
            scanner.stopScan(scanCallback)
            isScanning = false
        } catch (_: SecurityException) {}
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (!isBound) {
            Toast.makeText(this, "Service not ready", Toast.LENGTH_SHORT).show()
            return
        }

        // Safely verify scan is stopped if called from places other than the adapter
        if (isScanning) {
            stopBleScan()
        }

        // Safely update the status text on the UI thread
        statusText.text = getString(R.string.button_has_been_clicked)

        // Hand off the device to your background service
        bluetoothService?.connect(device)
    }


    override fun onDestroy() {
        super.onDestroy()
        delayedStatusRunnable?.let {
            uiHandler.removeCallbacks(it)
        }
        unregisterReceiver(classicScanReceiver)
        classicService?.connectionManager?.onStateChanged = null
        classicService?.connectionManager?.onMessageReceived= null
        stopBleScan()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        if (isClassicBound) {
            unbindService(classicConnection)
            isClassicBound = false
        }
    }
    override fun onStart() {
        super.onStart()
        if (!isBound) startBluetoothService()
    }
}

// ─── Simple Adapter ──────────────────────────────────────────────────────────
class DeviceAdapter(
    private val devices: List<BleDeviceItem>,
    private val deviceMap: Map<String, BluetoothDevice>,
    private val onStopScanRequested: () -> Unit, // 🌟 Pass a function pointer to stop the scan
    private val connectCallback: (BluetoothDevice) -> Unit
) : BaseAdapter() {

    override fun getCount() = devices.size
    override fun getItem(p: Int) = devices[p]
    override fun getItemId(p: Int) = p.toLong()

    override fun getView(p: Int, v: View?, parent: ViewGroup): View {
        val view = v ?: LayoutInflater.from(parent.context).inflate(R.layout.device_item, parent, false)
        val item = devices[p]

        view.findViewById<TextView>(R.id.deviceName).text = item.name
        view.findViewById<TextView>(R.id.deviceAddress).text = item.address
        view.findViewById<TextView>(R.id.deviceSignal).text =
            view.context.getString(R.string.rssi, item.rssi)

        view.findViewById<Button>(R.id.connectBtn).apply {
            isEnabled = false       // ADD THIS
            alpha = 0.4f            // ADD THIS
            isAllCaps = false

            val addressLine = item.address

            setOnClickListener {
                // STEP 1: Instantly stop the scanner to preserve main thread performance
                onStopScanRequested()

                // STEP 2: Hand off the device directly to your activity to run connectToDevice()
                if (addressLine.isNotEmpty()) {
                    deviceMap[addressLine]?.let { bluetoothDevice ->
                        connectCallback(bluetoothDevice)
                    } ?: run {
                        Toast.makeText(context, "Device data mismatch. Try refreshing.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }


        return view
    }
}
class ClassicDeviceAdapter(
    private val devices: List<ClassicDeviceItem>,
    private val deviceMap: Map<String, BluetoothDevice>,
    private val connectCallback: (BluetoothDevice) -> Unit
) : BaseAdapter() {
    override fun getCount() = devices.size
    override fun getItem(p: Int) = devices[p]
    override fun getItemId(p: Int) = p.toLong()
    override fun getView(p: Int, v: View?, parent: ViewGroup): View {
        val view = v ?: LayoutInflater.from(parent.context).inflate(R.layout.device_item, parent, false)
        val item = devices[p]
        view.findViewById<TextView>(R.id.deviceName).text = item.name
        view.findViewById<TextView>(R.id.deviceAddress).text = item.address
        view.findViewById<TextView>(R.id.deviceSignal).text =
            if (item.type == BluetoothDevice.DEVICE_TYPE_DUAL) "Dual (Classic+BLE)" else "Classic"
        view.findViewById<Button>(R.id.connectBtn).apply {
            isAllCaps = false
            setOnClickListener {
                deviceMap[item.address]?.let { connectCallback(it) }
                    ?: Toast.makeText(context, "Device not found, try rescanning", Toast.LENGTH_SHORT).show()
            }
        }
        return view
    }
}

