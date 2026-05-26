package com.example.myapplication

import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
class ClassicUiController (
    private val activity: AppCompatActivity,
    private val classicStatusText: TextView,
    private val transferStatusText: TextView,
    private val backgroundView: GlassmorphicBackgroundView,
    private val onSendFile: () -> Unit,
    private val isClassicConnected: () -> Boolean,
    private val getActiveTab: () -> ActiveTab,
    private val onDismissDataSheet: () -> Unit,
    private val getConnectedDeviceName: () -> String?
){  private fun animateStatusText(tv: TextView, newText: String) {
    if (tv.text == newText) return
    tv.animate().alpha(0f).translationY(-8f).setDuration(160).withEndAction {
        tv.text = newText
        tv.translationY = 8f
        tv.animate().alpha(1f).translationY(0f).setDuration(240).start()
    }.start()
}
    fun updateClassicStatusUi(state: ClassicState, address: String) {
        backgroundView.transitionToClassicState(state)
        val name = getConnectedDeviceName() ?: "Device"
        val statusMsg = when (state) {
            ClassicState.IDLE -> "Classic: Idle"
            ClassicState.CONNECTING -> "Classic: Connecting to $name..."
            ClassicState.CONNECTED -> "🟢 Classic: Connected to $name ($address)"
            ClassicState.DISCONNECTED -> "🔴 Classic: Disconnected"
            is ClassicState.RECONNECTING -> "🔄 Classic: Reconnecting… (${state.attempt}/${ClassicConnectionManager.RECONNECT_MAX_ATTEMPTS}"
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
    fun showClassicFeaturesSheet() {
        val sheet = BottomSheetDialog(activity)
        val container = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 28, 32, 40) }
        val title = TextView(activity).apply { text = activity.getString(R.string.classic_features); textSize = 17f; setTypeface(null, Typeface.BOLD); setTextColor(activity.getColor(R.color.color_text_primary)); setPadding(0, 0, 0, 20.dp(activity)) }
        container.addView(title)
        val sendFileBtn = MaterialButton(activity).apply { text = activity.getString(R.string.send_file); textSize = 13f; letterSpacing = 0.03f; setTextColor(activity.getColor(R.color.color_text_primary)); setBackgroundResource(R.drawable.bg_button_glass); stateListAnimator = null; layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10.dp(activity) }; setOnClickListener { sheet.dismiss(); if (isClassicConnected()) onSendFile() } }
        container.addView(sendFileBtn)
        sheet.setContentView(container)
        sheet.show()
    }
}