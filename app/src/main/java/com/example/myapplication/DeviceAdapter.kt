package com.example.myapplication

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton

class DeviceAdapter(
    private val devices: List<BleDeviceItem>,
    private val deviceMap: Map<String, BluetoothDevice>,
    private val onStopScanRequested: () -> Unit, // 🌟 Pass a function pointer to stop the scan
    private val connectCallback: (BluetoothDevice) -> Unit
) : BaseAdapter() {

    override fun getCount() = devices.size
    override fun getItem(p: Int) = devices[p]
    override fun getItemId(p: Int) = p.toLong()

    override fun getView(p: Int, v: View?, parent: ViewGroup): View {
        val view = v ?: LayoutInflater.from(parent.context).inflate(R.layout.device_item, parent, false)
        val item = devices[p]

        view.findViewById<TextView>(R.id.deviceName).text = item.name
        view.findViewById<TextView>(R.id.deviceAddress).text = item.address
        view.findViewById<TextView>(R.id.deviceSignal).text =
            view.context.getString(R.string.rssi, item.rssi)

        view.findViewById< MaterialButton>(R.id.connectBtn).apply {

            isAllCaps = false

            val addressLine = item.address

            setOnClickListener {
                // STEP 1: Instantly stop the scanner to preserve main thread performance
                onStopScanRequested()

                // STEP 2: Hand off the device directly to your activity to run connectToDevice()
                if (addressLine.isNotEmpty()) {
                    deviceMap[addressLine]?.let { bluetoothDevice ->
                        connectCallback(bluetoothDevice)
                    } ?: run {
                        Toast.makeText(context, "Device data mismatch. Try refreshing.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }


        return view
    }
}