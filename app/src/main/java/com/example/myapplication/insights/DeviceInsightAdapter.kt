package com.example.myapplication.insights

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R

class DeviceInsightAdapter(
    private val items: MutableList<DeviceInsightSession>
) : RecyclerView.Adapter<DeviceInsightAdapter.ViewHolder>() {

    class ViewHolder(view: View)
        : RecyclerView.ViewHolder(view) {

        val content: TextView =
            view.findViewById(R.id.deviceInsightText)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(
                R.layout.item_device_insight,
                parent,
                false
            )

        return ViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {

        holder.content.text =
            DeviceInsightFormatter.format(items[position])
    }

    override fun getItemCount(): Int {
        return items.size
    }

    fun updateData(
        newItems: List<DeviceInsightSession>
    ) {

        items.clear()
        items.addAll(newItems)

        notifyDataSetChanged()
    }
}