package com.example.myapplication
import android.bluetooth.BluetoothDevice
import android.util.Log
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanResult


class BleScanCallback(
    private val deviceList: MutableList<BleDeviceItem>,
    private val deviceMap: MutableMap<String, BluetoothDevice>,
    private val permissionChecker: () -> Boolean,
    private val onListUpdated: () -> Unit
) : ScanCallback() {

    // 1. Handles Modern Phones (Processes whole batches smoothly)
    override fun onBatchScanResults(results: MutableList<ScanResult>) {
        var listUpdated = false

        // Cache permission status once per batch to avoid checking it hundreds of times
        // CORRECT - use the injected lambda
        val hasConnectPermission = permissionChecker()

        for (result in results) {
            val isChanged = processSingleResult(result, hasConnectPermission)
            if (isChanged) listUpdated = true
        }

        // ONLY refresh the UI thread once per batch window (e.g., every 500ms)
        if (listUpdated) {
            onListUpdated()
        }
    }

    // 2. Fallback for Older Phones
    override fun onScanResult(callbackType: Int, result: ScanResult) {
        // CORRECT - use the injected lambda
        val hasConnectPermission = permissionChecker()

        val isChanged = processSingleResult(result, hasConnectPermission)
        if (isChanged) {
            onListUpdated()
        }
    }

    // 3. Shared parsing logic - COMPLETELY background thread-safe
    private fun processSingleResult(result: ScanResult, hasPermission: Boolean): Boolean {
        val device = result.device
        val address = device.address

        // Safely determine name without crashing or overloading the CPU
        val name = if (hasPermission) {
            try {
                device.name ?: "Unknown"
            } catch (_: SecurityException) {
                "No Permission"
            }
        } else {
            "No Permission"
        }

        val newEntry = BleDeviceItem(
            name = name,
            address = address,
            rssi = result.rssi
        )

        // OPTIMIZATION: Use your deviceMap to instantly check if a device exists (O(1) speed)
        // instead of slow string searching (it.contains(address))
        val isNewDevice = !deviceMap.containsKey(address)

        deviceMap[address] = device

        if (isNewDevice) {
            Log.d(
                "BLE_SCAN",
                "ADDING DEVICE: $name $address"
            )
            deviceList.add(newEntry)
            return true

        } else {

            val index = deviceList.indexOfFirst {
                it.address == address
            }

            if (index != -1) {

                val old = deviceList[index]

                if (old.rssi != result.rssi || old.name != name) {

                    deviceList[index] = newEntry
                    return true
                }
            }
        }

        return false

    }}
