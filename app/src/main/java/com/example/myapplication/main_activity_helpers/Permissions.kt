package com.example.myapplication.main_activity_helpers

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class BluetoothPermissionHandler(
    private val activity: ComponentActivity,
    private val onPermissionsGranted: () -> Unit,
    private val onShowBlocker: () -> Unit
) {

    private val requestPermissionsLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->

            val bluetoothGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissions[Manifest.permission.BLUETOOTH_SCAN] == true &&
                        permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
            } else {
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
            }

            if (bluetoothGranted) {
                executeBluetoothWorkflow()
            } else {
                Toast.makeText(activity, "Bluetooth permissions required to scan.", Toast.LENGTH_SHORT).show()
            }
        }


    fun checkPermissionsAndStartService() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }


        val missing = permissions.filter { ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED }

        if (missing.isEmpty()) {
            executeBluetoothWorkflow()
        } else {

            requestPermissionsLauncher.launch(missing.toTypedArray())
        }
    }

    // Consolidated helper to check hardware status and fire correct callbacks
    private fun executeBluetoothWorkflow() {
        if (isBluetoothHardwareEnabled()) {
            onPermissionsGranted()
        } else {
            onShowBlocker()
        }
    }

    private fun isBluetoothHardwareEnabled(): Boolean {
        val manager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter?.isEnabled == true
    }
    // Put this in a new file: ContextExt.kt

}
