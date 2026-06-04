package com.example.myapplication.ui

import com.example.myapplication.ble.BleState
import com.example.myapplication.classic.ClassicState

/**
 * Mediates between BLE and Classic Bluetooth states to resolve a single, 
 * consistent UI theme for the background view.
 */
class GlobalUiStateManager(private val backgroundView: GlassmorphicBackgroundView) {

    private var lastBleState: BleState = BleState.IDLE
    private var lastClassicState: ClassicState = ClassicState.IDLE

    fun updateBleState(state: BleState) {
        lastBleState = state
        resolveCompositeState()
    }

    fun updateClassicState(state: ClassicState) {
        lastClassicState = state
        resolveCompositeState()
    }

    private fun resolveCompositeState() {
        // Priority: FAILED > CONNECTING/BONDING > READY > IDLE
        
        val bleSeverity = getBleSeverity(lastBleState)
        val classicSeverity = getClassicSeverity(lastClassicState)

        if (bleSeverity >= 4 || classicSeverity >= 4) {
            backgroundView.transitionToState(BleState.FAILED)
            return
        }

        if (bleSeverity >= 3 || classicSeverity >= 3) {
            backgroundView.transitionToState(BleState.CONNECTING)
            return
        }

        if (bleSeverity >= 2 || classicSeverity >= 2) {
            backgroundView.transitionToState(BleState.READY)
            return
        }

        backgroundView.transitionToState(BleState.IDLE)
    }

    private fun getBleSeverity(state: BleState): Int = when (state) {
        BleState.FAILED               -> 4
        BleState.CONNECTING, 
        BleState.BONDING, 
        BleState.DISCOVERING_SERVICES -> 3
        BleState.READY                -> 2
        BleState.IDLE, 
        BleState.DISCONNECTED         -> 1
    }

    private fun getClassicSeverity(state: ClassicState): Int = when (state) {
        is ClassicState.FAILED        -> 4
        ClassicState.CONNECTING,
        is ClassicState.RECONNECTING  -> 3
        ClassicState.CONNECTED        -> 2
        ClassicState.IDLE,
        ClassicState.DISCONNECTED     -> 1
    }
}
