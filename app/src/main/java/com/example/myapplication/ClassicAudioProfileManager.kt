package com.example.myapplication

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicInteger

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
    val deviceName: String = ""
)

class ClassicAudioProfileManager(
    private val context: Context
) {

    companion object {
        private const val MAX_RECONNECT_ATTEMPTS = 3
    }

    private val managerScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state =
        MutableStateFlow<AudioProfileState>(AudioProfileState.IDLE)

    val state: StateFlow<AudioProfileState> =
        _state.asStateFlow()

    private val _connectionInfo =
        MutableStateFlow(
            AudioConnectionInfo(AudioProfileState.IDLE)
        )

    val connectionInfo: StateFlow<AudioConnectionInfo> =
        _connectionInfo.asStateFlow()

    private var bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE)
                as BluetoothManager).adapter

    private var a2dp: BluetoothA2dp? = null

    private var connectedDevice: BluetoothDevice? = null

    private val reconnectAttempts =
        AtomicInteger(0)

    private var reconnectJob: Job? = null
    private val profileListener: BluetoothProfile.ServiceListener =
        object : BluetoothProfile.ServiceListener {

            override fun onServiceConnected(
                profile: Int,
                proxy: BluetoothProfile
            ) {

                if (profile == BluetoothProfile.A2DP) {

                    a2dp = proxy as BluetoothA2dp

                    checkCurrentlyConnectedDevices()
                }
            }
            override fun onServiceDisconnected(profile: Int) {

                if (profile == BluetoothProfile.A2DP) {

                    a2dp = null

                    updateState(AudioProfileState.DISCONNECTED)
                }
            }
            }
            init {
                bluetoothAdapter?.getProfileProxy(
                    context,
                    profileListener,
                    BluetoothProfile.A2DP
                )
            }





    private fun checkCurrentlyConnectedDevices() {

        val profile = a2dp ?: return

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

        val profile = a2dp ?: return

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
                deviceName = resolveName(device)
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

        a2dp?.let {

            bluetoothAdapter?.closeProfileProxy(
                BluetoothProfile.A2DP,
                it
            )
        }

        managerScope.cancel()
    }
}