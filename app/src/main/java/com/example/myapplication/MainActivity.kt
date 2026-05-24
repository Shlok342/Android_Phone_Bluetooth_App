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
import kotlinx.coroutines.Job
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import android.net.Uri
import com.google.android.material.button.MaterialButton
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
enum class ActiveTab {
    BLE,
    CLASSIC
}
class MainActivity : AppCompatActivity() {

    // ─── UI State ─────────────────────────────────────────────────────────────
    private lateinit var listView: ListView
    private lateinit var statusText: TextView
    private lateinit var bleHeaderText: TextView
    private lateinit var classicTextHeader: TextView
    private lateinit var deviceAdapter: DeviceAdapter
    private var isScanning = false
    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingRefresh = false
    private var delayedStatusRunnable: Runnable? = null
    private val deviceList = mutableListOf<BleDeviceItem>()
    private val deviceMap = mutableMapOf<String, BluetoothDevice>()
    // ─── Classic State ────────────────────────────────────────────────────────
     // "BLE" or "CLASSIC"
    private var classicServiceStarted = false
    private val classicDeviceList = mutableListOf<ClassicDeviceItem>()
    private val classicDeviceMap = mutableMapOf<String, BluetoothDevice>()
    private lateinit var classicAdapter: ClassicDeviceAdapter
    private lateinit var classicStatusText: TextView
    private lateinit var bleTabBtn: MaterialButton
    private lateinit var classicTabBtn: MaterialButton
    private lateinit var classicListView: ListView
    private var classicCollectorJob: Job? = null
    private var activeTab = ActiveTab.BLE
    private lateinit var backgroundView: GlassmorphicBackgroundView
    private lateinit var classicActionsRow: LinearLayout
    private lateinit var transferStatusText: TextView
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
    // ─── Classic Service Binding ──────────────────────────────────────────────
    private var classicService: ClassicBluetoothService? = null
    private var isClassicBound = false
    fun Int.dp(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
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
                            updateClassicStatusUi(info.state, info.address)
                        }
                    }
                    launch {
                        manager.messages.collect { message ->
                            val display = when (message) {
                                is ClassicMessage.Text ->
                                    "[Classic] ${message.raw}  |  ${message.hex}"
                                is ClassicMessage.Binary ->
                                    "[Classic Binary] ${message.bytes.size} bytes"
                                is ClassicMessage.ParseError ->
                                    "[Parse Error] ${message.reason}"
                            }
                            showDataBottomSheet(display)
                        }
                    }
                    launch {
                        manager.events.collect { event ->
                            showDataBottomSheet("[Log] $event")
                        }
                    }
                    launch {
                        service.fileTransferManager.state.collect { state ->
                            val msg = when (state) {
                                is FileTransferState.Idle        -> null
                                is FileTransferState.Sending     -> "⬆ ${state.filename}: ${(state.progress * 100).toInt()}%"
                                is FileTransferState.Receiving   -> "⬇ ${state.filename}: ${(state.progress * 100).toInt()}%"
                                is FileTransferState.Done        -> if (state.direction == TransferDirection.SEND) "✅ Sent: ${state.filename}"
                                else "✅ Saved: ${state.filename}"
                                is FileTransferState.Failed      -> "❌ ${state.reason}"
                                is FileTransferState.Cancelled -> "⚠ Transfer cancelled"
                            }
                            if (msg != null) showDataBottomSheet("[Transfer] $msg")
                            updateTransferUi(state)
                        }
                    }
                }
            }

            updateClassicStatusUi(
                manager.state.value,
                manager.connectedDeviceAddress ?: ""
            )
            if (activeTab == ActiveTab.CLASSIC) startClassicScan()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            classicCollectorJob?.cancel()
            classicCollectorJob = null
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
                    // Only exclude confirmed BLE-only devices.
                    // UNKNOWN (0) is returned during live discovery before Android resolves the type.
                    if (type == BluetoothDevice.DEVICE_TYPE_LE) return

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
                BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

                    val variant = intent.getIntExtra(
                        BluetoothDevice.EXTRA_PAIRING_VARIANT,
                        BluetoothDevice.ERROR
                    )
                    val pairingType = when (variant) {
                        BluetoothDevice.PAIRING_VARIANT_PIN          -> "Enter PIN"
                        BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION -> "Confirm passkey"
                        6 /* PAIRING_VARIANT_CONSENT */              -> "Confirm pairing"
                        else                                         -> "Pairing"
                    }
                    runOnUiThread {
                        classicStatusText.text =
                            getString(R.string.with, pairingType, device?.address ?: "device")
                    }
                }

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val bondState = intent.getIntExtra(
                        BluetoothDevice.EXTRA_BOND_STATE,
                        BluetoothDevice.BOND_NONE
                    )
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

                    val address = device?.address ?: return
                    runOnUiThread {
                        when (bondState) {
                            BluetoothDevice.BOND_BONDED -> {
                                // Refresh list entry — it might now show a real name
                                val idx = classicDeviceList.indexOfFirst { it.address == address }
                                if (idx != -1) {
                                    val name = try { device.name ?: "Unknown" } catch (_: SecurityException) { "Unknown" }
                                    classicDeviceList[idx] = classicDeviceList[idx].copy(name = name)
                                    classicAdapter.notifyDataSetChanged()
                                }
                                classicStatusText.text = getString(R.string.paired_connecting)
                            }
                            BluetoothDevice.BOND_NONE ->
                                classicStatusText.text = getString(R.string.pairing_failed)
                        }
                    }
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
                )}


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

            val clearBtn = MaterialButton(this).apply {
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

                    data.startsWith("[Log]") -> "#AAAAAA".toColorInt()

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
    private fun updateTransferUi(state: FileTransferState) {
        when (state) {
            is FileTransferState.Idle -> {
                transferStatusText.visibility = View.GONE
            }
            is FileTransferState.Sending -> {
                val pct = (state.progress * 100).toInt()
                transferStatusText.text = getString(R.string.sending_classic, state.filename, pct)
                transferStatusText.visibility = (View.VISIBLE)
            }
            is FileTransferState.Receiving -> {
                val pct = (state.progress * 100).toInt()
                transferStatusText.text = getString(R.string.receiving_classic, state.filename, pct)
                transferStatusText.visibility = View.VISIBLE
            }
            is FileTransferState.Done -> {
                val dir = if (state.direction== TransferDirection.SEND) "Sent" else "Saved to Downloads"
                transferStatusText.text = getString(R.string.transfer_status, dir, state.filename)
            }
            is FileTransferState.Failed -> {
                transferStatusText.text = getString(R.string.transfer_failed, state.reason)
            }
            is FileTransferState.Cancelled -> {
                transferStatusText.text = getString(R.string.transfer_cancelled)
            }
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
    private fun animateStatusText(tv: TextView, newText: String) {
        if (tv.text == newText) return
        tv.animate().alpha(0f).translationY(-8f).setDuration(160).withEndAction {
            tv.text = newText
            tv.translationY = 8f
            tv.animate().alpha(1f).translationY(0f).setDuration(240).start()
        }.start()
    }
    private fun updateStatusUi(state: BleState, address: String) {
        delayedStatusRunnable?.let { uiHandler.removeCallbacks(it) }
        backgroundView.transitionToState(state)
        val name = bluetoothService?.connectedDeviceName ?: "Device"
        val statusMsg = when (state) {
            BleState.IDLE                 -> "Status: Idle"
            BleState.CONNECTING -> {

                delayedStatusRunnable = Runnable {

                    if (bluetoothService?.currentState == BleState.CONNECTING) {


                        animateStatusText(statusText,getString(R.string.connection_taking_longer_than_expected))

                    }
                }

                uiHandler.postDelayed(delayedStatusRunnable!!, 5000)

                "Status: Connecting to $name..."
            }
            BleState.BONDING -> {

                delayedStatusRunnable = Runnable {

                    if (bluetoothService?.currentState == BleState.BONDING) {


                        animateStatusText(statusText,getString(R.string.taking_longer_than_expected_may_disconnect))

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
        animateStatusText(statusText, statusMsg)

        if (state == BleState.DISCONNECTED || state == BleState.FAILED) {
            if (activeTab == ActiveTab.BLE) {          // ← ADD guard
                bottomSheetDialog?.dismiss()
                bottomSheetDialog = null
                bottomSheetList = null
            }
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
            addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)   // ← add this
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }

        registerReceiver(classicScanReceiver, filter)

    }

    private fun setupUi() {
        listView = ListView(this)
        statusText = TextView(this).apply {
            text = getString(R.string.not_connected)
            textSize = 13f
            setTextColor(getColor(R.color.color_text_secondary))
            letterSpacing = 0.03f
            setPadding(24.dp(context), 4.dp(context), 24.dp(context), 10.dp(context))
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
                bleHeaderText = TextView(this@MainActivity).apply {
                text = getString(R.string.nearby_ble_devices)
                textSize = 16f
                setTextColor(getColor(R.color.color_text_primary))
                setTypeface(null, Typeface.BOLD)
                letterSpacing = 0.10f
                setPadding(
                    24.dp(this@MainActivity),
                    32.dp(this@MainActivity),
                    24.dp(this@MainActivity),
                    6.dp(this@MainActivity)
                )
            }
                classicTextHeader = TextView(this@MainActivity).apply {
                text = getString(R.string.nearby_classic_devices)

                textSize = 16f

                setTextColor(getColor(R.color.color_text_primary))

                setTypeface(null, Typeface.BOLD)

                letterSpacing = 0.10f

                setPadding(
                    24.dp(this@MainActivity),
                    32.dp(this@MainActivity),
                    24.dp(this@MainActivity),
                    6.dp(this@MainActivity)
                )

                visibility = View.GONE
            }

            addView(bleHeaderText)
            addView(classicTextHeader)}

        val btnRow = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(
                    16.dp(context),
                    0,
                    16.dp(context),
                    10.dp(context)
                )
            }
        }
        val refreshBtn = MaterialButton(this).apply {
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            text = getString(R.string.refresh)
            textSize = 12f
            letterSpacing = 0.04f
            setTextColor(getColor(R.color.color_text_primary))
            setBackgroundResource(R.drawable.bg_button_glass)
            setPadding(18, 0, 18, 0)
            stateListAnimator = null
            setOnClickListener {
                if (activeTab == ActiveTab.BLE) { pendingRefresh = true; stopBleScan(); bluetoothService?.disconnect() }
                else { stopClassicScan(); startClassicScan() }
            }
        }
        val stopBtn = MaterialButton(this).apply {
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0


            text = getString(R.string.stop_scan)
            textSize = 12f
            letterSpacing = 0.04f
            setTextColor(getColor(R.color.color_text_primary))
            setBackgroundResource(R.drawable.bg_button_glass)
            setPadding(18, 0, 18, 0)
            stateListAnimator = null
            setOnClickListener {
                if (activeTab == ActiveTab.BLE) stopBleScan() else stopClassicScan()
            }
        }
        val disconnectBtn = MaterialButton(this).apply {
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0


            text = getString(R.string.disconnect)
            textSize = 12f
            letterSpacing = 0.04f
            setTextColor(getColor(R.color.color_text_primary))
            setBackgroundResource(R.drawable.bg_button_glass)
            setPadding(18, 0, 18, 0)
            stateListAnimator = null
            setOnClickListener {
                if (activeTab == ActiveTab.BLE) bluetoothService?.disconnect()
                else classicService?.connectionManager?.disconnect()
            }
        }

        btnRow.addView(refreshBtn,
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = 6.dp(this@MainActivity)
            }
        )

        btnRow.addView(stopBtn,
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = 6.dp(this@MainActivity)
            }
        )

        btnRow.addView(disconnectBtn,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        layout.addView(btnRow)

        // ─── Tab buttons ──────────────────────────────────────────────────────────
        bleTabBtn = MaterialButton(this).apply {

            text = context.getString(R.string.BLETABBUTTON)

            textSize = 11f
            letterSpacing = 0.04f

            setTextColor(getColor(R.color.color_text_primary))

            setBackgroundResource(R.drawable.bg_tab_selected)

            minHeight = 0
            minimumHeight = 0

            minWidth = 0
            minimumWidth = 0

            insetTop = 0
            insetBottom = 0

            setPadding(0, 0, 0, 0)

            stateListAnimator = null
        }
        classicTabBtn = MaterialButton(this).apply {

            text = context.getString(R.string.CLASSICTABBUTTON)

            textSize = 11f
            letterSpacing = 0.03f

            setTextColor(getColor(R.color.color_text_secondary))

            setBackgroundResource(R.drawable.bg_tab_unselected)

            minHeight = 0
            minimumHeight = 0

            insetTop = 0
            insetBottom = 0

            setPadding(0, 0, 0, 0)

            stateListAnimator = null
        }

        val tabRow = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(
                    16.dp(context),
                    0,
                    16.dp(context),
                    14.dp(context)
                )
            }
        }
        tabRow.addView(bleTabBtn, LinearLayout.LayoutParams(0, -2, 1f))
        tabRow.addView(classicTabBtn, LinearLayout.LayoutParams(0, -2, 1f))

// ─── Classic status + list ────────────────────────────────────────────────
        classicStatusText = TextView(this).apply {
            text = context.getString(R.string.state_of_classic_devices)
            textSize = 13f
            setTextColor(getColor(R.color.color_text_secondary))
            letterSpacing = 0.03f
            setPadding(24.dp(context), 4.dp(context), 24.dp(context), 10.dp(context))
            visibility = View.GONE
        }
        classicListView = ListView(this).apply { visibility = View.GONE }
        classicAdapter = ClassicDeviceAdapter(classicDeviceList, classicDeviceMap) { device ->
            classicService?.connectionManager?.connect(device)
        }
        classicListView.adapter = classicAdapter
        // ── Classic file transfer controls ────────────────────────────────────
        classicActionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(16.dp(context), 0, 16.dp(context), 8.dp(context)) }
            visibility = View.GONE
        }
        val featuresBtn = MaterialButton(this).apply {
            text = context.getString(R.string.features_button_txt)
            textSize = 12f
            letterSpacing = 0.04f
            setTextColor(getColor(R.color.color_text_primary))
            setBackgroundResource(R.drawable.bg_button_glass)
            setPadding(18, 0, 18, 0)
            minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
            stateListAnimator = null
            setOnClickListener { showClassicFeaturesSheet() }
        }
        classicActionsRow.addView(featuresBtn, LinearLayout.LayoutParams(0, -2, 1f))

        transferStatusText = TextView(this).apply {
            textSize = 12f
            setTextColor(getColor(R.color.color_text_secondary))
            setPadding(24.dp(context), 4.dp(context), 24.dp(context), 4.dp(context))
            visibility = View.GONE
        }
// Insert tab row BEFORE the existing btnRow in layout
// Add these views to layout:
        layout.addView(tabRow)
        layout.addView(classicStatusText)
        layout.addView(classicActionsRow)
        layout.addView(transferStatusText)
        layout.addView(listView, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(classicListView, LinearLayout.LayoutParams(-1, 0, 1f))

// ─── Tab switching logic ──────────────────────────────────────────────────
        bleTabBtn.setOnClickListener {
            activeTab= ActiveTab.BLE
            bleTabBtn.setBackgroundResource(R.drawable.bg_tab_selected)
            bleTabBtn.setTextColor(getColor(R.color.color_text_primary))
            bleHeaderText.visibility = View.VISIBLE
            classicTextHeader.visibility = View.GONE
            classicTabBtn.setBackgroundResource(R.drawable.bg_tab_unselected)
            classicTabBtn.setTextColor(getColor(R.color.color_text_secondary))
            statusText.visibility = View.VISIBLE
            listView.visibility = View.VISIBLE
            classicStatusText.visibility = View.GONE
            classicListView.visibility = View.GONE
            classicActionsRow.visibility = View.GONE
            transferStatusText.visibility = View.GONE

        }
        classicTabBtn.setOnClickListener {
            activeTab = ActiveTab.CLASSIC
            classicTabBtn.setBackgroundResource(R.drawable.bg_tab_selected)
            classicTabBtn.setTextColor(getColor(R.color.color_text_primary))
            bleTabBtn.setBackgroundResource(R.drawable.bg_tab_unselected)
            bleTabBtn.setTextColor(getColor(R.color.color_text_secondary))
            bleHeaderText.visibility = View.GONE
            classicTextHeader.visibility = View.VISIBLE
            statusText.visibility = View.GONE
            listView.visibility = View.GONE
            classicStatusText.visibility = View.VISIBLE
            classicListView.visibility = View.VISIBLE
            classicActionsRow.visibility = View.VISIBLE

            startClassicScan()
        }

        val rootFrame = FrameLayout(this)
        backgroundView = GlassmorphicBackgroundView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        rootFrame.addView(backgroundView)
        rootFrame.addView(layout, FrameLayout.LayoutParams(-1, -1))
        setContentView(rootFrame)
        ViewCompat.setOnApplyWindowInsetsListener(rootFrame) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            layout.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        deviceAdapter = DeviceAdapter(
            devices = deviceList,
            deviceMap = deviceMap,
            onStopScanRequested = { stopBleScan() }, //
            connectCallback = { device -> connectToDevice(device) }
        )
        listView.adapter = deviceAdapter
        listView.layoutAnimation = android.view.animation.AnimationUtils
            .loadLayoutAnimation(this, R.anim.layout_item_slide_in)
        classicListView.layoutAnimation = android.view.animation.AnimationUtils
            .loadLayoutAnimation(this, R.anim.layout_item_slide_in)
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
        if (!adapter.isEnabled) {
            Toast.makeText(this, "Enable Bluetooth first", Toast.LENGTH_SHORT).show()
            return
        }

        classicDeviceList.clear()
        classicDeviceMap.clear()
        classicAdapter.notifyDataSetChanged()

        // ── Pre-load already-paired Classic/Dual devices ──────────────────
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED
            ) {
                adapter.bondedDevices?.forEach { device ->
                    val type = device.type
                    if (type != BluetoothDevice.DEVICE_TYPE_CLASSIC &&
                        type != BluetoothDevice.DEVICE_TYPE_DUAL) return@forEach
                    val address = device.address
                    val name = try { device.name ?: "Unknown" } catch (_: SecurityException) { "Unknown" }
                    classicDeviceMap[address] = device
                    classicDeviceList.add(ClassicDeviceItem(name, address, type))
                }
                classicAdapter.notifyDataSetChanged()
            }
        } catch (_: SecurityException) {}

        // ── Cancel any active discovery before starting fresh ─────────────
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
            == PackageManager.PERMISSION_GRANTED
        ) {
            if (adapter.isDiscovering) adapter.cancelDiscovery()
        }

        try {
            val started = adapter.startDiscovery()
            Toast.makeText(
                this,
                if (started) "Classic scan started ✅" else "Classic scan failed to start ❌",
                Toast.LENGTH_SHORT
            ).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Permission missing for Classic scan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopClassicScan() {
        try { (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter.cancelDiscovery() } catch (_: SecurityException) {}
    }

    private fun updateClassicStatusUi(state: ClassicState, address: String) {
        backgroundView.transitionToClassicState(state)
        val name = classicService?.connectionManager?.connectedDeviceName ?: "Device"
        val statusMsg = when (state) {

                ClassicState.IDLE ->
                    "Classic: Idle"

                ClassicState.CONNECTING ->
                    "Classic: Connecting to $name..."

                ClassicState.CONNECTED ->
                    "🟢 Classic: Connected to $name ($address)"

                ClassicState.DISCONNECTED ->
                    "🔴 Classic: Disconnected"

                is ClassicState.RECONNECTING ->
                    "🔄 Classic: Reconnecting… (${state.attempt}/5)"

                is ClassicState.FAILED -> {

                    when (state.reason) {

                        FailureReason.Timeout ->
                            "⏱ Classic: Timed out"

                        FailureReason.MaxReconnectAttempts ->
                            "❌ Classic: Reconnect limit reached"

                        FailureReason.ConnectionLost ->
                            "❌ Classic: Connection lost"

                        FailureReason.PermissionDenied ->
                            "❌ Classic: Permission denied"

                        FailureReason.SocketClosed ->
                            "❌ Classic: Socket closed"

                        is FailureReason.Unknown ->
                            "❌ Classic: ${state.reason.message}"
                    }
                }
            }
        animateStatusText(classicStatusText,statusMsg)

        if (
            state == ClassicState.DISCONNECTED ||
            state is ClassicState.FAILED
        )   if (activeTab == ActiveTab.CLASSIC){

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
            startClassicBluetoothService()
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
        if (Build.VERSION.SDK_INT >=Build.VERSION_CODES.R) {
            listView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            listView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
        // Hand off the device to your background service
        bluetoothService?.connect(device)
    }

    private fun showClassicFeaturesSheet() {
        val sheet = BottomSheetDialog(this)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 28, 32, 40)
        }

        val title = TextView(this).apply {
            text = context.getString(R.string.classic_features)
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(getColor(R.color.color_text_primary))
            setPadding(0, 0, 0, 20.dp(context))
        }
        container.addView(title)

        val sendFileBtn = MaterialButton(this).apply {
            text = getString(R.string.send_file)
            textSize = 13f
            letterSpacing = 0.03f
            setTextColor(getColor(R.color.color_text_primary))
            setBackgroundResource(R.drawable.bg_button_glass)
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10.dp(context) }
            setOnClickListener {
                sheet.dismiss()
                if (classicService?.connectionManager?.isConnected() == true) {
                    filePickerLauncher.launch("*/*")
                } else {
                    Toast.makeText(this@MainActivity, "Not connected to a Classic device", Toast.LENGTH_SHORT).show()
                }
            }
        }
        container.addView(sendFileBtn)

        // Future feature buttons go here

        sheet.setContentView(container)
        sheet.show()
    }
    override fun onDestroy() {
        super.onDestroy()
        delayedStatusRunnable?.let {
            uiHandler.removeCallbacks(it)
        }
        unregisterReceiver(classicScanReceiver)
        classicCollectorJob?.cancel()
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
        if (!isClassicBound) startClassicBluetoothService()  // ← ADD
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

        view.findViewById< MaterialButton>(R.id.connectBtn).apply {
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

