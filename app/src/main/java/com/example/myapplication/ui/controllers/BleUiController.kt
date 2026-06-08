package com.example.myapplication.ui.controllers

import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import com.example.myapplication.R
import com.example.myapplication.ble.BleState
import com.example.myapplication.classic.helpers.ConnectionSecurity
import com.example.myapplication.models.ActiveTab
import com.example.myapplication.util.ConnectionFeedbackHelper
import com.example.myapplication.util.GlobalUiStateManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

class BleUiController(
    private val activity: AppCompatActivity,
    private val statusText: TextView,
    private val globalUiStateManager: GlobalUiStateManager,
    private val onStartBleScan: () -> Unit,
    private val getConnectedDeviceName: () -> String?,
    private val isPendingRefresh: () -> Boolean,
    private val clearPendingRefresh: () -> Unit,
    private val getActiveTab: () -> ActiveTab,
    private val onDismissDataSheet: () -> Unit
){
    private var bottomSheetDialog: BottomSheetDialog? = null
    private var bottomSheetList: LinearLayout? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    private var delayedStatusRunnable: Runnable? = null

    // 1. NEW: Helper to determine color based on percentage
    private fun getBatteryColor(level: Int): Int {
        return when {
            level <= 15 -> "#EF5350".toColorInt() // Red
            level <= 30 -> "#FFA726".toColorInt() // Orange
            else -> "#66BB6A".toColorInt()        // Green
        }
    }

    // 2. UPDATED: Signature now includes batteryLevel
    fun updateStatusUi(
        state: BleState,
        address: String,
        security: ConnectionSecurity,
        batteryLevel: Int? = null
    ) {
        // ALWAYS cancel pending timers when ANY state change occurs
        cancelDelayedStatus()

        // Delegate background management to the mediator
        globalUiStateManager.updateBleState(state)

        val name = getConnectedDeviceName() ?: "Device"
        val securityIcon = when (security) {
            ConnectionSecurity.SECURE -> "🔒 "
            else -> ""
        }

        // 3. UPDATED: Logic to build colored text objects
        val finalMessage: CharSequence = when (state) {
            BleState.IDLE -> activity.getString(R.string.not_connected)

            BleState.CONNECTING -> {
                startDelayedStatus(activity.getString(R.string.connection_taking_longer_than_expected), 6000L)
                "Status: Connecting to $name..."
            }

            BleState.BONDING -> {
                startDelayedStatus(activity.getString(R.string.taking_longer_than_expected_may_disconnect), 4000L)
                activity.getString(R.string.pairing_new) + " " + name
            }

            BleState.DISCOVERING_SERVICES -> {
                startDelayedStatus("Setting up services...", 5000L)
                activity.getString(R.string.paired_connecting)
            }

            BleState.READY -> {
                ConnectionFeedbackHelper.onConnected(statusText)

                // Build the base string
                val baseText = "${securityIcon}🟢 BLE: Connected to $name ($address)"
                val sb = SpannableStringBuilder(baseText)

                // Append and color the battery if available
                if (batteryLevel != null) {
                    val batText = " [🔋 $batteryLevel%]"
                    val color = getBatteryColor(batteryLevel)

                    val start = sb.length
                    sb.append(batText)

                    // Apply color only to the battery segment
                    sb.setSpan(
                        ForegroundColorSpan(color),
                        start,
                        sb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                sb // Return the SpannableStringBuilder
            }

            BleState.DISCONNECTED -> {
                ConnectionFeedbackHelper.onDisconnected(statusText)
                if (isPendingRefresh()) {
                    clearPendingRefresh()
                    uiHandler.postDelayed({ onStartBleScan() }, 700)
                }
                "🔴 Disconnected"
            }

            BleState.FAILED -> "❌ Connection Failed"
        }

        animateStatusText(statusText, finalMessage)

        if (state == BleState.DISCONNECTED || state == BleState.FAILED) {
            if (getActiveTab() == ActiveTab.BLE) onDismissDataSheet()
        }
    }

    private fun startDelayedStatus(message: String, delay: Long) {
        delayedStatusRunnable = Runnable {
            animateStatusText(statusText, message)
        }
        uiHandler.postDelayed(delayedStatusRunnable!!, delay)
    }

    private fun cancelDelayedStatus() {
        delayedStatusRunnable?.let { uiHandler.removeCallbacks(it) }
        delayedStatusRunnable = null
    }

    // 4. UPDATED: Accepts CharSequence to support both String and SpannableStringBuilder
    private fun animateStatusText(tv: TextView, newText: CharSequence) {
        if (tv.text.toString() == newText.toString()) return

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