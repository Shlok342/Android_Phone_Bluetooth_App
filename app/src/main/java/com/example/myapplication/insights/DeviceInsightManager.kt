package com.example.myapplication.insights

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import com.example.myapplication.util.BluetoothUuidRegistry
import com.example.myapplication.util.CharacteristicPropertyParser
import com.example.myapplication.classic.AudioProfileState

object DeviceInsightManager {

    private val sessions = mutableMapOf<String, DeviceInsightSession>()
    private val appEvents = mutableListOf<DeviceEvent>()

    private var audioManager: AudioManager? = null
    private val audioPlaybackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
            super.onPlaybackConfigChanged(configs)
            // Whenever playback config changes, re-check if music is active
            updateAudioPlayingState()
        }
        private fun updateAudioPlayingState() {
            if (audioManager == null) return

            // 1. Is the system playing media right now?
            val isMusicActive = audioManager?.isMusicActive == true

            // 2. Is the active audio output routed to a Bluetooth device?
            val activeDevices = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val isRoutedToBluetooth = activeDevices?.any { device ->
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        device.type == AudioDeviceInfo.TYPE_HEARING_AID
            } == true

            // 3. Only count as "playing" if media is active AND it's on Bluetooth
            val isPlayingOnHeadphones = isMusicActive && isRoutedToBluetooth

            // Update all active sessions
            sessions.values.forEach { session ->
                if (session.isAudioDevice) {
                    if (session.isAudioPlaying != isPlayingOnHeadphones) {
                        val statusText = if (isPlayingOnHeadphones) "Started" else "Stopped"
                        addDeviceEvent(session.macAddress, "Audio Playback $statusText")
                    }
                    session.isAudioPlaying = isPlayingOnHeadphones
                }
            }
        }}
    fun onAppEvent(message: String) {
        appEvents.add(DeviceEvent(System.currentTimeMillis(), message))
        if (appEvents.size > 200) appEvents.removeAt(0)
    }

    fun getAppEvents(): List<DeviceEvent> = appEvents
    fun init(context: Context) {
        if (audioManager == null) {
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            // Register the callback to listen for ALL audio changes
            audioManager?.registerAudioPlaybackCallback(
                audioPlaybackCallback,
                Handler(Looper.getMainLooper())
            )
        }
    }

    fun onDeviceConnected(device: BluetoothDevice, transport: String, context: Context) {
        if (audioManager == null) init(context)
        val session = DeviceInsightSession(
            deviceName = try {
                device.name ?: "Unknown"
            } catch (_: SecurityException) {
                "Permission Denied"
            },
            macAddress = device.address,
            transportType = transport,
            connectedAt = System.currentTimeMillis()
        )
        detectAudioCapabilities(device, session, context)
        sessions[device.address] = session
    }

    fun onGattServicesDiscovered(device: BluetoothDevice, gatt: BluetoothGatt) {
        val session = sessions[device.address] ?: return
        session.services.clear()
        gatt.services.forEach { service ->
            val serviceInsight = ServiceInsight(
                serviceName = BluetoothUuidRegistry.getServiceName(service.uuid),
                serviceUuid = service.uuid.toString()
            )
            service.characteristics.forEach { characteristic ->
                serviceInsight.characteristics.add(
                    CharacteristicInsight(
                        characteristicName = BluetoothUuidRegistry.getCharacteristicName(
                            characteristic.uuid
                        ),
                        uuid = characteristic.uuid.toString(),
                        properties = CharacteristicPropertyParser.parse(characteristic.properties)
                    )
                )
            }
            session.services.add(serviceInsight)
        }
    }

    fun updateMtu(device: BluetoothDevice, mtu: Int) {
        sessions[device.address]?.mtu = mtu
    }

    fun updateRssi(deviceAddress: String, rssi: Int) {
        sessions[deviceAddress]?.rssi = rssi
    }

    fun addDeviceEvent(deviceAddress: String, message: String) {
        sessions[deviceAddress]?.events?.add(DeviceEvent(System.currentTimeMillis(), message))
    }

    fun onDisconnected(deviceAddress: String, reason: String) {
        sessions[deviceAddress]?.apply {
            disconnectedAt = System.currentTimeMillis()
            disconnectReason = reason
        }
    }

    fun getSession(macAddress: String): DeviceInsightSession? = sessions[macAddress]
    fun getAllSessions(): List<DeviceInsightSession> = sessions.values.toList()

    private fun detectAudioCapabilities(device: BluetoothDevice, session: DeviceInsightSession, context: Context) {
        val bluetoothClass = try { device.bluetoothClass } catch (_: SecurityException) { null }
        val isAudioDevice = bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO
        session.isAudioDevice = isAudioDevice
        if (!isAudioDevice) return
        session.audioProfiles["A2DP"] = AudioProfileState.CONNECTED
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        session.isAudioPlaying = audioManager.isMusicActive == true
    }
}