package com.example.myapplication.main_activity_helpers

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.myapplication.models.BleDeviceItem

class BluetoothManagerWrapper(
    private val context: Context,
    private val scope: CoroutineScope,
    private val checkConnectPermission: ()-> Boolean,
    private val checkScanPermission: () -> Boolean// Injected permission helper
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    // Add ': BluetoothAdapter?' to explicitly mark it as nullable
    val adapter: BluetoothAdapter? = bluetoothManager.adapter


    // --- Service Control ---
    fun startService(intent: Intent) {
        context.startForegroundService(intent)
    }

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    // --- Classic Scan Operations ---
    @SuppressLint("MissingPermission")
    fun startClassicScan(
        onPreScan: () -> Unit,
        onDeviceFound: (BluetoothDevice) -> Unit,
        onScanStarted: () -> Unit
    ) {
        if (adapter == null || !adapter.isEnabled) {
            Toast.makeText(context, "Enable Bluetooth first", Toast.LENGTH_SHORT).show()
            return
        }

        onPreScan() // Clear UI lists via callback

        // 1. Process Bonded Devices
        if (checkConnectPermission()) {
            try {
                adapter.bondedDevices?.forEach { device ->
                    val type = device.type
                    if (type == BluetoothDevice.DEVICE_TYPE_CLASSIC || type == BluetoothDevice.DEVICE_TYPE_DUAL) {
                        onDeviceFound(device)
                    }
                }
            } catch (_: SecurityException) {}
        }

        // 2. Start Discovery
        scope.launch {
            if (checkScanPermission()) {
                if (adapter.isDiscovering) {
                    try { adapter.cancelDiscovery() } catch (_: SecurityException) {}
                    delay(500)
                }
                try {
                    adapter.startDiscovery()
                    onScanStarted()
                } catch (_: SecurityException) {}
            }
        }
    }

    fun stopClassicScan() {
        try { adapter?.cancelDiscovery() } catch (_: SecurityException) {}
    }

    // --- Device Queries (UI Data Helpers) ---
    @SuppressLint("MissingPermission")
    fun getSavedBleDevices(onDeviceMapped: (BluetoothDevice) -> Unit): List<BleDeviceItem> {
        if (!checkConnectPermission()) return emptyList()
        return try {
            adapter?.bondedDevices
                ?.filter { it.type == BluetoothDevice.DEVICE_TYPE_LE || it.type == BluetoothDevice.DEVICE_TYPE_DUAL }
                ?.onEach { device -> onDeviceMapped(device) } // 👈 Pass the device parameter here
                ?.map {
                    BleDeviceItem(
                        name = try { it.name ?: "Unknown" } catch (_: SecurityException) { "Unknown" },
                        address = it.address,
                        rssi = 0
                    )
                } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }


    @SuppressLint("MissingPermission")
    fun getBondedAddresses(deviceTypeFilter: (Int) -> Boolean): Set<String> {
        if (checkConnectPermission()) return emptySet()
        return try {
            adapter?.bondedDevices
                ?.filter { deviceTypeFilter(it.type) }
                ?.map { it.address }
                ?.toSet() ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }
}