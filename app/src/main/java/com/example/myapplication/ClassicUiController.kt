package com.example.myapplication

import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import android.widget.ScrollView
import android.view.ViewGroup

import com.google.android.material.bottomsheet.BottomSheetBehavior
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
        val insightsBtn = MaterialButton(activity).apply {
            text = context.getString(R.string.procedural_insights_button)
            textSize = 13f
            letterSpacing = 0.03f
            setTextColor(activity.getColor(R.color.color_text_primary))
            setBackgroundResource(R.drawable.bg_button_glass)
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10.dp(activity) }
            setOnClickListener { sheet.dismiss(); showInsightsModal() }
        }
        container.addView(insightsBtn)
        sheet.setContentView(container)
        sheet.show()
    }

    fun showInsightsModal() {
        val sheet = BottomSheetDialog(activity)
        val screenHeight = activity.resources.displayMetrics.heightPixels
        val contentHeight = (screenHeight * 0.92).toInt()

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, contentHeight
            )
            setPadding(28.dp(activity), 24.dp(activity), 28.dp(activity), 32.dp(activity))
        }

        // ── Header row ──────────────────────────────────────────
        val headerRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, -2)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val title = TextView(activity).apply {
            text = context.getString(R.string.procedural_insights_title)
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(activity.getColor(R.color.color_text_primary))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val clearBtn = MaterialButton(activity).apply {
            text = context.getString(R.string.clear_in_procedural_insights)
            textSize = 11f
            setTextColor(activity.getColor(R.color.color_text_secondary))
            setBackgroundResource(R.drawable.bg_button_glass)
            minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
            stateListAnimator = null
            setPadding(16.dp(activity), 0, 16.dp(activity), 0)
        }
        headerRow.addView(title)
        headerRow.addView(clearBtn)
        root.addView(headerRow)

        // ── Divider ──────────────────────────────────────────────
        root.addView(View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 1).apply {
                topMargin = 14.dp(activity); bottomMargin = 10.dp(activity)
            }
            setBackgroundColor(activity.getColor(R.color.color_glass_border))
        })

        // ── Scrollable event list ────────────────────────────────
        val scrollView = ScrollView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        val eventList = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

        fun rebuildEvents() {
            eventList.removeAllViews()
            val events = SystemTimeline.getEvents()
            if (events.isEmpty()) {
                eventList.addView(TextView(activity).apply {
                    text = context.getString(R.string.events_are_null)
                    textSize = 13f
                    setTextColor(activity.getColor(R.color.color_text_tertiary))
                    setPadding(0, 20.dp(activity), 0, 0)
                })
            } else {
                events.forEach { event ->
                    eventList.addView(TextView(activity).apply {
                        text = event.formatted
                        textSize = 12f
                        typeface = android.graphics.Typeface.MONOSPACE
                        setTextColor(activity.getColor(R.color.color_text_secondary))
                        setPadding(0, 7.dp(activity), 0, 7.dp(activity))
                    })
                    // subtle separator
                    eventList.addView(View(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(-1, 1)
                        setBackgroundColor(0x0DFFFFFF)
                    })
                }
            }
        }

        rebuildEvents()
        clearBtn.setOnClickListener { SystemTimeline.clear(); rebuildEvents() }

        scrollView.addView(eventList)
        root.addView(scrollView)
        sheet.setContentView(root)
        sheet.show()
        sheet.behavior.apply {
            peekHeight=contentHeight
            skipCollapsed = true
            isFitToContents = false
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }
}