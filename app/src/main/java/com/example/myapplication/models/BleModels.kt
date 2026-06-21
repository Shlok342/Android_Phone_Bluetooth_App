package com.example.myapplication.models

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

enum class FilterType {
    NONE, SAVED, FAVORITES, NEARBY
}

data class BleConnectionInfo(
    val state: com.example.myapplication.ble.BleState,
    val address: String,
    val failureMessage: String? = null,
    val batteryLevel: Int? = null

)

fun Int.dp(context: Context): Int {
    return (this * context.resources.displayMetrics.density).toInt()
}
