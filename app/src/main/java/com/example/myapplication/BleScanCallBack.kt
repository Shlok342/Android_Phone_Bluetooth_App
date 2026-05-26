package com.example.myapplication
import android.bluetooth.BluetoothDevice
import android.util.Log
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanResult


class BleScanCallback(

    private val onDeviceFound: (BleDeviceItem, BluetoothDevice) -> Unit,
    private val permissionChecker: () -> Boolean

) : ScanCallback() {

    override fun onBatchScanResults(results: MutableList<ScanResult>) {

        val hasConnectPermission = permissionChecker()

        for (result in results) {
            processSingleResult(result, hasConnectPermission)
        }
    }

    override fun onScanResult(callbackType: Int, result: ScanResult) {
        Log.d("BLE_SCAN", "RAW RESULT RECEIVED")

        val hasConnectPermission = permissionChecker()

        processSingleResult(result, hasConnectPermission)
    }
    override fun onScanFailed(errorCode: Int) {
        Log.e("BLE_SCAN", "Scan failed with error: $errorCode")
    }
    private fun processSingleResult(
        result: ScanResult,
        hasPermission: Boolean
    ) {

        val device = result.device

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
            address = device.address,
            rssi = result.rssi
        )

        Log.d(
         "BLE_SCAN",
            "DEVICE FOUND: $name (${device.address}")

        onDeviceFound(newEntry, device)
    }
}
