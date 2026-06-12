package com.example.myapplication.classic

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicInteger
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.bluetooth.BluetoothCodecConfig

sealed class AudioProfileState {
    object IDLE : AudioProfileState()

    object CONNECTING : AudioProfileState()

    object CONNECTED : AudioProfileState()

    object PLAYING : AudioProfileState()

    object DISCONNECTED : AudioProfileState()

    data class RECONNECTING(
        val attempt: Int
    ) : AudioProfileState()

    data class FAILED(
        val reason: String
    ) : AudioProfileState()
}

data class AudioConnectionInfo(
    val state: AudioProfileState,
    val address: String = "",
    val deviceName: String = "",
    val codecName: String = "Unknown"
)

class ClassicAudioProfileManager(
    private val context: Context
) {
    companion object {
        private const val MAX_RECONNECT_ATTEMPTS = 3
    }

    private val tag = "ClassicAudioProfile"
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    @Volatile private var isIntentionalDisconnect = false
    @Volatile private var a2dpProxy: BluetoothA2dp? = null
    @Volatile private var hfpProxy: BluetoothHeadset? = null

    private val _state = MutableStateFlow<AudioProfileState>(AudioProfileState.IDLE)
    val state: StateFlow<AudioProfileState> = _state.asStateFlow()

    private val _connectionInfo = MutableStateFlow(AudioConnectionInfo(AudioProfileState.IDLE))
    val connectionInfo: StateFlow<AudioConnectionInfo> = _connectionInfo.asStateFlow()

    private var connectedDevice: BluetoothDevice? = null
    private val reconnectAttempts = AtomicInteger(0)
    private var reconnectJob: Job? = null

    // Dual-profile listener to bind both A2DP and HFP safely
    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            when (profile) {
                BluetoothProfile.A2DP -> {
                    a2dpProxy = proxy as BluetoothA2dp
                    Log.d(tag, "A2DP proxy acquired")
                    checkCurrentlyConnectedDevices()
                }
                BluetoothProfile.HEADSET -> {
                    hfpProxy = proxy as BluetoothHeadset
                    Log.d(tag, "HFP proxy acquired")
                }
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            when (profile) {
                BluetoothProfile.A2DP -> {
                    a2dpProxy = null
                    Log.d(tag, "A2DP proxy lost")
                    updateState(AudioProfileState.DISCONNECTED)
                    if (!isIntentionalDisconnect) {
                        bluetoothAdapter?.getProfileProxy(context, this, BluetoothProfile.A2DP)
                    }
                }
                BluetoothProfile.HEADSET -> {
                    hfpProxy = null
                    Log.d(tag, "HFP proxy lost")
                    if (!isIntentionalDisconnect) {
                        bluetoothAdapter?.getProfileProxy(context, this, BluetoothProfile.HEADSET)
                    }
                }
            }
        }
    }

    init {
        // Request both profiles on initialization
        bluetoothAdapter?.getProfileProxy(context, profileListener, BluetoothProfile.A2DP)
        bluetoothAdapter?.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET)
    }

    /**
     * YOUR INTEGRATED FUNCTION: Cleanly disconnects profiles sequentially
     * to protect your app's concurrent BLE links.
     */
    fun disconnectIfAudio(device: BluetoothDevice) {
        if (!isClassicAudioDevice(device)) return

        // Crucial step: Set this flag immediately so our proxies don't try to auto-reconnect!
        setIntentionalDisconnect(true)
        Log.d(tag, "Initiating safe, sequenced profile disconnect for ${device.address}")

        try {
            // 1. Force release system SCO/Microphone focus to pacify Telecom system_server
            @Suppress("DEPRECATION")
            if (audioManager.isBluetoothScoOn) {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
                Log.d(tag, "Bluetooth SCO audio stream explicitly stopped")
            }
        } catch (e: Exception) { Log.e(tag, "Failed to stop SCO audio: ${e.message}") }

        // 2. Disconnect A2DP (Media) immediately
        a2dpProxy?.let { proxy ->
            try {
                proxy.javaClass.getMethod("disconnect", BluetoothDevice::class.java).invoke(proxy, device)
                Log.d(tag, "A2DP disconnect invoked")
            } catch (e: Exception) { Log.e(tag, "A2DP disconnect failed: ${e.message}") }
        }

        // 3. Postpone HFP (Call/Voice) disconnect to prevent a shared link-layer radio crash
        handler.postDelayed({
            hfpProxy?.let { proxy ->
                try {
                    proxy.javaClass.getMethod("disconnect", BluetoothDevice::class.java).invoke(proxy, device)
                    Log.d(tag, "Sequenced HFP disconnect invoked")

                    // Final state cleanup after the delay clears
                    updateState(AudioProfileState.DISCONNECTED, device)
                } catch (e: Exception) { Log.e(tag, "HFP disconnect failed: ${e.message}") }
            }
        }, 400L)
    }

    fun connectIfAudio(device: BluetoothDevice, delayMs: Long = 600L) {
        if (!isClassicAudioDevice(device)) return
        Log.d(tag, "Audio device detected (${device.address}), scheduling profile connects")

        handler.postDelayed({
            a2dpProxy?.let { proxy ->
                try {
                    val state = try { proxy.getConnectionState(device) } catch (_: SecurityException) { BluetoothProfile.STATE_DISCONNECTED }
                    if (state == BluetoothProfile.STATE_DISCONNECTED) {
                        proxy.javaClass.getMethod("connect", BluetoothDevice::class.java).invoke(proxy, device)
                        Log.d(tag, "A2DP connect invoked")
                    }
                } catch (e: Exception) { Log.e(tag, "A2DP connect failed: ${e.message}") }
            }

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
    fun setIntentionalDisconnect(intentional: Boolean) {
        isIntentionalDisconnect = intentional
        if (intentional) {
            reconnectJob?.cancel()
            reconnectAttempts.set(0)
        }
    }

    private fun isClassicAudioDevice(device: BluetoothDevice): Boolean = try {
        val type = device.type
        val isClassicOrDual = type == BluetoothDevice.DEVICE_TYPE_CLASSIC || type == BluetoothDevice.DEVICE_TYPE_DUAL
        val isAudioClass = device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO
        isClassicOrDual && isAudioClass
    } catch (_: SecurityException) { false }

    @SuppressLint("DiscouragedPrivateApi")
    private fun resolveCodec(device: BluetoothDevice): String {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return "SBC"   
        }

        return try {

            val codecStatus = a2dpProxy?.javaClass
                ?.getMethod("getCodecStatus", BluetoothDevice::class.java)
                ?.invoke(a2dpProxy, device)
                ?: return "Unknown"

            val codecConfig = codecStatus.javaClass
                .getMethod("getCodecConfig")
                .invoke(codecStatus)
                ?: return "Unknown"

            val codecType = codecConfig.javaClass
                .getMethod("getCodecType")
                .invoke(codecConfig) as? Int
                ?: return "Unknown"

            @Suppress("DEPRECATION")
            when (codecType) {

                BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC ->
                    "SBC"

                BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC ->
                    "AAC"

                BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX ->
                    "aptX"

                BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX_HD ->
                    "aptX HD"

                BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC ->
                    "LDAC"

                else ->
                    "Unknown ($codecType)"
            }

        } catch (_: Exception) {

            "Unknown"
        }
    }





    private fun checkCurrentlyConnectedDevices() {

        val profile = a2dpProxy ?: return

        if (!hasConnectPermission()) return

        try {

            val devices = profile.connectedDevices

            if (devices.isNotEmpty()) {

                val device = devices.first()

                connectedDevice = device

                updateState(
                    AudioProfileState.CONNECTED,
                    device
                )
            }

        } catch (_: SecurityException) {
        }
    }

    fun onA2dpConnectionStateChanged(
        device: BluetoothDevice?,
        state: Int
    ) {

        when (state) {

            BluetoothProfile.STATE_CONNECTED -> {
                isIntentionalDisconnect = false
                reconnectAttempts.set(0)
                connectedDevice = device

                updateState(
                    AudioProfileState.CONNECTED,
                    device
                )
            }

            BluetoothProfile.STATE_CONNECTING -> {

                updateState(
                    AudioProfileState.CONNECTING,
                    device
                )
            }

            BluetoothProfile.STATE_DISCONNECTED -> {

                updateState(
                    AudioProfileState.DISCONNECTED,
                    device
                )

                scheduleReconnect()
            }
        }
    }

    fun onA2dpPlayingStateChanged(
        device: BluetoothDevice?,
        state: Int
    ) {

        when (state) {

            BluetoothA2dp.STATE_PLAYING -> {

                updateState(
                    AudioProfileState.PLAYING,
                    device
                )
            }

            BluetoothA2dp.STATE_NOT_PLAYING -> {

                updateState(
                    AudioProfileState.CONNECTED,
                    device
                )
            }
        }
    }

    private fun scheduleReconnect() {
        if (isIntentionalDisconnect) return
        val device = connectedDevice ?: return

        if (reconnectAttempts.get() >= MAX_RECONNECT_ATTEMPTS) {

            updateState(
                AudioProfileState.FAILED(
                    "Reconnect limit reached"
                )
            )

            return
        }

        reconnectJob?.cancel()

        reconnectJob = managerScope.launch {

            val attempt =
                reconnectAttempts.incrementAndGet()

            updateState(
                AudioProfileState.RECONNECTING(attempt),
                device
            )

            delay(
                when (attempt) {
                    1 -> 1000L
                    2 -> 2000L
                    else -> 4000L
                }
            )

            tryReconnect(device)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun tryReconnect(device: BluetoothDevice) {

        val profile = a2dpProxy ?: return

        if (!hasConnectPermission()) return

        try {

            val method = profile.javaClass.getMethod(
                "connect",
                BluetoothDevice::class.java
            )

            method.invoke(profile, device)

        } catch (_: Exception) {

            updateState(
                AudioProfileState.FAILED(
                    "Reconnect failed"
                ),
                device
            )
        }
    }

    private fun updateState(
        state: AudioProfileState,
        device: BluetoothDevice? = connectedDevice
    ) {

        _state.value = state

        _connectionInfo.value =
            AudioConnectionInfo(
                state = state,
                address = device?.address ?: "",
                deviceName = resolveName(device),
                codecName  = if (device != null) resolveCodec(device) else ""
            )
    }

    private fun resolveName(
        device: BluetoothDevice?
    ): String {

        if (device == null) return "Unknown"

        return try {

            if (!hasConnectPermission()) {
                "Unknown"
            } else {
                device.name ?: "Unknown"
            }

        } catch (_: SecurityException) {
            "Unknown"
        }
    }

    private fun hasConnectPermission(): Boolean {

        if (Build.VERSION.SDK_INT <
            Build.VERSION_CODES.S
        ) return true

        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun destroy() {

        reconnectJob?.cancel()

        a2dpProxy?.let {

            bluetoothAdapter?.closeProfileProxy(
                BluetoothProfile.A2DP,
                it
            )
        }

        managerScope.cancel()
    }

}