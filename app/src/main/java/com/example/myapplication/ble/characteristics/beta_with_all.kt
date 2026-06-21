package com.example.myapplication.ble.characteristics

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
import com.example.myapplication.ble.BleDataParser

import com.example.myapplication.ble.BleGattRegistry
import com.example.myapplication.ble.BleNotificationManager
import com.example.myapplication.ble.BlePeripheralPolicy
import com.example.myapplication.ble.BleState
import com.example.myapplication.insights.DeviceInsightManager
import java.util.UUID

// 1. The bridge back to your BluetoothService
interface BleEnvironment {
    val bluetoothGatt: BluetoothGatt?
    val currentState: BleState
    val subscribedCharacteristics: Set<String>
    val batteryCharacteristicUuid: UUID
    val cccdUuid: UUID
    fun addSubscribedCharacteristic(uuid: String)
    fun updateBatteryLevel(percent: Int)
    fun enqueue(action: () -> Unit)

    fun emitMessage(message: String)
    fun gattOperationComplete()
    fun requestBondIfNeeded(status: Int)
}
// 2.Modularized writer class
class BleCharacteristicWriter(private val env: BleEnvironment) {
    private lateinit var bleNotificationManager: BleNotificationManager
    private var lastNotifTime = 0L
    private fun updateNotificationThrottled(text: String) {
        val now = System.currentTimeMillis()
        if (now - lastNotifTime > 1500) {
            lastNotifTime = now
            bleNotificationManager.updateNotification(text)
        }
    }
    private fun enableNotifications(characteristic: BluetoothGattCharacteristic) {
        val uuid = characteristic.uuid.toString()

        // 1. Check if already subscribed
        if (env.subscribedCharacteristics.contains(uuid)) {
            env.gattOperationComplete()
            return
        }

        // 2. Safely grab the gatt instance from the environment
        val gatt = env.bluetoothGatt ?: run {
            env.gattOperationComplete()
            return
        }

        try {
            val notificationEnabled = gatt.setCharacteristicNotification(characteristic, true)
            if (!notificationEnabled) {
                env.gattOperationComplete()
                return
            }

            env.emitMessage("[Trying Notify] ${characteristic.uuid}")

            // 3. Grab cccdUuid from the environment
            val descriptor = characteristic.getDescriptor(env.cccdUuid)
            if (descriptor == null) {
                env.gattOperationComplete()
                return
            }

            val value = when {
                characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ->
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0 ->
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                else -> {
                    env.gattOperationComplete()
                    return
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, value)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = value
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        } catch (_: SecurityException) {
            env.gattOperationComplete()
        }
    }
    /**
     * Queues a write to [uuid]. Auto-selects WRITE vs WRITE_NO_RESPONSE from
     * the characteristic's declared properties. Returns false if the
     * characteristic is not found or the connection isn't READY.
     */
        fun writeCharacteristic(uuid: String, value: ByteArray): Boolean {
            val gatt = env.bluetoothGatt ?: return false
            if (env.currentState != BleState.READY) return false

            val characteristic = gatt.services
                ?.flatMap { it.characteristics }
                ?.firstOrNull { it.uuid.toString().equals(uuid, ignoreCase = true) }
                ?: return false

            val writeType = when {
                characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ->
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0 ->
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                else -> return false
            }
            val noAck = writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

            env.enqueue {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeCharacteristic(characteristic, value, writeType)
                    } else {
                        @Suppress("DEPRECATION")
                        characteristic.value = value
                        @Suppress("DEPRECATION")
                        characteristic.writeType = writeType
                        @Suppress("DEPRECATION")
                        gatt.writeCharacteristic(characteristic)
                    }

                    if (noAck) {
                        env.emitMessage("[Write] ${characteristic.uuid} (no-ack)")
                        env.gattOperationComplete()
                    }
                } catch (_: SecurityException) {
                    env.gattOperationComplete()
                }
            }
            return true
        }
    fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        val uuid = descriptor.characteristic.uuid.toString()
        if (status == BluetoothGatt.GATT_SUCCESS) {
            env.addSubscribedCharacteristic(uuid)
            env.emitMessage("[Subscribed] ${descriptor.characteristic.uuid}")
        } else {
           env.emitMessage("[Subscribe Failed] ${descriptor.characteristic.uuid}")
        }
        env.gattOperationComplete()
    }
    fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        val uuid = characteristic.uuid.toString()
        if (status == BluetoothGatt.GATT_SUCCESS) {
            DeviceInsightManager.addDeviceEvent(gatt.device.address, "Write OK: $uuid")
            env.emitMessage("[Write OK] $uuid")
        } else {
            DeviceInsightManager.addDeviceEvent(gatt.device.address, "Write Failed: $uuid (status $status)")
            env.emitMessage("[Write Failed] $uuid (status $status)")
            env.requestBondIfNeeded(status)
        }
        env.gattOperationComplete()}
    fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        val uuid = characteristic.uuid.toString().lowercase()

        if (uuid == "00002a37-0000-1000-8000-00805f9b34fb") {
            val parsed = BleDataParser.parseHeartRate(value)
            updateNotificationThrottled(parsed)
            env.emitMessage("[Notify] $parsed")
            return
        }

        val hex = value.joinToString(" ") { "%02X".format(it) }
        val text = BleDataParser.parseText(value)
        updateNotificationThrottled("📡 ${characteristic.uuid.toString().take(4)}: $text")
        env.emitMessage("[Notify] ${characteristic.uuid} → Hex: $hex | Text: $text")}
    @Suppress("DEPRECATION")
    fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {

        val value = characteristic.value ?: return
        val hex = value.joinToString(" ") { "%02X".format(it) }
        val text = try { String(value, Charsets.UTF_8) } catch (_: Exception) { "Unreadable" }
        env.emitMessage("[Read] ${characteristic.uuid} → Hex: $hex | Text: $text")
        env.requestBondIfNeeded(status)
        env.gattOperationComplete()
    }
    fun setupCharacteristics(
        gatt: BluetoothGatt
    ) {
        val services =
            gatt.services ?: return

        env.emitMessage(
            "[System] Found ${services.size} services"
        )

        for (service in services) {

            env.emitMessage(
                "[Service] ${
                    BleGattRegistry.identifyService(
                        service.uuid.toString()
                    )
                }\n${service.uuid}"
            )

            for (characteristic in service.characteristics) {

                env.emitMessage(
                    "[Characteristic] ${
                        BleGattRegistry.identifyCharacteristic(
                            characteristic.uuid.toString()
                        )
                    }\n${characteristic.uuid}"
                )

                val uuid =
                    characteristic.uuid
                        .toString()
                        .lowercase()

                if (
                    BlePeripheralPolicy.shouldAutoSubscribe(
                        characteristic
                    )
                ) {
                    env.enqueue {
                        enableNotifications(
                            characteristic
                        )
                    }
                }

                if (
                    characteristic.uuid ==
                    env.batteryCharacteristicUuid
                ) {
                    env.enqueue {
                        try {
                            gatt.readCharacteristic(
                                characteristic
                            )
                        } catch (_: SecurityException) {
                            env.gattOperationComplete()
                        }
                    }
                }

                else if (
                    BlePeripheralPolicy.shouldAutoRead(
                        uuid
                    )
                ) {
                    env.enqueue {
                        try {
                            gatt.readCharacteristic(
                                characteristic
                            )
                        } catch (_: SecurityException) {
                            env.gattOperationComplete()
                        }
                    }
                }
            }
        }
    }
    fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
        if (status == BluetoothGatt.GATT_SUCCESS) {

            if (
                characteristic.uuid ==
                env.batteryCharacteristicUuid
            ) {
                if (value.isNotEmpty()) {

                    val batteryPercent =
                        value[0].toInt() and 0xFF

                    env.updateBatteryLevel(batteryPercent)

                    env.emitMessage(
                        "[Battery] $batteryPercent%"
                    )

                    DeviceInsightManager.addDeviceEvent(
                        gatt.device.address,
                        "Battery Level: $batteryPercent%"
                    )
                }
            }

            DeviceInsightManager.addDeviceEvent(
                gatt.device.address,
                "Read Characteristic: ${characteristic.uuid}"
            )

            val hex =
                value.joinToString(" ") {
                    "%02X".format(it)
                }

            val text = try {
                String(value, Charsets.UTF_8)
            } catch (_: Exception) {
                env.requestBondIfNeeded(status)
                "Unreadable"
            }

            env.emitMessage(
                "[Read] ${characteristic.uuid} → Hex: $hex | Text: $text"
            )

        }

        env.gattOperationComplete()
    }
    fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        @Suppress("DEPRECATION")
        val value = characteristic.value ?: return
        val uuid = characteristic.uuid.toString().lowercase()

        if (uuid == "00002a37-0000-1000-8000-00805f9b34fb") {
            val parsed = BleDataParser.parseHeartRate(value)
            updateNotificationThrottled(parsed)
            env.emitMessage("[Notify] $parsed")
            return
        }

        val hex = value.joinToString(" ") { "%02X".format(it) }
        val text = BleDataParser.parseText(value)
        updateNotificationThrottled("📡 ${characteristic.uuid.toString().take(4)}: $text")
        env.emitMessage("[Notify] ${characteristic.uuid} → Hex: $hex | Text: $text")
    }
}
