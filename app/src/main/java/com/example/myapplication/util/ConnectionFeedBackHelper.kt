package com.example.myapplication.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Haptic feedback that mirrors system Bluetooth's connect/disconnect response.
 * Use only from UI controllers (BleUiController, ClassicUiController) — not
 * from services, which have no attached View.
 */
object ConnectionFeedbackHelper {

    /**
     * Call exactly once when BleState.READY or ClassicState.CONNECTED is first observed.
     * Double-pulse = confirmed link.
     */
    fun onConnected(anchorView: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            anchorView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            vibrateLegacy(anchorView.context, longArrayOf(0, 40, 60, 80))
        }
    }

    /**
     * Call when the link drops — intentional or not.
     * Short rejection pulse.
     */
    fun onDisconnected(anchorView: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            anchorView.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            vibrateLegacy(anchorView.context, longArrayOf(0, 80, 40, 40))
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrateLegacy(context: Context, pattern: LongArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
                    .vibrate(pattern, -1)
            }
        } catch (_: Exception) { /* vibration is non-critical */ }
    }
}