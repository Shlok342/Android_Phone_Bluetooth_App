// FILE: app/src/main/java/com/example/myapplication/DeviceInsightsFragment.kt

package com.example.myapplication

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DeviceInsightsFragment : Fragment(
    R.layout.fragment_device_insights
) {

    private lateinit var recyclerView: RecyclerView

    private lateinit var adapter: DeviceInsightAdapter

    private lateinit var manager: DeviceInsightManager

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {

        super.onViewCreated(view, savedInstanceState)

        manager = DeviceInsightManager(
            requireContext().applicationContext
        )

        recyclerView =
            view.findViewById(R.id.deviceInsightsRecycler)

        adapter =
            DeviceInsightAdapter(
                manager.getAllSessions().toMutableList()
            )

        recyclerView.layoutManager =
            LinearLayoutManager(requireContext())

        recyclerView.adapter = adapter
    }

    fun refresh() {

        adapter.updateData(
            manager.getAllSessions()
        )
    }
}