package com.example.myapplication.ui

import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import com.example.myapplication.util.DeviceNameStore
import com.example.myapplication.R
import com.example.myapplication.models.BleDeviceItem
import com.google.android.material.button.MaterialButton
import com.example.myapplication.insights.DeviceInsightManager
import com.example.myapplication.util.FavoriteStore
import com.example.myapplication.models.FilterType
import android.content.Context
class DeviceAdapter(
    private val adapterContext: Context,
    private val devices: MutableList<BleDeviceItem>,
    private val deviceMap: Map<String, BluetoothDevice>,
    private val connectCallback: (BluetoothDevice) -> Unit
) : BaseAdapter() {

    override fun getCount() = displayList().size

    override fun getItem(p: Int) = displayList()[p]

    // ─── Filter ───────────────────────────────────────────────────────────────
    private var filterQuery = ""
    private var filterByMac = false
    private var activeFilterType = FilterType.NONE
    private var savedItems: List<BleDeviceItem> = emptyList()
    private var bondedAddresses: Set<String> = emptySet()
    
    private fun displayList(): List<BleDeviceItem> {
        // Snapshots the current lists to avoid ConcurrentModificationException from background scan updates
        val currentDevices = synchronized(devices) { devices.toList() }
        val currentSaved = savedItems.toList()
        val currentBonded = bondedAddresses.toSet()

        val base = when (activeFilterType) {
            FilterType.SAVED     -> currentSaved
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

    fun applyFilterType(type: FilterType, saved: List<BleDeviceItem>? = null, bonded: Set<String>? = null) {
        activeFilterType = type
        if (saved != null) savedItems = saved
        if (bonded != null) bondedAddresses = bonded
        notifyDataSetChanged()
    }

    fun clearFilter() {
        filterQuery = ""; filterByMac = false; activeFilterType = FilterType.NONE; notifyDataSetChanged()
    }

    override fun getItemId(p: Int) = p.toLong()
    private fun updateStarButton(btn: ImageButton, isFavorite: Boolean) {
        if (isFavorite) {
            btn.setImageResource(R.drawable.ic_star_filled)
            btn.setBackgroundResource(R.drawable.bg_star_btn_active)
        } else {
            btn.setImageResource(R.drawable.ic_star_outline)
            btn.setBackgroundResource(R.drawable.bg_star_btn)
        }
    }
    private fun signalLabel(rssi: Int): String = when {
        rssi >= -60 -> "Excellent"
        rssi >= -70 -> "Strong"
        rssi >= -80 -> "Good"
        else -> "Weak"
    }

    private fun signalBars(rssi: Int): String = when {
        rssi >= -60 -> "▰▰▰▰▰"
        rssi >= -70 -> "▰▰▰▰▱"
        rssi >= -80 -> "▰▰▰▱▱"
        rssi >= -90 -> "▰▰▱▱▱"
        else -> "▰▱▱▱▱"
    }
    override fun getView(p: Int, v: View?, parent: ViewGroup): View {

        val view = v ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.device_item, parent, false)

        val item = displayList()[p]
        val card = view.findViewById<View>(R.id.deviceCard)
        val indicator = view.findViewById<View>(R.id.deviceIndicator)
        indicator.background.setTint(
            when {
                item.rssi >= -60 -> "#22C55E".toColorInt()
                item.rssi >= -70 -> "#84CC16".toColorInt()
                item.rssi >= -80 -> "#F59E0B".toColorInt()
                else -> "#EF4444".toColorInt()
            }
        )
        val displayName = DeviceNameStore.get(parent.context, item.address) ?: item.name
        view.findViewById<TextView>(R.id.deviceName).text = displayName

        view.findViewById<TextView>(R.id.deviceAddress).text = item.address

        val signalText = view.findViewById<TextView>(R.id.deviceSignal)

        signalText.text =
            "📶 ${signalBars(item.rssi)} • ${signalLabel(item.rssi)}"
        signalText.setTextColor(
            when {
                item.rssi >= -60 -> "#22C55E".toColorInt()
                item.rssi >= -70 -> "#84CC16".toColorInt()
                item.rssi >= -80 -> "#F59E0B".toColorInt()
                else -> "#EF4444".toColorInt()
            }
        )

        view.findViewById<MaterialButton>(R.id.connectBtn).apply {
            isAllCaps = false

            val addressLine = item.address

            setOnClickListener {

                if (addressLine.isNotEmpty()) {

                    deviceMap[addressLine]?.let { bluetoothDevice ->

                        // Activity handles stopping scan + connection flow
                        connectCallback(bluetoothDevice)

                    } ?: run {

                        Toast.makeText(
                            context,
                            "Device data mismatch. Try refreshing.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
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
        view.setOnLongClickListener {
            val session = DeviceInsightManager.getSession(item.address)
            val msg = if (session != null)
                "${session.deviceName}\n${session.services.size} service(s) discovered"
            else
                "No session data yet for this device"
            Toast.makeText(it.context, msg, Toast.LENGTH_LONG).show()
            true
        }
        val starBtn = view.findViewById<ImageButton>(R.id.starBtn)
        val isFavorite =
            FavoriteStore.isFavorite(parent.context, item.address)

        updateStarButton(starBtn, isFavorite)

        card.setBackgroundResource(
            if (isFavorite)
                R.drawable.bg_glass_card_favourite
            else
                R.drawable.bg_glass_card
        )
        starBtn.setOnClickListener {

            val newState =
                FavoriteStore.toggle(parent.context, item.address)

            updateStarButton(starBtn, newState)

            card.setBackgroundResource(
                if (newState)
                    R.drawable.bg_glass_card_favourite
                else
                    R.drawable.bg_glass_card
            )
        }
        return view
    }
}