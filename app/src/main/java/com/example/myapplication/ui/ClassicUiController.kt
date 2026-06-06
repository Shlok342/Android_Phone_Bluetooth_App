package com.example.myapplication.ui

import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.util.DeviceNameStore
import com.example.myapplication.R
import com.example.myapplication.classic.ClassicConnectionManager
import com.example.myapplication.classic.ClassicState
import com.example.myapplication.classic.FailureReason
import com.example.myapplication.classic.FileTransferState
import com.example.myapplication.classic.TransferDirection
import com.example.myapplication.models.ActiveTab
import com.example.myapplication.models.dp
import com.example.myapplication.classic.ConnectionSecurity
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
    private fun animateStatusText(tv: TextView, newText: String) {
        if (tv.text == newText) return
        tv.animate().alpha(0f).translationY(-8f).setDuration(160).withEndAction {
            tv.text = newText
            tv.translationY = 8f
            tv.animate().alpha(1f).translationY(0f).setDuration(240).start()
        }.start()
    }

    fun updateClassicStatusUi(state: ClassicState, address: String,security: ConnectionSecurity) {
        // Delegate background management
        globalUiStateManager.updateClassicState(state)

        val name = (if (address.isNotEmpty()) DeviceNameStore.get(activity, address) else null)
            ?: getConnectedDeviceName()
            ?: "Device"
        val securityIcon = when (security) {
            ConnectionSecurity.SECURE -> "🔒 "
            ConnectionSecurity.INSECURE -> "🔓 "
            ConnectionSecurity.UNKNOWN -> ""
        }
        val statusMsg = when (state) {
            ClassicState.IDLE -> "Classic: Idle"
            
            ClassicState.CONNECTING -> "Classic: Connecting to $name..."
            
            ClassicState.CONNECTED -> "${securityIcon}🟢 Classic: Connected to $name ($address)"
            
            ClassicState.DISCONNECTED -> "🔴 Classic: Disconnected"
            
            is ClassicState.RECONNECTING -> "🔄 Classic: Reconnecting… (${state.attempt}/${ClassicConnectionManager.RECONNECT_MAX_ATTEMPTS})"
            
            is ClassicState.FAILED -> {
                when (state.reason) {
                    FailureReason.Timeout -> "⏱ Classic: Timed out"
                    FailureReason.MaxReconnectAttempts -> "❌ Classic: Reconnect limit reached"
                    FailureReason.ConnectionLost -> "❌ Classic: Connection lost"
                    FailureReason.PermissionDenied -> "❌ Classic: Permission denied"
                    FailureReason.SocketClosed -> "❌ Classic: Socket closed"
                    is FailureReason.Unknown -> "❌ Classic: ${state.reason.message}"
                }
            }
        }
        animateStatusText(classicStatusText, statusMsg)

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
