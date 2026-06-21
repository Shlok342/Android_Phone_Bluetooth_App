package com.example.myapplication.ui

import android.bluetooth.BluetoothDevice
import android.graphics.Typeface
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.Gravity
import com.example.myapplication.R
import com.example.myapplication.models.BleDeviceItem
import com.example.myapplication.models.ClassicDeviceItem
import com.example.myapplication.models.dp
import com.example.myapplication.ui.adapters.ClassicDeviceAdapter
import com.example.myapplication.ui.adapters.DeviceAdapter
import com.example.myapplication.ui.sheets.DeviceSearchSheet

import com.google.android.material.button.MaterialButton

data class UiComponents(
    val rootFrame: FrameLayout,
    val backgroundView: GlassmorphicBackgroundView,
    val listView: ListView,
    val classicListView: ListView,
    val statusText: TextView,
    val classicStatusText: TextView,
    val bleHeaderText: TextView,
    val classicTextHeader: TextView,
    val bleTabBtn: MaterialButton,
    val classicTabBtn: MaterialButton,
    val classicActionsRow: LinearLayout,
    val transferStatusText: TextView,
    val deviceAdapter: DeviceAdapter,
    val classicAdapter: ClassicDeviceAdapter,
    val bleClearFilterBtn: MaterialButton,
    val classicClearFilterBtn: MaterialButton,
    val bleFilterDBtn: ImageButton,//D for devices, there was confusion while debugging.
    val bleBluetoothBtn: ImageButton,
    val classicFilterBtn: ImageButton,
    val classicBluetoothBtn: ImageButton
)

object MainUiFactory {

    fun build(

        activity: AppCompatActivity,
        bleDeviceList: MutableList<BleDeviceItem>,
        bleDeviceMap: MutableMap<String, BluetoothDevice>,
        classicDeviceList: List<ClassicDeviceItem>,
        classicDeviceMap: Map<String, BluetoothDevice>,
        onRefresh: () -> Unit,
        onStopScan: () -> Unit,
        onDisconnect:() -> Unit,
        onTabBle: () -> Unit,
        onTabClassic: () -> Unit,
        onFeatures: () -> Unit,
        connectBleCallback: (BluetoothDevice) -> Unit,
        connectClassicCallback: (BluetoothDevice) -> Unit,
        onForgetClassicDevice: (BluetoothDevice) -> Unit,
        onForgetBleDevice: (BluetoothDevice) -> Unit
    ): UiComponents {
        val deviceAdapter = DeviceAdapter(
            adapterContext = activity,
            devices = bleDeviceList,
            deviceMap = bleDeviceMap,
            connectCallback = { device -> connectBleCallback(device) },
            forgetCallback= onForgetBleDevice
        )
        val bluetoothFiltering = com.example.myapplication.util.Filtering(
            permissionChecker = {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    activity,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        )
        val classicAdapter = ClassicDeviceAdapter(
            adapterContext = activity,
            filtering = bluetoothFiltering,
            devices = classicDeviceList,
            deviceMap = classicDeviceMap,
            connectCallback = { device -> connectClassicCallback(device) },
            forgetCallback = onForgetClassicDevice
        )
        val listView = ListView(activity)
        val statusText = TextView(activity).apply {
            text = activity.getString(R.string.not_connected)
            textSize = 13f
            setTextColor(activity.getColor(R.color.color_text_secondary))
            letterSpacing = 0.03f
            setPadding(24.dp(activity), 4.dp(activity), 24.dp(activity), 10.dp(activity))
        }

        val bleHeaderText = TextView(activity).apply {
            text = activity.getString(R.string.nearby_ble_devices)
            textSize = 16f
            setTextColor(activity.getColor(R.color.color_text_primary))
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.10f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val bleSearchBtn = ImageButton(activity).apply {
            setImageResource(R.drawable.ic_search_xml)
            setBackgroundResource(R.drawable.bg_edit_pen)
            setPadding(6.dp(activity), 6.dp(activity), 6.dp(activity), 6.dp(activity))
            layoutParams = LinearLayout.LayoutParams(32.dp(activity), 32.dp(activity))
            contentDescription = activity.getString(R.string.search_devices)
        }
        val bleClearFilterBtn = MaterialButton(activity).apply {
            text = activity.getString(R.string.clear_filter)
            textSize = 11f; letterSpacing = 0.03f
            setTextColor(activity.getColor(R.color.white))
            setBackgroundResource(R.drawable.bg_button_glass)
            backgroundTintList =
                android.content.res.ColorStateList.valueOf(activity.getColor(R.color.color_state_failed))
            minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
            stateListAnimator = null
            setPadding(12.dp(activity), 0, 12.dp(activity), 0)
            layoutParams =
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 28.dp(activity))
                    .apply { marginStart = 8.dp(activity) }
            visibility = View.GONE
            setOnClickListener {
                deviceAdapter.clearFilter()
                visibility = View.GONE
            }
        }
        val bleBluetoothBtn = ImageButton(activity).apply {
            setImageResource(R.drawable.ic_bluetooth_toggle)
            setBackgroundResource(R.drawable.bg_edit_pen)
            setPadding(6.dp(activity), 6.dp(activity), 6.dp(activity), 6.dp(activity))
            layoutParams = LinearLayout.LayoutParams(32.dp(activity), 32.dp(activity)).apply {
                marginStart = 8.dp(activity)
            }

        }
        val bleFilterDBtn = ImageButton(activity).apply {
            setImageResource(R.drawable.ic_filter)
            setBackgroundResource(R.drawable.bg_edit_pen)
            setPadding(6.dp(activity), 6.dp(activity), 6.dp(activity), 6.dp(activity))
            layoutParams = LinearLayout.LayoutParams(32.dp(activity), 32.dp(activity)).apply {
                marginStart = 8.dp(activity)
            }
            contentDescription = "Filter devices"
        }
        val bleFeaturesBtn = MaterialButton(activity).apply {
            text = activity.getString(R.string.features_button_txt)
            textSize = 11f
            letterSpacing = 0.03f

            setTextColor(activity.getColor(R.color.color_text_primary))
            setBackgroundResource(R.drawable.bg_button_glass)

            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0

            stateListAnimator = null

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                32.dp(activity)
            ).apply {
                marginStart = 8.dp(activity)
            }

            setOnClickListener {
                onFeatures()
            }
        }
        val classicBluetoothBtn = ImageButton(activity).apply {
            setImageResource(R.drawable.ic_bluetooth_toggle)
            setBackgroundResource(R.drawable.bg_edit_pen)
            setPadding(6.dp(activity), 6.dp(activity), 6.dp(activity), 6.dp(activity))
            layoutParams = LinearLayout.LayoutParams(32.dp(activity), 32.dp(activity)).apply {
                marginStart = 8.dp(activity)
            }

        }
        val classicFilterBtn = ImageButton(activity).apply {
            setImageResource(R.drawable.ic_filter)
            setBackgroundResource(R.drawable.bg_edit_pen)
            setPadding(6.dp(activity), 6.dp(activity), 6.dp(activity), 6.dp(activity))
            layoutParams = LinearLayout.LayoutParams(32.dp(activity), 32.dp(activity)).apply {
                marginStart = 8.dp(activity)
            }
            contentDescription = "Filter classic devices"
        }
        val classicFeaturesBtn = MaterialButton(activity).apply {
            text = activity.getString(R.string.features_button_txt)
            textSize = 11f
            letterSpacing = 0.03f

            setTextColor(activity.getColor(R.color.color_text_primary))
            setBackgroundResource(R.drawable.bg_button_glass)

            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0

            stateListAnimator = null

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                32.dp(activity)
            ).apply {
                marginStart = 8.dp(activity)
            }

            setOnClickListener {
                onFeatures()
            }
        }
        val bleHeaderRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24.dp(activity), 32.dp(activity), 24.dp(activity), 6.dp(activity))
            addView(bleHeaderText)
            addView(bleBluetoothBtn)
            addView(bleClearFilterBtn)
            addView(bleFilterDBtn)
            addView(bleFeaturesBtn)
            addView(bleSearchBtn)
        }

        val classicTextHeader = TextView(activity).apply {
            text = activity.getString(R.string.nearby_classic_devices)
            textSize = 16f
            setTextColor(activity.getColor(R.color.color_text_primary))
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.10f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val classicSearchBtn = ImageButton(activity).apply {
            setImageResource(R.drawable.ic_search_xml)
            setBackgroundResource(R.drawable.bg_edit_pen)
            setPadding(6.dp(activity), 6.dp(activity), 6.dp(activity), 6.dp(activity))
            layoutParams = LinearLayout.LayoutParams(32.dp(activity), 32.dp(activity))
            contentDescription = activity.getString(R.string.search_devices)
        }
        val classicClearFilterBtn = MaterialButton(activity).apply {
            text = context.getString(R.string.clear_filter)
            textSize = 11f; letterSpacing = 0.03f
            setTextColor(activity.getColor(R.color.white))
            setBackgroundResource(R.drawable.bg_button_glass)
            backgroundTintList =
                android.content.res.ColorStateList.valueOf(activity.getColor(R.color.color_state_failed))
            minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
            stateListAnimator = null
            setPadding(12.dp(activity), 0, 12.dp(activity), 0)
            layoutParams =
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 28.dp(activity))
                    .apply { marginStart = 8.dp(activity) }
            visibility = View.GONE
            setOnClickListener {
                classicAdapter.clearFilter()
                visibility = View.GONE
            }
        }
        val classicHeaderRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24.dp(activity), 32.dp(activity), 24.dp(activity), 6.dp(activity))
            visibility = View.GONE
            addView(classicTextHeader)
            addView(classicClearFilterBtn)
            addView(classicBluetoothBtn)
            addView(classicFilterBtn)
            addView(classicFeaturesBtn)
            addView(classicSearchBtn)

        }

        bleSearchBtn.setOnClickListener {
            DeviceSearchSheet(
                context = activity,
                onFilter = { query, byMac ->
                    deviceAdapter.applyFilter(query, byMac)
                    bleClearFilterBtn.visibility = View.GONE
                },
                onDismissed = { hasQuery ->
                    bleClearFilterBtn.visibility = if (hasQuery) View.VISIBLE else View.GONE
                }
            ).show()
        }
        classicSearchBtn.setOnClickListener {
            DeviceSearchSheet(
                context = activity,
                onFilter = { query, byMac ->
                    classicAdapter.applyFilter(query, byMac)
                    classicClearFilterBtn.visibility = View.GONE
                },
                onDismissed = { hasQuery ->
                    classicClearFilterBtn.visibility = if (hasQuery) View.VISIBLE else View.GONE
                }
            ).show()
        }

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(bleHeaderRow)
            addView(classicHeaderRow)
            addView(statusText)
        }

        val btnRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16.dp(activity), 0, 16.dp(activity), 10.dp(activity))
            }
        }

        val refreshBtn = MaterialButton(activity).apply {
            minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
            gravity = Gravity.CENTER
            text = activity.getString(R.string.refresh)
            textSize = 13f
            letterSpacing = 0.04f
            setTextColor(activity.getColor(R.color.color_text_primary))
            setBackgroundResource(R.drawable.bg_button_glass)
            setPadding(24.dp(activity), 0, 24.dp(activity), 0)
            stateListAnimator = null
            setOnClickListener { onRefresh() }
        }

        val stopBtn = MaterialButton(activity).apply {
            minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
            gravity = Gravity.CENTER
            text = activity.getString(R.string.stop_scan)
            textSize = 13f
            letterSpacing = 0.04f
            setTextColor(activity.getColor(R.color.color_text_primary))
            setBackgroundResource(R.drawable.bg_button_glass)
            setPadding(24.dp(activity), 0, 24.dp(activity), 0)
            stateListAnimator = null
            setOnClickListener { onStopScan() }
        }

        val disconnectBtn = MaterialButton(activity).apply {
            minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
            gravity = Gravity.CENTER
            text = activity.getString(R.string.disconnect)
            textSize = 13f
            letterSpacing = 0.04f
            setTextColor(activity.getColor(R.color.color_text_primary))
            setBackgroundResource(R.drawable.bg_button_glass)
            setPadding(24.dp(activity), 0, 24.dp(activity), 0)
            stateListAnimator = null

            setOnClickListener { onDisconnect() }
        }


        btnRow.addView(refreshBtn, LinearLayout.LayoutParams(0, 48.dp(activity), 1f).apply { marginEnd = 6.dp(activity) })

        btnRow.addView(stopBtn, LinearLayout.LayoutParams(0, 48.dp(activity), 1f).apply { marginEnd = 6.dp(activity) })

        btnRow.addView(disconnectBtn, LinearLayout.LayoutParams(0, 48.dp(activity), 1f))
        layout.addView(btnRow)

        val bleTabBtn = MaterialButton(activity).apply {
            text = activity.getString(R.string.BLETABBUTTON)
            textSize = 11f
            letterSpacing = 0.04f
            setTextColor(activity.getColor(R.color.color_text_primary))
            setBackgroundResource(R.drawable.bg_tab_selected)
            minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
            height = 42.dp(activity)
            textSize = 12f
            insetTop = 0; insetBottom = 0
            setPadding(0, 0, 0, 0)
            stateListAnimator = null
        }

        val classicTabBtn = MaterialButton(activity).apply {
            text = activity.getString(R.string.CLASSICTABBUTTON)
            textSize = 11f
            letterSpacing = 0.03f
            setTextColor(activity.getColor(R.color.color_text_secondary))
            setBackgroundResource(R.drawable.bg_tab_unselected)
            minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
            height = 42.dp(activity)
            textSize = 12f
            insetTop = 0; insetBottom = 0
            setPadding(0, 0, 0, 0)
            stateListAnimator = null
        }

        val tabRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16.dp(activity), 0, 16.dp(activity), 14.dp(activity))
            }
        }
        tabRow.addView(
            bleTabBtn,
            LinearLayout.LayoutParams(0, 42.dp(activity), 1f)
        )

        tabRow.addView(
            classicTabBtn,
            LinearLayout.LayoutParams(0, 42.dp(activity), 1f)
        )
        layout.addView(tabRow)

        val classicStatusText = TextView(activity).apply {
            text = activity.getString(R.string.state_of_classic_devices)
            textSize = 13f
            setTextColor(activity.getColor(R.color.color_text_secondary))
            letterSpacing = 0.03f
            setPadding(24.dp(activity), 4.dp(activity), 24.dp(activity), 10.dp(activity))
            visibility = View.GONE
        }
        val classicListView = ListView(activity).apply { visibility = View.GONE }
        

        classicListView.adapter = classicAdapter

        val classicActionsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(16.dp(activity), 0, 16.dp(activity), 8.dp(activity)) }
            visibility = View.GONE
        }
        val transferStatusText = TextView(activity).apply {
            textSize = 12f
            setTextColor(activity.getColor(R.color.color_text_secondary))
            setPadding(24.dp(activity), 4.dp(activity), 24.dp(activity), 4.dp(activity))
            visibility = View.GONE
        }

        layout.addView(classicStatusText)

        layout.addView(transferStatusText)
        layout.addView(listView, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(classicListView, LinearLayout.LayoutParams(-1, 0, 1f))

        bleTabBtn.setOnClickListener {
            onTabBle()
            bleTabBtn.setBackgroundResource(R.drawable.bg_tab_selected)
            bleTabBtn.setTextColor(activity.getColor(R.color.color_text_primary))
            bleHeaderRow.visibility = View.VISIBLE
            classicHeaderRow.visibility = View.GONE
            classicTabBtn.setBackgroundResource(R.drawable.bg_tab_unselected)
            classicTabBtn.setTextColor(activity.getColor(R.color.color_text_secondary))
            statusText.visibility = View.VISIBLE
            listView.visibility = View.VISIBLE
            classicStatusText.visibility = View.GONE
            classicListView.visibility = View.GONE

            transferStatusText.visibility = View.GONE
        }

        classicTabBtn.setOnClickListener {
            onTabClassic()
            classicTabBtn.setBackgroundResource(R.drawable.bg_tab_selected)
            classicTabBtn.setTextColor(activity.getColor(R.color.color_text_primary))
            bleTabBtn.setBackgroundResource(R.drawable.bg_tab_unselected)
            bleTabBtn.setTextColor(activity.getColor(R.color.color_text_secondary))
            bleHeaderRow.visibility = View.GONE
            classicHeaderRow.visibility = View.VISIBLE
            statusText.visibility = View.GONE
            listView.visibility = View.GONE
            classicStatusText.visibility = View.VISIBLE
            classicListView.visibility = View.VISIBLE

        }

        val rootFrame = FrameLayout(activity)
        val backgroundView = GlassmorphicBackgroundView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        rootFrame.addView(backgroundView)
        rootFrame.addView(layout, FrameLayout.LayoutParams(-1, -1))
        
        activity.setContentView(rootFrame)
        ViewCompat.setOnApplyWindowInsetsListener(rootFrame) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            layout.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        listView.adapter = deviceAdapter
        
        listView.layoutAnimation = AnimationUtils.loadLayoutAnimation(activity, R.anim.layout_item_slide_in)
        classicListView.layoutAnimation = AnimationUtils.loadLayoutAnimation(activity, R.anim.layout_item_slide_in)

        return UiComponents(
            rootFrame, backgroundView, listView, classicListView, statusText,
            classicStatusText, bleHeaderText, classicTextHeader, bleTabBtn,
            classicTabBtn, classicActionsRow, transferStatusText, deviceAdapter, classicAdapter,
            bleClearFilterBtn, classicClearFilterBtn, bleFilterDBtn, bleBluetoothBtn,classicFilterBtn, classicBluetoothBtn
        )
    }
}
