package com.example.myapplication.util

import android.bluetooth.BluetoothDevice
import android.os.Build

class Filtering (private val permissionChecker: () -> Boolean){
    fun isProbablyClassicCapable(device: BluetoothDevice): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !permissionChecker()) {
                false
            } else {
                when (device.type) {
                    // Strictly Classic devices belong here (e.g., old car audio, legacy OBD)
                    BluetoothDevice.DEVICE_TYPE_CLASSIC -> true

                    // Banish Dual-mode to the BLE tab. Consumers get a much better,
                    // modern connection experience there without annoying PIN prompts.
                    BluetoothDevice.DEVICE_TYPE_DUAL -> false

                    // Definitely LE - exclude entirely
                    BluetoothDevice.DEVICE_TYPE_LE -> false

                    // UNKNOWN: Android is still fetching the profile during a classic scan.
                    // Real classic devices will have a Major Device Class like COMPUTER, PHONE,
                    // AUDIO, or TOY. Dual-mode smart locks/tools flag as UNcategorized/MISC.
                    BluetoothDevice.DEVICE_TYPE_UNKNOWN -> {
                        val hasNameAndClass = device.bluetoothClass != null && !device.name.isNullOrEmpty()
                        if (hasNameAndClass) {
                            // Check the major class. If it's UNCATEGORIZED (0x1F00) or MISC (0x0000),
                            // it's highly likely a dual-mode device masking as UNKNOWN. Filter it out.
                            val majorClass = device.bluetoothClass?.majorDeviceClass
                            majorClass != 0x1F00 && majorClass != 0x0000
                        } else {
                            false
                        }
                    }

                    else -> false
                }
            }
        } catch (_: SecurityException) {
            false
        }
    }
}
