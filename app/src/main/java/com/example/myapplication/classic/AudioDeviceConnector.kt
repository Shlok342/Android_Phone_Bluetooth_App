package com.example.myapplication.classic

import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Actively manages A2DP and Headset profile connections/disconnections.
 */
class AudioDeviceConnector(private val context: Context) {

    private val tag = "AudioDeviceConnector"
    private val bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    @Volatile private var a2dpProxy: BluetoothA2dp? = null
    @Volatile private var hfpProxy: BluetoothHeadset? = null

    private val handler = Handler(Looper.getMainLooper())

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            when (profile) {
                BluetoothProfile.A2DP -> { a2dpProxy = proxy as BluetoothA2dp; Log.d(tag, "A2DP proxy acquired") }
                BluetoothProfile.HEADSET -> { hfpProxy = proxy as BluetoothHeadset; Log.d(tag, "HFP proxy acquired") }
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            when (profile) {
                BluetoothProfile.A2DP -> { a2dpProxy = null; Log.d(tag, "A2DP proxy lost"); bluetoothAdapter?.getProfileProxy(context, this, BluetoothProfile.A2DP) }
                BluetoothProfile.HEADSET -> { hfpProxy = null; Log.d(tag, "HFP proxy lost"); bluetoothAdapter?.getProfileProxy(context, this, BluetoothProfile.HEADSET) }
            }
        }
    }

    init {
        bluetoothAdapter?.getProfileProxy(context, profileListener, BluetoothProfile.A2DP)
        bluetoothAdapter?.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET)
    }

    fun connectIfAudio(device: BluetoothDevice, delayMs: Long = 600L) {
        if (!isAudioDevice(device)) return
        Log.d(tag, "Audio device detected (${device.address}), scheduling profile connects")

        handler.postDelayed({
            // Try A2DP
            a2dpProxy?.let { proxy ->
                try {
                    val state = try { proxy.getConnectionState(device) } catch (_: SecurityException) { BluetoothProfile.STATE_DISCONNECTED }
                    if (state == BluetoothProfile.STATE_DISCONNECTED) {
                        proxy.javaClass.getMethod("connect", BluetoothDevice::class.java).invoke(proxy, device)
                        Log.d(tag, "A2DP connect invoked")
                    }
                } catch (e: Exception) { Log.e(tag, "A2DP connect failed: ${e.message}") }
            }

            // Try HFP (Headset)
            hfpProxy?.let { proxy ->
                try {
                    val state = try { proxy.getConnectionState(device) } catch (_: SecurityException) { BluetoothProfile.STATE_DISCONNECTED }
                    if (state == BluetoothProfile.STATE_DISCONNECTED) {
                        proxy.javaClass.getMethod("connect", BluetoothDevice::class.java).invoke(proxy, device)
                        Log.d(tag, "HFP connect invoked")
                    }
                } catch (e: Exception) { Log.e(tag, "HFP connect failed: ${e.message}") }
            }
        }, delayMs)
    }

    fun disconnectIfAudio(device: BluetoothDevice) {
        if (!isAudioDevice(device)) return
        Log.d(tag, "Disconnecting profiles for ${device.address}")

        // Disconnect A2DP
        a2dpProxy?.let { proxy ->
            try {
                proxy.javaClass.getMethod("disconnect", BluetoothDevice::class.java).invoke(proxy, device)
                Log.d(tag, "A2DP disconnect invoked")
            } catch (e: Exception) { Log.e(tag, "A2DP disconnect failed: ${e.message}") }
        }

        // Disconnect HFP
        hfpProxy?.let { proxy ->
            try {
                proxy.javaClass.getMethod("disconnect", BluetoothDevice::class.java).invoke(proxy, device)
                Log.d(tag, "HFP disconnect invoked")
            } catch (e: Exception) { Log.e(tag, "HFP disconnect failed: ${e.message}") }
        }
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        a2dpProxy?.let { bluetoothAdapter?.closeProfileProxy(BluetoothProfile.A2DP, it) }
        hfpProxy?.let { bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, it) }
        a2dpProxy = null
        hfpProxy = null
    }

    private fun isAudioDevice(device: BluetoothDevice): Boolean = try {
        device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO
    } catch (_: SecurityException) { false }
}
