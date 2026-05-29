// FILE: app/src/main/java/com/example/myapplication/DeviceInsightFormatter.kt

package com.example.myapplication

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DeviceInsightFormatter {

    private val timeFormat by lazy { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    fun format(session: DeviceInsightSession): String {

        val builder = StringBuilder()

        builder.appendLine(
            "${formatTime(session.connectedAt)} — Device \"${session.deviceName}\" connected."
        )

        builder.appendLine()

        builder.appendLine("━━━━━━━━━━━━━━━━━━")
        builder.appendLine("DEVICE INFORMATION")
        builder.appendLine("━━━━━━━━━━━━━━━━━━")

        builder.appendLine("Name: ${session.deviceName}")
        builder.appendLine("MAC: ${session.macAddress}")
        builder.appendLine("Transport: ${session.transportType}")

        session.rssi?.let {

            builder.appendLine("RSSI: $it dBm")

            builder.appendLine(
                "Connection Quality: ${getSignalQuality(it)}"
            )
        }

        session.mtu?.let {
            builder.appendLine("MTU: $it")
        }

        if (session.isAudioDevice) {

            builder.appendLine()

            builder.appendLine("━━━━━━━━━━━━━━━━━━")
            builder.appendLine("AUDIO DETAILS")
            builder.appendLine("━━━━━━━━━━━━━━━━━━")

            builder.appendLine(
                "Audio Playing: ${
                    if (session.isAudioPlaying) "Yes"
                    else "No"
                }"
            )

            builder.appendLine()

            session.audioProfiles.forEach { (profile, state) ->

                builder.appendLine(
                    "$profile: ${formatAudioState(state)}"
                )
            }
        }

        builder.appendLine()

        builder.appendLine("━━━━━━━━━━━━━━━━━━")
        builder.appendLine("GATT SERVICES")
        builder.appendLine("━━━━━━━━━━━━━━━━━━")

        session.services.forEach { service ->

            builder.appendLine()

            builder.appendLine(
                "Service: ${service.serviceName}"
            )

            builder.appendLine(
                "UUID: ${service.serviceUuid}"
            )

            if (service.characteristics.isNotEmpty()) {

                builder.appendLine("Characteristics:")

                service.characteristics.forEach { characteristic ->

                    builder.appendLine(
                        "• ${characteristic.characteristicName}"
                    )

                    builder.appendLine(
                        "  UUID: ${characteristic.uuid}"
                    )

                    builder.appendLine(
                        "  Properties: ${
                            characteristic.properties.joinToString(" | ")
                        }"
                    )

                    builder.appendLine()
                }
            }
        }

        if (session.events.isNotEmpty()) {

            builder.appendLine()

            builder.appendLine("━━━━━━━━━━━━━━━━━━")
            builder.appendLine("EVENT TIMELINE")
            builder.appendLine("━━━━━━━━━━━━━━━━━━")

            session.events.forEach { event ->

                builder.appendLine(
                    "${formatTime(event.timestamp)} — ${event.message}"
                )
            }
        }

        if (session.disconnectedAt != null) {

            builder.appendLine()

            builder.appendLine("━━━━━━━━━━━━━━━━━━")
            builder.appendLine("DISCONNECTION")
            builder.appendLine("━━━━━━━━━━━━━━━━━━")

            builder.appendLine(
                "${formatTime(session.disconnectedAt!!)} — Device \"${session.deviceName}\" disconnected."
            )

            session.disconnectReason?.let {

                builder.appendLine("Reason: $it")
            }

            val durationSeconds =
                (session.disconnectedAt!! - session.connectedAt) / 1000

            builder.appendLine(
                "Duration: ${formatDuration(durationSeconds)}"
            )
        }

        return builder.toString()
    }

    private fun formatAudioState(
        state: AudioProfileState
    ): String {

        return when(state) {

            AudioProfileState.IDLE ->
                "Idle"

            AudioProfileState.CONNECTING ->
                "Connecting"

            AudioProfileState.CONNECTED ->
                "Connected"

            AudioProfileState.PLAYING ->
                "Playing Audio"

            AudioProfileState.DISCONNECTED ->
                "Disconnected"

            is AudioProfileState.RECONNECTING ->
                "Reconnecting (Attempt ${state.attempt})"

            is AudioProfileState.FAILED ->
                "Failed (${state.reason})"
        }
    }

    private fun formatTime(
        timestamp: Long
    ): String {

        return timeFormat.format(Date(timestamp))
    }

    private fun getSignalQuality(
        rssi: Int
    ): String {

        return when {

            rssi >= -60 ->
                "Excellent"

            rssi >= -75 ->
                "Good"

            rssi >= -90 ->
                "Weak"

            else ->
                "Very Weak"
        }
    }

    private fun formatDuration(
        seconds: Long
    ): String {

        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainingSeconds = seconds % 60

        return buildString {

            if (hours > 0) {
                append("${hours}h ")
            }

            if (minutes > 0) {
                append("${minutes}m ")
            }

            append("${remainingSeconds}s")
        }
    }
}