package com.example.myapplication.ui.adapters

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.example.myapplication.R
import com.example.myapplication.models.ClassicDeviceItem
import com.example.myapplication.models.FilterType
import com.example.myapplication.util.DeviceNameStore
import com.example.myapplication.util.FavoriteStore
import com.google.android.material.button.MaterialButton

class ClassicDeviceAdapter(
    private val adapterContext: Context,
    private val devices: List<ClassicDeviceItem>,
    private val deviceMap: Map<String, BluetoothDevice>,
    private val connectCallback: (BluetoothDevice) -> Unit,
    private val forgetCallback: (BluetoothDevice) -> Unit
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
    private fun classicTypeLabel(type: Int): String = when (type) {
        BluetoothDevice.DEVICE_TYPE_DUAL -> "🔄 Dual Mode"
        BluetoothDevice.DEVICE_TYPE_CLASSIC -> "📡 Classic"
        else -> "📶 Bluetooth"
    }

    override fun getView(p: Int, v: View?, parent: ViewGroup): View {
        val view = v ?: LayoutInflater.from(parent.context).inflate(R.layout.device_item, parent, false)
        val item = displayList()[p]
        val card = view.findViewById<View>(R.id.deviceCard)
        val indicator = view.findViewById<View>(R.id.deviceIndicator)
        val displayName = DeviceNameStore.get(parent.context, item.address) ?: item.name
        view.findViewById<TextView>(R.id.deviceName).text = displayName
        view.findViewById<TextView>(R.id.deviceAddress).text = item.address
        val signalText = view.findViewById<TextView>(R.id.deviceSignal)

        signalText.text = classicTypeLabel(item.type)
        signalText.setTextColor(
            when (item.type) {
                BluetoothDevice.DEVICE_TYPE_DUAL -> "#22C55E".toColorInt()
                BluetoothDevice.DEVICE_TYPE_CLASSIC -> "#60A5FA".toColorInt()
                else -> "#A78BFA".toColorInt()
            }
        )
        indicator.background.setTint(
            when (item.type) {
                BluetoothDevice.DEVICE_TYPE_DUAL -> "#22C55E".toColorInt()
                BluetoothDevice.DEVICE_TYPE_CLASSIC -> "#60A5FA".toColorInt()
                else -> "#A78BFA".toColorInt()
            }
        )
        val forgetBtn = view.findViewById<ImageButton>(R.id.forgetBtn)
        val device = deviceMap[item.address]

        forgetBtn.visibility =
            if (
                ContextCompat.checkSelfPermission(
                    parent.context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
                &&
                device?.bondState == BluetoothDevice.BOND_BONDED
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }


        forgetBtn.setOnClickListener {



            if (device == null) {
                Toast.makeText(
                    adapterContext,                    "Device not found",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(adapterContext)
                .setTitle("Forget Device")
                .setMessage("Forget $displayName?")
                .setPositiveButton("Forget") { _, _ ->
                    forgetCallback(device)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        view.findViewById<Button>(R.id.connectBtn).apply {

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
        val isFavorite =
            FavoriteStore.isFavorite(parent.context, item.address)

        card.setBackgroundResource(
            if (isFavorite)
                R.drawable.bg_glass_card_favourite
            else
                R.drawable.bg_glass_card
        )
        updateStarButton(starBtn, isFavorite)
        starBtn.setOnClickListener {
            val newState = FavoriteStore.toggle(parent.context, item.address)
            card.setBackgroundResource(
                if (newState)
                    R.drawable.bg_glass_card_favourite
                else
                    R.drawable.bg_glass_card
            )
            updateStarButton(starBtn, newState)
        }
        return view
    }
}