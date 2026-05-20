package com.example.myapplication
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ClassicBluetoothService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): ClassicBluetoothService = this@ClassicBluetoothService
    }

    private val binder = LocalBinder()
    private var lastNotifTime = 0L
    private val mainHandler =
        android.os.Handler(android.os.Looper.getMainLooper())

    private fun updateNotification(text: String) {

        mainHandler.post {

            getSystemService(NotificationManager::class.java)
                .notify(notifId, buildNotification(text))
        }
    }
    private fun updateNotificationThrottled(text: String) {

        val now = System.currentTimeMillis()

        if (now - lastNotifTime > 1500) {

            lastNotifTime = now

            updateNotification(text)
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    // ─── Connection Manager ────────────────────────────────

    lateinit var connectionManager: ClassicConnectionManager
        private set

    // ─── Notification ──────────────────────────────────────

    private val channelId = "classic_bt_channel"
    private val notifId = 2

    override fun onCreate() {
        connectionManager = ClassicConnectionManager(this)
        super.onCreate()

        createNotificationChannel()

        startForeground(
            notifId,
            buildNotification("Classic BT Ready")
        )

        observeConnection()
    }
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        return START_STICKY
    }

    override fun onDestroy() {

        connectionManager.onStateChanged = null
        connectionManager.onMessageReceived = null

        connectionManager.disconnect()
        connectionManager.destroy()

        super.onDestroy()
    }

    // ─── Observe Manager ───────────────────────────────────

    private fun observeConnection() {

        connectionManager.onStateChanged =
            { state, _ ->

                when (state) {

                    ClassicState.IDLE ->
                        updateNotification("Classic BT Ready")

                    ClassicState.CONNECTING ->
                        updateNotification("Connecting...")

                    ClassicState.CONNECTED ->
                        updateNotification(
                            "Connected: ${
                                connectionManager.connectedDeviceName
                            }"
                        )

                    ClassicState.DISCONNECTED ->
                        updateNotification("Disconnected")

                    ClassicState.FAILED ->
                        updateNotification("Connection failed")
                    ClassicState.RECONNECTING ->
                        updateNotification(
                            "Reconnecting… (${connectionManager.reconnectAttempts}/${5})"
                        )

                    ClassicState.TIMEOUT ->
                        updateNotification("Connection timed out")
                }
            }

        connectionManager.onMessageReceived = { message ->
            val preview = when (message) {
                is ClassicMessage.Text       -> message.raw
                is ClassicMessage.Binary     -> "[Binary: ${message.bytes.size}B]"
                is ClassicMessage.ParseError -> "[Parse error: ${message.reason}]"
            }
            updateNotificationThrottled(preview.take(40))
        }
    }

    // ─── Notification Helpers ──────────────────────────────

    private fun createNotificationChannel() {

        val channel = NotificationChannel(
            channelId,
            "Classic Bluetooth",
            NotificationManager.IMPORTANCE_LOW
        )

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {

        val intent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Classic BT")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(intent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }


}