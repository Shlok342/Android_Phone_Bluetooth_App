package com.example.myapplication
import android.bluetooth.BluetoothDevice
import android.util.Log
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult


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
        val reason = when (errorCode) {
            SCAN_FAILED_ALREADY_STARTED ->
                "SCAN_FAILED_ALREADY_STARTED"

            SCAN_FAILED_APPLICATION_REGISTRATION_FAILED ->
                "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED"

            SCAN_FAILED_FEATURE_UNSUPPORTED ->
                "SCAN_FAILED_FEATURE_UNSUPPORTED"

            SCAN_FAILED_INTERNAL_ERROR ->
                "SCAN_FAILED_INTERNAL_ERROR"

            SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES ->
                "SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES"

            SCAN_FAILED_SCANNING_TOO_FREQUENTLY ->
                "SCAN_FAILED_SCANNING_TOO_FREQUENTLY"

            else ->
                "UNKNOWN_ERROR"
        }

        Log.e("BLE_SCAN", "Scan failed: $reason")
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
            "DEVICE FOUND: $name ${device.address}")

        onDeviceFound(newEntry, device)
    }
}
