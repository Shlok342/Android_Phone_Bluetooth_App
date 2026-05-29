// FILE: app/src/main/java/com/example/myapplication/deviceinsights/utils/BluetoothUuidRegistry.kt

package com.example.myapplication

import java.util.UUID

object BluetoothUuidRegistry {

    private val knownServices = mapOf(
        "00001800-0000-1000-8000-00805f9b34fb" to "Generic Access",
        "00001801-0000-1000-8000-00805f9b34fb" to "Generic Attribute",
        "0000180a-0000-1000-8000-00805f9b34fb" to "Device Information",
        "0000180f-0000-1000-8000-00805f9b34fb" to "Battery Service",
        "00001812-0000-1000-8000-00805f9b34fb" to "Human Interface Device"
    )

    private val knownCharacteristics = mapOf(
        "00002a19-0000-1000-8000-00805f9b34fb" to "Battery Level",
        "00002a29-0000-1000-8000-00805f9b34fb" to "Manufacturer Name",
        "00002a24-0000-1000-8000-00805f9b34fb" to "Model Number",
        "00002a26-0000-1000-8000-00805f9b34fb" to "Firmware Revision"
    )

    fun getServiceName(uuid: UUID): String {
        return knownServices[uuid.toString().lowercase()]
            ?: "Unknown Service"
    }

    fun getCharacteristicName(uuid: UUID): String {
        return knownCharacteristics[uuid.toString().lowercase()]
            ?: "Unknown Characteristic"
    }
}