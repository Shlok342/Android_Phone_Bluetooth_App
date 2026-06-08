package com.example.myapplication.ui.controllers

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import com.example.myapplication.R
import com.example.myapplication.classic.ClassicConnectionManager
import com.example.myapplication.classic.file_transfer.FileTransferState
import com.example.myapplication.classic.file_transfer.TransferDirection
import com.example.myapplication.classic.helpers.BatteryErrorProfile
import com.example.myapplication.classic.helpers.ConnectionSecurity
import com.example.myapplication.models.ActiveTab
import com.example.myapplication.models.ClassicState
import com.example.myapplication.models.FailureReason
import com.example.myapplication.models.dp
import com.example.myapplication.ui.sheets.ProceduralInsightsSheet
import com.example.myapplication.util.ConnectionFeedbackHelper
import com.example.myapplication.util.DeviceNameStore
import com.example.myapplication.util.GlobalUiStateManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton

class ClassicUiController (
    private val activity: AppCompatActivity,
    private val classicStatusText: TextView,
    private val transferStatusText: TextView,
    private val globalUiStateManager: GlobalUiStateManager,
    private val onSendFile: () -> Unit,
    private val isClassicConnected: () -> Boolean,
    private val getActiveTab: () -> ActiveTab,
    private val onDismissDataSheet: () -> Unit,
    private val getConnectedDeviceName: () -> String?
){
    // 1. UPDATED: Accepts CharSequence now to support Colors/Spannables
    private fun animateStatusText(tv: TextView, newText: CharSequence) {
        // Compare string content to avoid loops, as CharSequence equals is strict
        if (tv.text.toString() == newText.toString()) return

        tv.animate().alpha(0f).translationY(-8f).setDuration(160).withEndAction {
            tv.text = newText
            tv.translationY = 8f
            tv.animate().alpha(1f).translationY(0f).setDuration(240).start()
        }.start()
    }

    // 2. UPDATED: Returns a colored color integer based on battery health
    private fun getBatteryColor(level: Int, isError: Boolean): Int {
        return when {
            isError -> "#FFB74D".toColorInt() // Amber for Stale/Error
            level <= 15 -> "#EF5350".toColorInt() // Red for Low Battery
            level <= 30 -> "#FFA726".toColorInt() // Orange for Medium-Low
            else -> "#66BB6A".toColorInt()        // Green for Good
        }
    }

    // 3. UPDATED: Main UI Logic with Spannable Building
    fun updateClassicStatusUi(
        state: ClassicState,
        address: String,
        security: ConnectionSecurity,
        batteryLevel: Int = -1,
        batteryError: BatteryErrorProfile = BatteryErrorProfile.None
    ) {
        globalUiStateManager.updateClassicState(state)

        val name = (if (address.isNotEmpty()) DeviceNameStore.get(activity, address) else null)
            ?: getConnectedDeviceName()
            ?: "Device"

        val securityIcon = when (security) {
            ConnectionSecurity.SECURE -> "🔒 "
            ConnectionSecurity.INSECURE -> "🔓 "
            ConnectionSecurity.UNKNOWN -> ""
        }

        // Build the final text object
        val finalMessage: CharSequence = when (state) {
            ClassicState.IDLE -> "Classic: Idle"
            ClassicState.CONNECTING -> "Classic: Connecting to $name..."

            ClassicState.CONNECTED -> {
                ConnectionFeedbackHelper.onConnected(classicStatusText)

                // Start with the base connection string
                val baseText = "${securityIcon}🟢 Classic: Connected to $name ($address)"
                val sb = SpannableStringBuilder(baseText)

                // Determine Battery Text & Color
                if (batteryError !is BatteryErrorProfile.None || batteryLevel != -1) {
                    val (batText, color) = when (batteryError) {
                        is BatteryErrorProfile.None -> {
                            // Standard Valid Battery
                            val c = getBatteryColor(batteryLevel, false)
                            Pair(" [🔋 $batteryLevel%]", c)
                        }
                        is BatteryErrorProfile.StaleDataWarning -> {
                            // Stale Data (Amber)
                            val displayLevel = if(batteryLevel > -1) "$batteryLevel%?" else "Stale"
                            Pair(" [⏳ $displayLevel]", getBatteryColor(-1, true))
                        }
                        is BatteryErrorProfile.ReflectionApiBlocked -> Pair(" [⚠️ API Blocked]", Color.GRAY)
                        is BatteryErrorProfile.DeviceUnsupported -> Pair(" [⚠️ Unsupported]", Color.GRAY)
                    }

                    // Append the battery text
                    val start = sb.length
                    sb.append(batText)

                    // Paint ONLY the battery part
                    sb.setSpan(
                        ForegroundColorSpan(color),
                        start,
                        sb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                sb // Return the built spannable
            }

            ClassicState.DISCONNECTED -> {
                ConnectionFeedbackHelper.onDisconnected(classicStatusText)
                "🔴 Classic: Disconnected"
            }
            is ClassicState.RECONNECTING -> "🔄 Classic: Reconnecting… (${state.attempt}/${ClassicConnectionManager.RECONNECT_MAX_ATTEMPTS})"
            is ClassicState.FAILED -> {
                val reasonStr = when (state.reason) {
                    FailureReason.Timeout -> "Timed out"

                    FailureReason.MaxReconnectAttempts ->
                        "Reconnect limit reached"

                    FailureReason.ConnectionLost ->
                        "Connection lost"

                    FailureReason.PermissionDenied ->
                        "Permission denied"

                    FailureReason.SocketClosed ->
                        "Socket closed"

                    FailureReason.AuthenticationFailed ->
                        "Incorrect PIN or authentication failed."

                    FailureReason.PairingRejected ->
                        "Pairing request was rejected."

                    FailureReason.BondingFailed ->
                        "Pairing failed. Verify the PIN and try again."

                    FailureReason.DeviceRefusedConnection ->
                        "The device refused the connection."

                    is FailureReason.Unknown ->
                        state.reason.message
                }
                "❌ Classic: $reasonStr"
            }
        }

        animateStatusText(classicStatusText, finalMessage)

        if (state == ClassicState.DISCONNECTED || state is ClassicState.FAILED) {
            if (getActiveTab() == ActiveTab.CLASSIC) onDismissDataSheet()
        }
    }

    fun updateTransferUi(state: FileTransferState) {
        when (state) {
            is FileTransferState.Idle -> transferStatusText.visibility = View.GONE
            is FileTransferState.Sending -> {
                val pct = (state.progress * 100).toInt()
                transferStatusText.text = activity.getString(R.string.sending_classic, state.filename, pct)
                transferStatusText.visibility = View.VISIBLE
            }
            is FileTransferState.Receiving -> {
                val pct = (state.progress * 100).toInt()
                transferStatusText.text = activity.getString(R.string.receiving_classic, state.filename, pct)
                transferStatusText.visibility = View.VISIBLE
            }
            is FileTransferState.Done -> {
                val dir = if (state.direction == TransferDirection.SEND) "Sent" else "Saved to Downloads"
                transferStatusText.text = activity.getString(R.string.transfer_status, dir, state.filename)
            }
            is FileTransferState.Failed -> transferStatusText.text = activity.getString(R.string.transfer_failed, state.reason)
            is FileTransferState.Cancelled -> transferStatusText.text = activity.getString(R.string.transfer_cancelled)
        }
    }

    fun showFeaturesSheet() {
        val sheet = BottomSheetDialog(activity)
        val container = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 28, 32, 40) }
        val title = TextView(activity).apply {
            text = activity.getString(R.string.features)
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(activity.getColor(R.color.color_text_primary))
            setPadding(0, 0, 0, 20.dp(activity))
        }
        container.addView(title)

        val sendFileBtn = MaterialButton(activity).apply {
            text = activity.getString(R.string.send_file)
            textSize = 13f
            letterSpacing = 0.03f
            setTextColor(activity.getColor(R.color.color_text_primary))
            setBackgroundResource(R.drawable.bg_button_glass)
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10.dp(activity) }
            setOnClickListener {
                sheet.dismiss()
                if (isClassicConnected()) onSendFile()
                else Toast.makeText(activity, "File transfer requires a Classic connection", Toast.LENGTH_SHORT).show()
            }
        }
        container.addView(sendFileBtn)

        val insightsBtn = MaterialButton(activity).apply {
            text = activity.getString(R.string.procedural_insights_button)
            textSize = 13f
            letterSpacing = 0.03f
            setTextColor(activity.getColor(R.color.color_text_primary))
            setBackgroundResource(R.drawable.bg_button_glass)
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10.dp(activity) }
            setOnClickListener { sheet.dismiss(); ProceduralInsightsSheet(activity).show() }
        }
        container.addView(insightsBtn)

        sheet.setContentView(container)
        sheet.show()
    }
}