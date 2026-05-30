package com.example.myapplication

import android.bluetooth.BluetoothGattCharacteristic
import java.nio.charset.Charset

/**
 * Static registry of standard Bluetooth GATT UUIDs and their human-readable names.
 */
object BleGattRegistry {
    fun identifyService(uuid: String): String {
        return when (uuid.lowercase()) {
            "0000180f-0000-1000-8000-00805f9b34fb" -> "🔋 Battery Service"
            "0000180d-0000-1000-8000-00805f9b34fb" -> "❤️ Heart Rate Service"
            "0000180a-0000-1000-8000-00805f9b34fb" -> "📱 Device Information Service"
            "00001808-0000-1000-8000-00805f9b34fb" -> "🌡️ Glucose Service"
            "00001816-0000-1000-8000-00805f9b34fb" -> "🚴 Cycling Speed & Cadence"
            "0000181a-0000-1000-8000-00805f9b34fb" -> "🩺 Environmental Sensing"
            "00001810-0000-1000-8000-00805f9b34fb" -> "🩸 Blood Pressure"
            "00001812-0000-1000-8000-00805f9b34fb" -> "🧍 Human Interface Device"
            "00001814-0000-1000-8000-00805f9b34fb" -> "🏃 Running Speed & Cadence"
            else -> "❓ Unknown Service"
        }
    }

    fun identifyCharacteristic(uuid: String): String {
        return when (uuid.lowercase()) {
            "00002a19-0000-1000-8000-00805f9b34fb" -> "🔋 Battery Level"
            "00002a37-0000-1000-8000-00805f9b34fb" -> "❤️ Heart Rate Measurement"
            "00002a38-0000-1000-8000-00805f9b34fb" -> "❤️ Body Sensor Location"
            "00002a29-0000-1000-8000-00805f9b34fb" -> "🏭 Manufacturer Name"
            "00002a24-0000-1000-8000-00805f9b34fb" -> "📦 Model Number"
            "00002a26-0000-1000-8000-00805f9b34fb" -> "🧠 Firmware Revision"
            "00002a18-0000-1000-8000-00805f9b34fb" -> "🩸 Glucose Measurement"
            "00002a5f-0000-1000-8000-00805f9b34fb" -> "🫁 Pulse Oximeter"
            else -> "❓ Unknown Characteristic"
        }
    }
}

/**
 * Protocol-specific data parsing (Heart Rate, Battery, etc.)
 */
object BleDataParser {
    fun parseHeartRate(value: ByteArray): String {
        if (value.isEmpty()) return "Invalid Heart Rate"
        val flags = value[0].toInt()
        val is16Bit = flags and 0x01 != 0
        val bpm = if (is16Bit) {
            if (value.size >= 3) {
                ((value[2].toInt() and 0xFF) shl 8) or (value[1].toInt() and 0xFF)
            } else return "Invalid Heart Rate"
        } else {
            if (value.size >= 2) value[1].toInt() and 0xFF
            else return "Invalid Heart Rate"
        }
        return "❤️ Heart Rate: $bpm BPM"
    }

    fun parseText(value: ByteArray): String {
        return try {
            String(value, Charsets.UTF_8).filter { it.isLetterOrDigit() || it.isWhitespace() }
        } catch (_: Exception) {
            "Binary"
        }
    }
}

/**
 * Policy decisions for automatic interaction with peripherals.
 */
object BlePeripheralPolicy {
    fun shouldAutoSubscribe(characteristic: BluetoothGattCharacteristic): Boolean {
        val uuid = characteristic.uuid.toString().lowercase()
        val props = characteristic.properties
        val supportsNotify = props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
        val supportsIndicate = props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0

        if (!supportsNotify && !supportsIndicate) return false

        // Skip known useless spam descriptors
        val blocked = listOf("2902", "2901")
        if (blocked.any { uuid.contains(it) }) return false

        // Priority standard profiles
        val important = listOf("2a37", "2a19", "2a5b", "2a18", "2a5f", "fff", "ffe", "ffb", "fe")
        if (important.any { uuid.contains(it) }) return true

        // Allow unknown custom characteristics if they support notifications
        return uuid.startsWith("0000").not()
    }

    fun shouldAutoRead(uuid: String): Boolean {
        val lower = uuid.lowercase()
        return lower.contains("2a19") || lower.contains("2a29") || 
               lower.contains("2a24") || lower.contains("2a26")
    }
}
