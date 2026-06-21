package com.example.myapplication.insights

import android.bluetooth.BluetoothDevice
import com.example.myapplication.main_activity_helpers.BluetoothManagerWrapper
import com.example.myapplication.models.BleDeviceItem
import kotlin.collections.set

class FunctionsGet(private val btManager: BluetoothManagerWrapper) {

    private val bleDeviceMap = mutableMapOf<String, BluetoothDevice>()
    fun getSavedBleDevices(): List<BleDeviceItem> {
        return btManager.getSavedBleDevices(
            onDeviceMapped = { bleDeviceMap[it.address] = it }
        )
    }

    // FROM: getBondedBleAddresses()
    fun getBondedBleAddresses(): Set<String> {
        return btManager.getBondedAddresses { type ->
            type == BluetoothDevice.DEVICE_TYPE_LE || type == BluetoothDevice.DEVICE_TYPE_DUAL
        }
    }

    // FROM: getBondedClassicAddresses()
    fun getBondedClassicAddresses(): Set<String> {
        return btManager.getBondedAddresses { type ->
            type == BluetoothDevice.DEVICE_TYPE_CLASSIC || type == BluetoothDevice.DEVICE_TYPE_DUAL
        }
    }
   fun stopClassicScan() {
        btManager.stopClassicScan()
    }
}