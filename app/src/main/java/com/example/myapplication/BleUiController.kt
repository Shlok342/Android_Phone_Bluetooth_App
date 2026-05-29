package com.example.myapplication
import android.graphics.Color
import android.graphics.Typeface
import android.view.*
import android.widget.*
import androidx.core.graphics.toColorInt
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import androidx.appcompat.app.AppCompatActivity
class BleUiController(
    private val activity: AppCompatActivity,
    private val statusText: TextView,
    private val backgroundView: GlassmorphicBackgroundView,
    private val onStartBleScan: () -> Unit,
    private val getConnectedDeviceName: () -> String?,
    private val getCurrentBleState: () -> BleState?,
    private val isPendingRefresh: () -> Boolean,
    private val clearPendingRefresh: () -> Unit,
    private val getActiveTab: () -> ActiveTab,
    private val onDismissDataSheet: () -> Unit
){
    private var bottomSheetDialog: BottomSheetDialog? = null
    private var bottomSheetList: LinearLayout? = null
    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var delayedStatusRunnable: Runnable? = null
    fun updateStatusUi(state: BleState, address: String) {
        delayedStatusRunnable?.let { uiHandler.removeCallbacks(it) }
        backgroundView.transitionToState(state)
        val name = (if (address.isNotEmpty()) DeviceNameStore.get(activity, address) else null)
            ?: getConnectedDeviceName()
            ?: "Device"
        val statusMsg = when (state) {
            BleState.IDLE -> activity.getString(R.string.not_connected)
            BleState.CONNECTING -> {
                delayedStatusRunnable = Runnable {
                    if (getCurrentBleState()== BleState.CONNECTING) {
                        animateStatusText(statusText, activity.getString(R.string.connection_taking_longer_than_expected))
                    }
                }
                uiHandler.postDelayed(delayedStatusRunnable!!, 5000)
                "Status: Connecting to $name..."
            }
            BleState.BONDING -> {
                delayedStatusRunnable = Runnable {
                    if (getCurrentBleState() == BleState.BONDING) {
                        animateStatusText(statusText, activity.getString(R.string.taking_longer_than_expected_may_disconnect))
                    }
                }
                uiHandler.postDelayed(delayedStatusRunnable!!, 25000)
                activity.getString(R.string.pairing_new) + name
            }
            BleState.DISCOVERING_SERVICES -> activity.getString(R.string.paired_connecting)
            BleState.READY -> "🟢 Connected: $name ($address)"
            BleState.DISCONNECTED -> {
                if (isPendingRefresh()) {
                    clearPendingRefresh()
                    uiHandler.postDelayed({ onStartBleScan() }, 700)
                }
                "🔴 Disconnected"
            }
            BleState.FAILED -> "❌ Connection Failed"
        }
        animateStatusText(statusText, statusMsg)

        if (state == BleState.DISCONNECTED || state == BleState.FAILED) {
            if (getActiveTab() == ActiveTab.BLE) onDismissDataSheet()
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
    fun showDataBottomSheet(data: String) {
        if (bottomSheetDialog == null) {
            bottomSheetDialog = BottomSheetDialog(activity)
            val scrollView = ScrollView(activity).apply { layoutParams = ViewGroup.LayoutParams(-1, -2) }
            val container = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 24, 32, 32) }
            val headerRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(-1, -2) }
            val title = TextView(activity).apply { text = activity.getString(R.string.live_data); textSize = 18f; setTypeface(null, Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
            val clearBtn = MaterialButton(activity).apply { text = activity.getString(R.string.clear); setOnClickListener { bottomSheetList?.removeAllViews() } }
            headerRow.addView(title); headerRow.addView(clearBtn); container.addView(headerRow)
            bottomSheetList = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            container.addView(bottomSheetList); scrollView.addView(container)
            bottomSheetDialog?.setContentView(scrollView)
            bottomSheetDialog?.setOnDismissListener { bottomSheetDialog = null; bottomSheetList = null }
        }

        val row = TextView(activity).apply {
            text = data; textSize = 13f; setPadding(0, 8, 0, 8)
            setTextColor(when {
                data.startsWith("[Notify]") -> "#7BC8F6".toColorInt()
                data.startsWith("[Read]")   -> "#A8D5A2".toColorInt()
                data.startsWith("[Subscribed]") -> "#F6C97B".toColorInt()
                data.startsWith("[Log]") -> "#AAAAAA".toColorInt()
                else -> Color.WHITE
            })
        }
        bottomSheetList?.addView(row, 0)
        if (bottomSheetDialog?.isShowing != true) bottomSheetDialog?.show()
    }
    fun dismissDataSheet() {
        bottomSheetDialog?.dismiss()
        bottomSheetDialog = null
        bottomSheetList = null
    }

}