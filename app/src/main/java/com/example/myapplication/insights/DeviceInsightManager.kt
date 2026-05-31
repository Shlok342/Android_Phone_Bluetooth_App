package com.example.myapplication.insights

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.Context
import android.media.AudioManager
import com.example.myapplication.util.BluetoothUuidRegistry
import com.example.myapplication.util.CharacteristicPropertyParser
import com.example.myapplication.classic.AudioProfileState

object DeviceInsightManager {

    private val sessions = mutableMapOf<String, DeviceInsightSession>()
    private val appEvents = mutableListOf<DeviceEvent>()

    fun onAppEvent(message: String) {
        appEvents.add(DeviceEvent(System.currentTimeMillis(), message))
        if (appEvents.size > 200) appEvents.removeAt(0)
    }

    fun getAppEvents(): List<DeviceEvent> = appEvents

    fun onDeviceConnected(device: BluetoothDevice, transport: String, context: Context) {
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
        session.isAudioPlaying = audioManager.isMusicActive
    }
}