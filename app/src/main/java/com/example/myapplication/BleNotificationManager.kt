package com.example.myapplication

import android.app.*
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/**
 * Handles the creation and updates of Foreground Service notifications.
 */
class BleNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "ble_channel"
        const val NOTIFICATION_ID = 1
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Bluetooth Connection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Status of active Bluetooth LE connection"
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun buildNotification(text: String): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("BLE Status")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    fun updateNotification(text: String) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
