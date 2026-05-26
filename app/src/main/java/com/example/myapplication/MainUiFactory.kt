package com.example.myapplication

import android.bluetooth.BluetoothDevice
import android.graphics.Typeface
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    val classicAdapter: ClassicDeviceAdapter
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
        onDisconnect: () -> Unit,
        onTabBle: () -> Unit,
        onTabClassic: () -> Unit,
        onFeatures: () -> Unit,
        connectBleCallback: (BluetoothDevice) -> Unit,
        connectClassicCallback: (BluetoothDevice) -> Unit
    ): UiComponents {

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
            setPadding(24.dp(activity), 32.dp(activity), 24.dp(activity), 6.dp(activity))
        }

        val classicTextHeader = TextView(activity).apply {
            text = activity.getString(R.string.nearby_classic_devices)
            textSize = 16f
            setTextColor(activity.getColor(R.color.color_text_primary))
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.10f
            setPadding(24.dp(activity), 32.dp(activity), 24.dp(activity), 6.dp(activity))
            visibility = View.GONE
        }

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(bleHeaderText)
            addView(classicTextHeader)
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
            text = activity.getString(R.string.refresh)
            textSize = 12f
            letterSpacing = 0.04f
            setTextColor(activity.getColor(R.color.color_text_primary))
            setBackgroundResource(R.drawable.bg_button_glass)
            setPadding(18.dp(activity), 0, 18.dp(activity), 0)
            stateListAnimator = null
            setOnClickListener { onRefresh() }
        }

        val stopBtn = MaterialButton(activity).apply {
            minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
            text = activity.getString(R.string.stop_scan)
            textSize = 12f
            letterSpacing = 0.04f
            setTextColor(activity.getColor(R.color.color_text_primary))
            setBackgroundResource(R.drawable.bg_button_glass)
            setPadding(18.dp(activity), 0, 18.dp(activity), 0)
            stateListAnimator = null
            setOnClickListener { onStopScan() }
        }

        val disconnectBtn = MaterialButton(activity).apply {
            minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
            text = activity.getString(R.string.disconnect)
            textSize = 12f
            letterSpacing = 0.04f
            setTextColor(activity.getColor(R.color.color_text_primary))
            setBackgroundResource(R.drawable.bg_button_glass)
            setPadding(18.dp(activity), 0, 18.dp(activity), 0)
            stateListAnimator = null
            setOnClickListener { onDisconnect() }
        }

        btnRow.addView(refreshBtn, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = 6.dp(activity) })
        btnRow.addView(stopBtn, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = 6.dp(activity) })
        btnRow.addView(disconnectBtn, LinearLayout.LayoutParams(0, -2, 1f))
        layout.addView(btnRow)

        val bleTabBtn = MaterialButton(activity).apply {
            text = activity.getString(R.string.BLETABBUTTON)
            textSize = 11f
            letterSpacing = 0.04f
            setTextColor(activity.getColor(R.color.color_text_primary))
            setBackgroundResource(R.drawable.bg_tab_selected)
            minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
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
        tabRow.addView(bleTabBtn, LinearLayout.LayoutParams(0, -2, 1f))
        tabRow.addView(classicTabBtn, LinearLayout.LayoutParams(0, -2, 1f))
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
        
        val classicAdapter = ClassicDeviceAdapter(classicDeviceList, classicDeviceMap) { device ->
            connectClassicCallback(device)
        }
        classicListView.adapter = classicAdapter

        val classicActionsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(16.dp(activity), 0, 16.dp(activity), 8.dp(activity)) }
            visibility = View.GONE
        }

        val featuresBtn = MaterialButton(activity).apply {
            text = activity.getString(R.string.features_button_txt)
            textSize = 12f
            letterSpacing = 0.04f
            setTextColor(activity.getColor(R.color.color_text_primary))
            setBackgroundResource(R.drawable.bg_button_glass)
            setPadding(18.dp(activity), 0, 18.dp(activity), 0)
            minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
            stateListAnimator = null
            setOnClickListener { onFeatures() }
        }
        classicActionsRow.addView(featuresBtn, LinearLayout.LayoutParams(0, -2, 1f))

        val transferStatusText = TextView(activity).apply {
            textSize = 12f
            setTextColor(activity.getColor(R.color.color_text_secondary))
            setPadding(24.dp(activity), 4.dp(activity), 24.dp(activity), 4.dp(activity))
            visibility = View.GONE
        }

        layout.addView(classicStatusText)
        layout.addView(classicActionsRow)
        layout.addView(transferStatusText)
        layout.addView(listView, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(classicListView, LinearLayout.LayoutParams(-1, 0, 1f))

        bleTabBtn.setOnClickListener {
            onTabBle()
            bleTabBtn.setBackgroundResource(R.drawable.bg_tab_selected)
            bleTabBtn.setTextColor(activity.getColor(R.color.color_text_primary))
            bleHeaderText.visibility = View.VISIBLE
            classicTextHeader.visibility = View.GONE
            classicTabBtn.setBackgroundResource(R.drawable.bg_tab_unselected)
            classicTabBtn.setTextColor(activity.getColor(R.color.color_text_secondary))
            statusText.visibility = View.VISIBLE
            listView.visibility = View.VISIBLE
            classicStatusText.visibility = View.GONE
            classicListView.visibility = View.GONE
            classicActionsRow.visibility = View.GONE
            transferStatusText.visibility = View.GONE
        }

        classicTabBtn.setOnClickListener {
            onTabClassic()
            classicTabBtn.setBackgroundResource(R.drawable.bg_tab_selected)
            classicTabBtn.setTextColor(activity.getColor(R.color.color_text_primary))
            bleTabBtn.setBackgroundResource(R.drawable.bg_tab_unselected)
            bleTabBtn.setTextColor(activity.getColor(R.color.color_text_secondary))
            bleHeaderText.visibility = View.GONE
            classicTextHeader.visibility = View.VISIBLE
            statusText.visibility = View.GONE
            listView.visibility = View.GONE
            classicStatusText.visibility = View.VISIBLE
            classicListView.visibility = View.VISIBLE
            classicActionsRow.visibility = View.VISIBLE
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

        val deviceAdapter = DeviceAdapter(
            devices = bleDeviceList,
            deviceMap = bleDeviceMap,
            connectCallback = { device -> connectBleCallback(device) }
        )
        listView.adapter = deviceAdapter
        
        listView.layoutAnimation = android.view.animation.AnimationUtils.loadLayoutAnimation(activity, R.anim.layout_item_slide_in)
        classicListView.layoutAnimation = android.view.animation.AnimationUtils.loadLayoutAnimation(activity, R.anim.layout_item_slide_in)

        return UiComponents(
            rootFrame, backgroundView, listView, classicListView, statusText,
            classicStatusText, bleHeaderText, classicTextHeader, bleTabBtn,
            classicTabBtn, classicActionsRow, transferStatusText, deviceAdapter, classicAdapter
        )
    }
}
