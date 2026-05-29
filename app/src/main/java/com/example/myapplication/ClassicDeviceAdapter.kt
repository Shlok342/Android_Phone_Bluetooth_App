package com.example.myapplication

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.app.AlertDialog
import android.widget.EditText
import android.widget.ImageButton
import androidx.core.graphics.toColorInt
import com.google.android.material.button.MaterialButton

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

        val displayName = DeviceNameStore.get(parent.context, item.address) ?: item.name
        view.findViewById<TextView>(R.id.deviceName).text = displayName
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
        view.findViewById<ImageButton>(R.id.editNameBtn).setOnClickListener {

            val ctx = parent.context

            val dialogView = LayoutInflater.from(ctx)
                .inflate(R.layout.dialog_edit_device_name, null)

            val input = dialogView.findViewById<EditText>(R.id.editNameInput)
            val clearAllBtn =
                dialogView.findViewById<MaterialButton>(R.id.btnClearAllCustomNames)
            input.setText(
                DeviceNameStore.get(ctx, item.address)
                    ?: item.name
            )

            input.selectAll()

            val alert = AlertDialog.Builder(ctx)

                .setView(dialogView)

                .setPositiveButton("Save") { _, _ ->

                    val name = input.text.toString().trim()

                    if (name.isEmpty()) {
                        DeviceNameStore.remove(ctx, item.address)
                    } else {
                        DeviceNameStore.save(ctx, item.address, name)
                    }

                    notifyDataSetChanged()
                }

                .setNegativeButton("Cancel", null)

                .show()

            alert.getButton(AlertDialog.BUTTON_POSITIVE).isAllCaps = false
            alert.getButton(AlertDialog.BUTTON_NEGATIVE).isAllCaps = false

            alert.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor("#E8E9F0".toColorInt())

            alert.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor("#AEB4C2".toColorInt())

            clearAllBtn.setOnClickListener {

                DeviceNameStore.clearAll(ctx)

                notifyDataSetChanged()

                Toast.makeText(
                    ctx,
                    "All custom names cleared",
                    Toast.LENGTH_SHORT
                ).show()

                alert.dismiss()
            }
        }
        return view
    }
}