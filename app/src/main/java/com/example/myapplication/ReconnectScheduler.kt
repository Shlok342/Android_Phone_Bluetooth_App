package com.example.myapplication

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger

class ReconnectScheduler(
    private val scope: CoroutineScope,
    private val maxAttempts: Int,
    private val getIsIntentionalDisconnect: () -> Boolean,
    private val onUpdateState: (ClassicState) -> Unit,
    private val onLogEvent: (String) -> Unit,
    private val onDoConnect: (BluetoothDevice) -> Unit
) {
    companion object {
        private const val FAILURE_COOLDOWN_MS = 60_000L
    }

    private var reconnectJob: Job? = null
    private val _reconnectAttempts = AtomicInteger(0)
    private val consecutiveFailures = AtomicInteger(0)
    @Volatile private var lastFailureTime = 0L

    val reconnectAttempts: Int get() = _reconnectAttempts.get()

    fun handleConnectionFailure(device: BluetoothDevice) {
        consecutiveFailures.incrementAndGet()
        lastFailureTime = System.currentTimeMillis()
        scheduleReconnect(device)
    }
    fun scheduleReconnect(device: BluetoothDevice) {
        if (getIsIntentionalDisconnect())         return
        if (reconnectJob?.isActive ==true)  return
        if (reconnectAttempts >=maxAttempts

            ) {

            onUpdateState(
                ClassicState.FAILED(
                    FailureReason.MaxReconnectAttempts
                )
            )

            return
        }

        // Cooldown after repeated failure bursts
        if (
            consecutiveFailures.get() >= ClassicConnectionManager.Companion.RECONNECT_MAX_ATTEMPTS &&
            System.currentTimeMillis() - lastFailureTime <  FAILURE_COOLDOWN_MS
        ) {

            onUpdateState(
                ClassicState.FAILED(
                    FailureReason.MaxReconnectAttempts
                )
            )

            return
        }



        reconnectJob = scope.launch {
            delay(600)
            val attempt= _reconnectAttempts.incrementAndGet()
            val delayMs = when (attempt) {
                1 -> 800L
                2 -> 1600L
                else -> 3000L
            }
            onUpdateState(ClassicState.RECONNECTING(attempt))
            onLogEvent("Reconnecting… attempt $reconnectAttempts/$maxAttempts")
            delay(delayMs)
            if (!getIsIntentionalDisconnect() && isActive) onDoConnect(device)
        }
    }

    fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    fun reset() {
        _reconnectAttempts.set(0)
        consecutiveFailures.set(0)
    }
}