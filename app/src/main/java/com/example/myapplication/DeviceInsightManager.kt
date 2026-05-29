// FILE: app/src/main/java/com/example/myapplication/deviceinsights/manager/DeviceInsightManager.kt

package com.example.myapplication

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.Context
import android.media.AudioManager

class DeviceInsightManager(
    context: Context
) {

    private val context =
        context.applicationContext
        private fun safeDeviceName(
            device: BluetoothDevice
        ): String {

            return try {

                if (
                    BluetoothPermissionUtils
                        .hasBluetoothConnectPermission(context)
                ) {
                    device.name ?: "Unknown Device"
                } else {
                    "Permission Denied"
                }

            } catch (_: SecurityException) {

                "Security Exception"
            }
        }
        private fun safeDeviceAddress(
            device: BluetoothDevice
        ): String {

            return try {

                if (
                    BluetoothPermissionUtils
                        .hasBluetoothConnectPermission(context)
                ) {
                    device.address
                } else {
                    "Unknown Address"
                }

            } catch (_: SecurityException) {

                "Security Exception"
            }
        }
        private val sessions =
            mutableMapOf<String, DeviceInsightSession>()

        fun onDeviceConnected(
            device: BluetoothDevice,
            transport: String
        ) {

            val session = DeviceInsightSession(
                deviceName = safeDeviceName(device),

                macAddress = device.address,
                transportType = transport,
                connectedAt = System.currentTimeMillis()
            )

            detectAudioCapabilities(device, session)

            sessions[device.address] = session
        }

        fun onGattServicesDiscovered(
            device: BluetoothDevice,
            gatt: BluetoothGatt
        ) {

            val session = sessions[device.address]
                ?: return

            gatt.services.forEach { service ->

                val serviceInsight = ServiceInsight(
                    serviceName = BluetoothUuidRegistry
                        .getServiceName(service.uuid),
                    serviceUuid = service.uuid.toString()
                )

                service.characteristics.forEach { characteristic ->

                    serviceInsight.characteristics.add(
                        CharacteristicInsight(
                            characteristicName =
                                BluetoothUuidRegistry
                                    .getCharacteristicName(
                                        characteristic.uuid
                                    ),

                            uuid = characteristic.uuid.toString(),

                            properties =
                                CharacteristicPropertyParser
                                    .parse(characteristic.properties)
                        )
                    )
                }

                session.services.add(serviceInsight)
            }
        }

        fun updateMtu(
            device: BluetoothDevice,
            mtu: Int
        ) {
            sessions[device.address]?.mtu = mtu
        }

        fun updateRssi(
            device: BluetoothDevice,
            rssi: Int
        ) {
            sessions[device.address]?.rssi = rssi
        }

        fun addEvent(
            device: BluetoothDevice,
            message: String
        ) {

            sessions[device.address]
                ?.events
                ?.add(
                    DeviceEvent(
                        timestamp = System.currentTimeMillis(),
                        message = message
                    )
                )
        }

        fun onDisconnected(
            device: BluetoothDevice,
            reason: String
        ) {

            sessions[device.address]?.apply {

                disconnectedAt = System.currentTimeMillis()
                disconnectReason = reason
            }
        }

        fun getSession(
            macAddress: String
        ): DeviceInsightSession? {

            return sessions[macAddress]
        }

        fun getAllSessions(): List<DeviceInsightSession> {
            return sessions.values.toList()
        }
    private fun safeBluetoothClass(
        device: BluetoothDevice
    ): BluetoothClass? {

        return try {

            if (
                BluetoothPermissionUtils
                    .hasBluetoothConnectPermission(context)
            ) {
                device.bluetoothClass
            } else {
                null
            }

        } catch (_: SecurityException) {

            null
        }
    }
        private fun detectAudioCapabilities(
            device: BluetoothDevice,
            session: DeviceInsightSession
        ) {

            val bluetoothClass =
                safeBluetoothClass(device)

            val isAudioDevice =
                bluetoothClass?.majorDeviceClass ==
                        BluetoothClass.Device.Major.AUDIO_VIDEO

            session.isAudioDevice = isAudioDevice

            if (!isAudioDevice) return

            session.audioProfiles["A2DP"] =
                AudioProfileState.CONNECTED

            val audioManager =
                context.getSystemService(Context.AUDIO_SERVICE)
                        as AudioManager

            session.isAudioPlaying =
                audioManager.isMusicActive
        }
}