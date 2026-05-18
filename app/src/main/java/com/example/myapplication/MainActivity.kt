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
data class BleDeviceItem(
    val name: String,
    val address: String,
    var rssi: Int
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
                pendingRefresh = true

                stopBleScan()

                bluetoothService?.disconnect()
            }
        }
        val stopBtn = Button(this).apply {
            text = getString(R.string.stop_scan)
            setOnClickListener { stopBleScan() }
        }
        val disconnectBtn = Button(this).apply {
            text = getString(R.string.disconnect)
            setOnClickListener { bluetoothService?.disconnect() }
        }

        btnRow.addView(refreshBtn, LinearLayout.LayoutParams(0, -2, 1f))
        btnRow.addView(stopBtn, LinearLayout.LayoutParams(0, -2, 1f))
        btnRow.addView(disconnectBtn, LinearLayout.LayoutParams(0, -2, 1f))

        layout.addView(btnRow)
        layout.addView(listView, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(layout)
        ViewCompat.setOnApplyWindowInsetsListener(layout) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        deviceAdapter = DeviceAdapter(
            devices = deviceList,
            deviceMap = deviceMap,
            onStopScanRequested = { stopBleScan() }, // ✅ Passes your activity's stop function down
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
            startBleScan() // 🌟 FIX: Trigger the automatic startup scan here!
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

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Check if all requested permissions were allowed by the user
        val allGranted = permissions.values.all { it }

        if (allGranted) {
            startBluetoothService()
            startBleScan() // 🌟 FIX: Automatically start scanning the microsecond they click "Allow"
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
        stopBleScan()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
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

