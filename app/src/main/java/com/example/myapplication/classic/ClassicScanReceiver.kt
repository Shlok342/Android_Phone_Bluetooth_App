package com.example.myapplication.classic

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresPermission
import com.example.myapplication.models.ClassicDeviceItem
import com.example.myapplication.R

class ClassicScanReceiver(
    private val classicDeviceList: MutableList<ClassicDeviceItem>,
    private val classicDeviceMap: MutableMap<String, BluetoothDevice>,
    private val permissionChecker: () -> Boolean,
    private val onDeviceListChanged: () -> Unit,
    private val onStatusUpdate: (String) -> Unit) : BroadcastReceiver(){
    private fun isProbablyClassicCapable(device: BluetoothDevice): Boolean {

        return try {

            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !permissionChecker()

            ) {
                false
            } else {

                when (device.type) {

                    BluetoothDevice.DEVICE_TYPE_CLASSIC,
                    BluetoothDevice.DEVICE_TYPE_DUAL -> true

                    BluetoothDevice.DEVICE_TYPE_LE -> {

                        // Some phones temporarily report LE before Android resolves properly.
                        // If bluetoothClass exists, keep it.
                        device.bluetoothClass != null
                    }

                    BluetoothDevice.DEVICE_TYPE_UNKNOWN -> {

                        // UNKNOWN during discovery is extremely common on Android.
                        // If bluetoothClass exists, it is usually a real Classic-capable device.
                        device.bluetoothClass != null
                    }

                    else -> false
                }
            }

        } catch (_: SecurityException) {

            false
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            BluetoothDevice.ACTION_FOUND -> {
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

                device ?: return
                val type = try {
                    if (!permissionChecker()) BluetoothDevice.DEVICE_TYPE_UNKNOWN else device.type
                } catch (_: SecurityException) { BluetoothDevice.DEVICE_TYPE_UNKNOWN }

                if (!isProbablyClassicCapable(device)) {
                    return
                }

                val address = device.address
                val name = try {
                    if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        !permissionChecker()
                    ) {
                        "Unknown"
                    } else {
                        device.name ?: "Unknown"
                    }
                } catch (_: SecurityException) {
                    "Unknown"
                }

                Log.d(
                    "CLASSIC_SCAN",
                    "Found: ${device.name} | ${device.address} | type=${device.type}"
                )


                val existingIndex =
                        classicDeviceList.indexOfFirst { it.address == address }

                if (existingIndex != -1) {

                    val existing = classicDeviceList[existingIndex]

                    val improvedName =
                        existing.name == "Unknown" && name != "Unknown"

                    val improvedType =
                        existing.type == BluetoothDevice.DEVICE_TYPE_UNKNOWN &&
                                type != BluetoothDevice.DEVICE_TYPE_UNKNOWN

                    if (improvedName || improvedType) {

                        classicDeviceList[existingIndex] =
                            existing.copy(
                                    name = if (improvedName) name else existing.name,
                                    type = if (improvedType) type else existing.type
                                )

                        classicDeviceMap[address] = device

                        onDeviceListChanged()
                        }

                } else {

                    classicDeviceMap[address] = device

                    classicDeviceList.add(
                        ClassicDeviceItem(name, address, type)
                        )


                            onDeviceListChanged()

                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {

                    Toast.makeText(context, "Classic scan done", Toast.LENGTH_SHORT).show()
                    Log.d(
                        "CLASSIC_SCAN",
                        "Final classic device count = ${classicDeviceList.size}"
                    )


                }
                BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

                    val variant = intent.getIntExtra(
                        BluetoothDevice.EXTRA_PAIRING_VARIANT,
                        BluetoothDevice.ERROR
                    )
                    val pairingType = when (variant) {
                        BluetoothDevice.PAIRING_VARIANT_PIN          -> "Enter PIN"
                        BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION -> "Confirm passkey"
                        6 /* PAIRING_VARIANT_CONSENT */              -> "Confirm pairing"
                        else                                         -> "Pairing"
                    }

                        onStatusUpdate(context.getString(R.string.with, pairingType, device?.address ?: "device"))


                }

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val bondState = intent.getIntExtra(
                        BluetoothDevice.EXTRA_BOND_STATE,
                        BluetoothDevice.BOND_NONE
                    )
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

                    val address = device?.address ?: return

                        when (bondState) {
                            BluetoothDevice.BOND_BONDED -> {
                                // Refresh list entry — it might now show a real name
                                val idx = classicDeviceList.indexOfFirst { it.address == address }
                                if (idx != -1) {
                                    val name = try { device.name ?: "Unknown" } catch (_: SecurityException) { "Unknown" }
                                    classicDeviceList[idx] = classicDeviceList[idx].copy(name = name)
                                    onDeviceListChanged()
                                }
                                onStatusUpdate(context.getString(R.string.paired_connecting))
                            }
                            BluetoothDevice.BOND_NONE ->
                                onStatusUpdate(context.getString(R.string.pairing_failed))
                        }

                }
            }
        }

}