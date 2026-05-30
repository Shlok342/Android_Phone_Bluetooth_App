package com.example.myapplication

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Context.BLUETOOTH_SERVICE
import android.content.Context.LOCATION_SERVICE
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast


class BleScanManager(
    private val context: Context,
    private val permissionChecker: () -> Boolean,
    private val onDeviceFound: (BleDeviceItem, BluetoothDevice) -> Unit,
    private val onClearDevices: () -> Unit,
    private val onScanStopped: () -> Unit
) {
    var isScanning = false
        private set
    var lastScanStartTime = 0L
    private val scancooldownms= 10000L
    private val scandurationms= 15_000L



    private val bluetoothAdapter by lazy {
        (context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val scanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }
    private val scanHandler = Handler(Looper.getMainLooper())
    private val scanTimeoutRunnable = Runnable {
        Log.d("BLE_SCAN", "AUTO STOPPING SCAN")
        stop()
    }

    private val scanCallback = BleScanCallback(
        onDeviceFound = { item, device -> onDeviceFound(item, device) },
        permissionChecker = permissionChecker
    )

    fun start() {
        DeviceInsightManager.onAppEvent("BLE: Scan Started")
        val now = System.currentTimeMillis()

        // Prevent Android BLE throttling
        if (now - lastScanStartTime < scancooldownms) {
            Log.d("BLE_SCAN", "Scan blocked by cooldown")
            return
        }

        // Prevent duplicate scans
        if (isScanning) {
            Log.d("BLE_SCAN", "Already scanning")
            return
        }

        val locationManager =
            context.getSystemService(LOCATION_SERVICE) as LocationManager

        val isLocationEnabled =
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isLocationEnabled) {
            Toast.makeText(
                context,
                "Turn on Location for BLE scanning",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val bluetoothManager =
            context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager

        if (!bluetoothManager.adapter.isEnabled) {
            Toast.makeText(context, "Enable Bluetooth first", Toast.LENGTH_SHORT).show()
            return
        }

        // Stop any ongoing scans before starting a new one
        stop()
        onClearDevices()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {

            Log.d("BLE_SCAN", "STARTING SCAN SESSION")

            scanner.startScan(null, settings, scanCallback)

            isScanning = true
            lastScanStartTime = now

            Toast.makeText(context, "Scanning BLE...", Toast.LENGTH_SHORT).show()

            // Auto-stop scan after 15 seconds
            scanHandler.removeCallbacks(scanTimeoutRunnable)

            scanHandler.postDelayed(
                scanTimeoutRunnable,
                scandurationms
            )

        } catch (e: SecurityException) {
            e.printStackTrace()
            Toast.makeText(context, "BLE scan permission denied", Toast.LENGTH_LONG).show()
        }
    }
    fun stop() {

        if (!isScanning) return

        try {

            scanner.stopScan(scanCallback)
            SystemTimeline.log("⏹ BLE scan stopped")
            scanHandler.removeCallbacks(scanTimeoutRunnable)
            isScanning = false
            onScanStopped()

            Log.d("BLE_SCAN", "SCAN STOPPED")

        } catch (_: SecurityException) {

        }
    }
}
