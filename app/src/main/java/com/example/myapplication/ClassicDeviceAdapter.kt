package com.example.myapplication

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

// ─── Simple Adapter ──────────────────────────────────────────────────────────

class ClassicDeviceAdapter(
    private val devices: List<ClassicDeviceItem>,
    private val deviceMap: Map<String, BluetoothDevice>,
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
            if (item.type == BluetoothDevice.DEVICE_TYPE_DUAL) "Dual (Classic+BLE)" else "Classic"
        view.findViewById<Button>(R.id.connectBtn).apply {
            isEnabled = false       // ADD THIS
            alpha = 0.4f            // ADD THIS
            isAllCaps = false
            setOnClickListener {
                deviceMap[item.address]?.let { connectCallback(it) }
                    ?: Toast.makeText(context, "Device not found, try rescanning", Toast.LENGTH_SHORT).show()
            }
        }
        return view
    }
}