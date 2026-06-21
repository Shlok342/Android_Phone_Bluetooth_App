package com.example.myapplication.main_activity_helpers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.ble.BluetoothService
import com.example.myapplication.classic.AudioProfileState
import com.example.myapplication.classic.ClassicBluetoothService
import com.example.myapplication.classic.ClassicConnectionManager
import com.example.myapplication.classic.file_transfer.FileTransferState
import com.example.myapplication.classic.file_transfer.TransferDirection
import com.example.myapplication.classic.helpers.BatteryErrorProfile
import com.example.myapplication.classic.helpers.ConnectionSecurity
import com.example.myapplication.classic.messages.ClassicMessage
import com.example.myapplication.models.ClassicState
import com.example.myapplication.util.SystemTimeline
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

// Create a clean state object for your Classic UI to consume
data class ClassicUiState(
    val state: ClassicState,
    val address: String,
    val security: ConnectionSecurity, // Match whatever type getConnectionSecurity() returns
    val batteryLevel: Int,
    val batteryError: BatteryErrorProfile
)

class Hybridization_On_Connected : ViewModel() {

    private var hasAudioEverConnected = false

    // 1. Connection Flags
    private val _isClassicBound = MutableStateFlow(false)
    val isClassicBound = _isClassicBound.asStateFlow()

    // 2. UI Status State Flow (holds the latest UI layout metrics)
    private val _classicUiState = MutableStateFlow<ClassicUiState?>(null)
    val classicUiState = _classicUiState.asStateFlow()
    // 3. Bottom Sheet Event Stream (Channels are perfect for transient UI events/dialogs)
    private val _bottomSheetMessages = Channel<String>(Channel.BUFFERED)
    val bottomSheetMessages = _bottomSheetMessages.receiveAsFlow()

    private val _bleBatteryLevel = MutableStateFlow<Int?>(null)
    val bleBatteryLevel = _bleBatteryLevel.asStateFlow()


    fun onServiceConnected(service: ClassicBluetoothService) {
        _isClassicBound.value = true

        val manager = service.connectionManager

        // Job 1: Process UI Info & Timeline Logs
        viewModelScope.launch {
            manager.connectionInfo.collect { info ->
                // Push the raw parameters into a clean data state instead of updating UI directly
                _classicUiState.value = ClassicUiState(
                    state = info.state,
                    address = info.address,
                    security = manager.getConnectionSecurity(),
                    batteryLevel = info.batteryLevel,
                    batteryError = info.batteryError
                )

                when (val s = info.state) {
                    ClassicState.CONNECTING   -> SystemTimeline.log("🔄 Classic connecting to ${info.deviceName.ifBlank { info.address }}")
                    ClassicState.CONNECTED    -> SystemTimeline.log("🟢 Classic connected: ${info.deviceName}")
                    ClassicState.DISCONNECTED -> SystemTimeline.log("🔇 Classic disconnected")
                    is ClassicState.RECONNECTING -> SystemTimeline.log("🔄 Classic reconnecting (${s.attempt}/${ClassicConnectionManager.RECONNECT_MAX_ATTEMPTS})")
                    is ClassicState.FAILED    -> SystemTimeline.log("❌ Classic failed: ${s.reason}")
                    else -> {}
                }
            }
        }

        // Job 2: Flatten Classic Messages to plain strings
        viewModelScope.launch {
            manager.messages.collect { message ->
                val display = when (message) {
                    is ClassicMessage.Text -> "[Classic] ${message.raw}  |  ${message.hex}"
                    is ClassicMessage.Binary -> "[Classic Binary] ${message.bytes.size} bytes"
                    is ClassicMessage.ParseError -> "[Parse Error] ${message.reason}"
                }
                _bottomSheetMessages.send(display)
            }
        }
        viewModelScope.launch {
            manager.events.collect { event ->
                _bottomSheetMessages.send("[Log] $event")
            }
        }

        // Job 3: Handle File Transfers
        viewModelScope.launch {
            service.fileTransferManager.state.collect { state ->
                val msg = when (state) {
                    is FileTransferState.Idle -> null
                    is FileTransferState.Sending -> "⬆ ${state.filename}: ${(state.progress * 100).toInt()}%"
                    is FileTransferState.Receiving   -> "⬇ ${state.filename}: ${(state.progress * 100).toInt()}%"
                    is FileTransferState.Done        -> if (state.direction == TransferDirection.SEND) "✅ Sent: ${state.filename}" else "✅ Saved: ${state.filename}"
                    is FileTransferState.Failed      -> "❌ ${state.reason}"
                    is FileTransferState.Cancelled -> "⚠ Transfer cancelled"
                }
                msg?.let { _bottomSheetMessages.send("[Transfer] $it") }
            }
        }

        // Job 4: Audio Profiles
        viewModelScope.launch {
            service.audioProfileManager.connectionInfo.collect { info ->
                if (info.state == AudioProfileState.CONNECTED || info.state == AudioProfileState.PLAYING) {
                    hasAudioEverConnected = true
                }
                val msg = when (val state = info.state) {
                    AudioProfileState.IDLE -> if (hasAudioEverConnected) "Audio: Idle" else ""
                    AudioProfileState.CONNECTING -> "🎧 Audio Connecting..."
                    AudioProfileState.CONNECTED -> "🎧 Audio Connected: ${info.deviceName}"
                    AudioProfileState.PLAYING -> "▶ Playing on ${info.deviceName}" + if (info.codecName.isNotEmpty()) " · ${info.codecName}" else ""
                    AudioProfileState.DISCONNECTED -> "🔇 Audio Disconnected"
                    is AudioProfileState.RECONNECTING -> "🔄 Audio Reconnecting (${state.attempt}/3)"
                    is AudioProfileState.FAILED -> "❌ Audio Failed: ${state.reason}"
                }
                if (msg.isNotBlank()) {
                    _bottomSheetMessages.send("[A2DP] $msg")
                }
            }
        }
    }
    fun observeBleBattery(bluetoothService: BluetoothService?) {
        viewModelScope.launch {
            bluetoothService?.batteryLevel?.collect { level ->
                _bleBatteryLevel.value = level
            }
        }
    }


}
