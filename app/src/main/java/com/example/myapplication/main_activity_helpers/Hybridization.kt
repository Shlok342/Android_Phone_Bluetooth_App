package com.example.myapplication.main_activity_helpers

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import com.example.myapplication.ble.BluetoothService
import com.example.myapplication.util.SystemTimeline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class Hybridization : ViewModel() {
    private val _uiState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val uiState: StateFlow<ConnectionState> = _uiState

    fun connect(device: BluetoothDevice, isBound: Boolean, bluetoothService: BluetoothService?) {
        if (!isBound) return

        val name = try { device.name ?: device.address } catch (_: SecurityException) { device.address }
        SystemTimeline.log("🔗 BLE connect attempt: $name")

        // Update the state so the Activity knows to perform UI/Haptic feedback
        _uiState.value = ConnectionState.Connecting

        bluetoothService?.connect(device)
    }

    // Reset state if needed later
    fun resetState() {
        _uiState.value = ConnectionState.Idle
    }
}

sealed interface ConnectionState {
    object Idle : ConnectionState
    object Connecting : ConnectionState
}
