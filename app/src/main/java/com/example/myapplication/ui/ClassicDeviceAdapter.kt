package com.example.myapplication.ui

import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import com.example.myapplication.util.DeviceNameStore
import com.example.myapplication.R
import com.example.myapplication.models.ClassicDeviceItem
import com.google.android.material.button.MaterialButton
import com.example.myapplication.util.FavoriteStore
import com.example.myapplication.models.FilterType
import android.content.Context
class ClassicDeviceAdapter(
    private val adapterContext: Context,
    private val devices: List<ClassicDeviceItem>,
    private val deviceMap: Map<String, BluetoothDevice>,
    private val connectCallback: (BluetoothDevice) -> Unit
) : BaseAdapter() {
    override fun getCount() = displayList().size
    override fun getItem(p: Int) = displayList()[p]
    override fun getItemId(p: Int) = p.toLong()

    // ─── Filter ───────────────────────────────────────────────────────────────
    private var filterQuery = ""
    private var filterByMac = false
    private var activeFilterType = FilterType.NONE
    private var bondedAddresses: Set<String> = emptySet()
    
    private fun displayList(): List<ClassicDeviceItem> {
        val currentDevices = synchronized(devices) { devices.toList() }
        val currentBonded = bondedAddresses.toSet()

        val base = when (activeFilterType) {
            FilterType.SAVED     -> currentDevices.filter { it.address in currentBonded }
            FilterType.FAVORITES -> currentDevices.filter { FavoriteStore.isFavorite(adapterContext, it.address) }
            FilterType.NEARBY    -> currentDevices.filter { it.address !in currentBonded }
            FilterType.NONE      -> currentDevices
        }
        if (filterQuery.isEmpty()) return base
        return if (filterByMac) base.filter { it.address.contains(filterQuery, ignoreCase = true) }
        else base.filter {
            (DeviceNameStore.get(adapterContext, it.address) ?: it.name).contains(filterQuery, ignoreCase = true)
        }
    }

    fun applyFilter(query: String, byMac: Boolean) {
        filterQuery = query; filterByMac = byMac; notifyDataSetChanged()
    }

    fun applyFilterType(type: FilterType, bonded: Set<String>? = null) {
        activeFilterType = type
        if (bonded != null) bondedAddresses = bonded
        notifyDataSetChanged()
    }

    fun clearFilter() {
        filterQuery = ""; filterByMac = false; activeFilterType = FilterType.NONE; notifyDataSetChanged()
    }
    private fun updateStarButton(btn: ImageButton, isFavorite: Boolean) {
        if (isFavorite) {
            btn.setImageResource(R.drawable.ic_star_filled)
            btn.setBackgroundResource(R.drawable.bg_star_btn_active)
        } else {
            btn.setImageResource(R.drawable.ic_star_outline)
            btn.setBackgroundResource(R.drawable.bg_star_btn)
        }
    }
    override fun getView(p: Int, v: View?, parent: ViewGroup): View {
        val view = v ?: LayoutInflater.from(parent.context).inflate(R.layout.device_item, parent, false)
        val item = displayList()[p]
        val displayName = DeviceNameStore.get(parent.context, item.address) ?: item.name
        view.findViewById<TextView>(R.id.deviceName).text = displayName
        view.findViewById<TextView>(R.id.deviceAddress).text = item.address
        view.findViewById<TextView>(R.id.deviceSignal).text =
            if (item.type == BluetoothDevice.DEVICE_TYPE_DUAL) "Dual (Classic+BLE)" else "Classic"
        view.findViewById<Button>(R.id.connectBtn).apply {
                       // ADD THIS
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
        val starBtn = view.findViewById<ImageButton>(R.id.starBtn)
        updateStarButton(starBtn, FavoriteStore.isFavorite(parent.context, item.address))
        starBtn.setOnClickListener {
            val newState = FavoriteStore.toggle(parent.context, item.address)
            updateStarButton(starBtn, newState)
        }
        return view
    }
}