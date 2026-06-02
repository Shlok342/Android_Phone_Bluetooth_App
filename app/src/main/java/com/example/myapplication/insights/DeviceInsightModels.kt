

package com.example.myapplication.insights

import com.example.myapplication.classic.AudioProfileState

data class DeviceInsightSession(
    val deviceName: String,
    val macAddress: String,
    val transportType: String,
    val connectedAt: Long,

    var disconnectedAt: Long? = null,
    var disconnectReason: String? = null,

    var mtu: Int? = null,
    var rssi: Int? = null,

    var isAudioDevice: Boolean = false,
    var isAudioPlaying: Boolean = false,

    val audioProfiles:
    MutableMap<String, AudioProfileState>
    = mutableMapOf(),
    val services: MutableList<ServiceInsight> = mutableListOf(),
    val events: MutableList<DeviceEvent> = mutableListOf()
)

data class ServiceInsight(
    val serviceName: String,
    val serviceUuid: String,
    val characteristics: MutableList<CharacteristicInsight> = mutableListOf()
)

data class CharacteristicInsight(
    val characteristicName: String,
    val uuid: String,
    val properties: List<String>
)

data class DeviceEvent(
    val timestamp: Long,
    val message: String
)