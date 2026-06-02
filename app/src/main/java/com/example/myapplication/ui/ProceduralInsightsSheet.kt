package com.example.myapplication.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.myapplication.R
import com.example.myapplication.insights.DeviceEvent
import com.example.myapplication.insights.DeviceInsightAdapter
import com.example.myapplication.insights.DeviceInsightManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProceduralInsightsSheet(context: Context) : BottomSheetDialog(context) {
    var deviceInsightAdapter: DeviceInsightAdapter? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = LayoutInflater.from(context).inflate(R.layout.fragment_device_insights, null)
        setContentView(view)

        val tabLayout = view.findViewById<TabLayout>(R.id.insightsTabLayout)
        val viewPager = view.findViewById<ViewPager2>(R.id.insightsViewPager)
        var deviceInsightAdapter: DeviceInsightAdapter? = null
        viewPager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val rv = RecyclerView(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(-1, -1)
                    layoutManager = LinearLayoutManager(parent.context)
                }
                return object : RecyclerView.ViewHolder(rv) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val rv = holder.itemView as RecyclerView
                if (position == 0) {
                    rv.adapter = AppEventsAdapter(DeviceInsightManager.getAppEvents())
                } else {

                    deviceInsightAdapter = DeviceInsightAdapter(
                        DeviceInsightManager.getAllSessions().toMutableList()
                    )
                    rv.adapter = deviceInsightAdapter
                }
            }

            override fun getItemCount(): Int = 2
        }



        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = if (position == 0) "App Status" else "Device Metrics"
        }.attach()
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position == 1) {
                    deviceInsightAdapter?.updateData(DeviceInsightManager.getAllSessions())
                }
            }
        })
    }

    class AppEventsAdapter(private val events: List<DeviceEvent>) : RecyclerView.Adapter<AppEventsAdapter.ViewHolder>() {
        private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val text: TextView = view.findViewById(R.id.deviceInsightText)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device_insight, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val event = events[position]

            val context = holder.text.context

            holder.text.text = context.getString(
                R.string.format_of_message,
                timeFormat.format(Date(event.timestamp)),
                event.message
            )

        }

        override fun getItemCount(): Int = events.size
    }
}