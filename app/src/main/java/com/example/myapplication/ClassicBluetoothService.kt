package com.example.myapplication
import android.os.Build
import android.content.pm.ServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel // Added for onDestroy
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.launch // Added for observeFlows
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
const val RECONNECT_MAX_ATTEMPTS= ClassicConnectionManager.RECONNECT_MAX_ATTEMPTS
class ClassicBluetoothService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): ClassicBluetoothService = this@ClassicBluetoothService
    }

    private val binder = LocalBinder()

    // Using a SupervisorJob allows child coroutines to fail independently
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastNotifTime = 0L
    private var _audioProfileManager:
            ClassicAudioProfileManager? = null

    val audioProfileManager: ClassicAudioProfileManager
        get() = requireNotNull(_audioProfileManager)

    private val channelId = "classic_bt_channel"
    private val notifId = 2


    private var _connectionManager:
            ClassicConnectionManager? = null
    private var _fileTransferManager: ClassicFileTransferManager? = null
    val fileTransferManager: ClassicFileTransferManager
        get() = requireNotNull(_fileTransferManager) { "FileTransferManager not initialized" }

    @Suppress("SameParameterValue")
    private fun updateBluetoothForeground(statusText: String) {
        // 1. Build the custom notification using your existing function
        val notification = buildNotification(statusText)

        // 2. Safely apply the correct startForeground format based on the OS version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // API 29+
            startForeground(
                notifId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else { // API 28 and below
            startForeground(
                notifId,
                notification
            )
        }
    }
    private fun saveReceivedFile(filename: String, bytes: ByteArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                java.io.File(dir, filename).writeBytes(bytes)
            }
        } catch (_: Exception) {}
    }
    private val a2dpReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context,
                intent: Intent
            ) {

                val device =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE,
                            BluetoothDevice::class.java
                        )
                    else
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE
                        )

                when (intent.action) {

                    BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {

                        val state = intent.getIntExtra(
                            BluetoothProfile.EXTRA_STATE,
                            BluetoothProfile.STATE_DISCONNECTED
                        )

                        audioProfileManager
                            .onA2dpConnectionStateChanged(
                                device,
                                state
                            )
                    }

                    BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED -> {

                        val state = intent.getIntExtra(
                            BluetoothProfile.EXTRA_STATE,
                            BluetoothA2dp.STATE_NOT_PLAYING
                        )

                        audioProfileManager
                            .onA2dpPlayingStateChanged(
                                device,
                                state
                            )
                    }
                }
            }
        }
    val connectionManager: ClassicConnectionManager
        get() = requireNotNull(_connectionManager) {
            "ConnectionManager not initialized"
        }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {

            addAction(
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED
            )

            addAction(
                BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(a2dpReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(a2dpReceiver, filter)
        }// Best practice: call super first
        _audioProfileManager =
            ClassicAudioProfileManager(this)
        _connectionManager = ClassicConnectionManager(this)
        _fileTransferManager = ClassicFileTransferManager(
            connectionManager = _connectionManager!!,
            context           = this,
            scope             = serviceScope
        ).also { mgr ->
            mgr.onFileReceived = { filename, _ ->
                updateNotification("📥 Received: $filename")
            }
        }
        createNotificationChannel()
        // Clean, safe, and handles Android 14+ automatically
        updateBluetoothForeground("Classic BT Ready")


    observeFlows()
}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        // FIX: Cancel the scope to stop collecting flows and prevent memory leaks
        serviceScope.cancel()
        _audioProfileManager?.destroy()
        _audioProfileManager = null
        unregisterReceiver(a2dpReceiver)
        // Safeguard against uninitialized connectionManager if onCreate failed early
        _fileTransferManager?.reset()
        _fileTransferManager = null
        _connectionManager?.let {
            it.disconnect()
            it.destroy()
        }

        super.onDestroy()
    }

    // ─── Observe Manager ───────────────────────────────────

    private fun observeFlows() {
        // FIX: Requires 'import kotlinx.coroutines.launch'
        serviceScope.launch {
            connectionManager.connectionInfo.collect { info ->
                updateNotification(
                    when (val state = info.state) {

                        ClassicState.IDLE ->
                            "Classic BT Ready"

                        ClassicState.CONNECTING ->
                            "Connecting..."

                        ClassicState.CONNECTED ->
                            "Connected: ${info.deviceName}"

                        ClassicState.DISCONNECTED ->
                            "Disconnected"

                        is ClassicState.RECONNECTING ->
                            "Reconnecting… (${state.attempt}/$RECONNECT_MAX_ATTEMPTS)"

                        is ClassicState.FAILED -> {

                            when (state.reason) {

                                FailureReason.Timeout ->
                                    "Connection timed out"

                                FailureReason.MaxReconnectAttempts ->
                                    "Reconnect limit reached"

                                else ->
                                    "Connection failed"
                            }
                        }
                    }
                )
            }
        }

        serviceScope.launch {
            connectionManager.messages

                .collect { message ->
                    val preview = when (message) {
                        is ClassicMessage.Text       -> message.raw
                        is ClassicMessage.Binary     -> "[Binary: ${message.bytes.size}B]"
                        is ClassicMessage.ParseError -> "[Error: ${message.reason}]"
                    }
                    if (message is ClassicMessage.ParseError) {

                        updateNotification(preview.take(40))

                    } else {

                        val now = System.currentTimeMillis()

                        if (now - lastNotifTime >= 1500L) {

                            lastNotifTime = now

                            updateNotification(preview.take(40))
                        }
                    }
                }
        }
    }

    // ─── Notification Helpers ──────────────────────────────

    private fun updateNotification(text: String) {
        val manager =
            getSystemService(NotificationManager::class.java)

        manager?.notify(
            notifId,
            buildNotification(text)
        )
    }

    // Added missing implementation
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId,
            "Bluetooth Service Channel",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows Bluetooth connection status and messages"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    // Added missing implementation
    private fun buildNotification(text: String): Notification {
        val pendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Classic Bluetooth")
            .setContentText(text)
            // Replace with your app's actual drawable resource ID
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
