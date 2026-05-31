package com.example.myapplication.util

import android.bluetooth.BluetoothGattCharacteristic

object CharacteristicPropertyParser {

    fun parse(properties: Int): List<String> {

        val parsed = mutableListOf<String>()

        if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
            parsed.add("READ")
        }

        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
            parsed.add("WRITE")
        }

        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
            parsed.add("WRITE_NO_RESPONSE")
        }

        if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
            parsed.add("NOTIFY")
        }

        if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
            parsed.add("INDICATE")
        }

        return parsed
    }
}