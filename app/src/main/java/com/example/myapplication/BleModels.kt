package com.example.myapplication

import android.content.Context

data class BleDeviceItem(
    val name: String,
    val address: String,
    val rssi: Int
)

data class ClassicDeviceItem(
    val name: String,
    val address: String,
    val rssi: Int = 0,
    val type: Int = 0
)

enum class ActiveTab {
    BLE, CLASSIC
}

fun Int.dp(context: Context): Int {
    return (this * context.resources.displayMetrics.density).toInt()
}
