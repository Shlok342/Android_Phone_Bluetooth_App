// Save as: com/example/myapplication/classic/BluetoothBatteryMonitor.kt
package com.example.myapplication.classic.helpers

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.reflect.InvocationTargetException

/**
 * Explicit error states for the battery subsystem
 */
sealed class BatteryErrorProfile {
    object None : BatteryErrorProfile()
    object ReflectionApiBlocked : BatteryErrorProfile()
    object DeviceUnsupported : BatteryErrorProfile()
    object StaleDataWarning : BatteryErrorProfile()
}

class BluetoothBatteryMonitor(private val context: Context) {

    companion object {
        const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"

        // Threshold to declare battery data stale if no broadcasts are received (e.g., 15 minutes)
        private const val STALE_DATA_THRESHOLD_MS = 15 * 60 * 1000L
    }

    private val _batteryLevel = MutableStateFlow<Int>(-1)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _errorProfile = MutableStateFlow<BatteryErrorProfile>(BatteryErrorProfile.None)
    val errorProfile: StateFlow<BatteryErrorProfile> = _errorProfile.asStateFlow()

    private var currentTrackingDeviceAddress: String? = null
    private var isReceiverRegistered = false

    // Tracks when the metrics were last successfully updated
    var lastUpdatedTimeMs: Long = 0L
        private set

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_BATTERY_LEVEL_CHANGED) {

                // Modern, type-safe Parcelable extraction
                val device = IntentCompat.getParcelableExtra(
                    intent,
                    BluetoothDevice.EXTRA_DEVICE,
                    BluetoothDevice::class.java
                ) ?: return

                if (device.address == currentTrackingDeviceAddress) {
                    val level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)

                    if (level in 0..100) {
                        _batteryLevel.value = level
                        lastUpdatedTimeMs = System.currentTimeMillis()
                        if (_errorProfile.value is BatteryErrorProfile.StaleDataWarning) {
                            _errorProfile.value = BatteryErrorProfile.None
                        }
                    } else {
                        _errorProfile.value = BatteryErrorProfile.DeviceUnsupported
                    }
                }
            }
        }
    }


    /**
     * Executes robust reflection with specific catches for structural system blocks.
     */
    fun queryBatteryViaReflection(device: BluetoothDevice): Int {
        return try {
            val getBatteryLevelMethod = device.javaClass.getMethod("getBatteryLevel")
            val level = getBatteryLevelMethod.invoke(device) as Int

            // Clean up error states if reflection succeeds
            _errorProfile.value = BatteryErrorProfile.None
            level
        } catch (e: NoSuchMethodException) {
            // Android OS version has removed or modified this hidden method structure entirely
            _errorProfile.value = BatteryErrorProfile.ReflectionApiBlocked
            -1
        } catch (e: InvocationTargetException) {
            // Method exists but internal hardware drivers crashed or refused to execute it
            _errorProfile.value = BatteryErrorProfile.DeviceUnsupported
            -1
        } catch (e: Exception) {
            // Fallback for general security blocks or runtime failures
            _errorProfile.value = BatteryErrorProfile.ReflectionApiBlocked
            -1
        }
    }

    fun startMonitoring(device: BluetoothDevice) {
        currentTrackingDeviceAddress = device.address
        _errorProfile.value = BatteryErrorProfile.None

        val initialLevel = queryBatteryViaReflection(device)
        if (initialLevel in 0..100) {
            _batteryLevel.value = initialLevel
            lastUpdatedTimeMs = System.currentTimeMillis()
        } else {
            _batteryLevel.value = -1
        }

        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter(ACTION_BATTERY_LEVEL_CHANGED)
                context.registerReceiver(batteryReceiver, filter)
                isReceiverRegistered = true
            } catch (e: Exception) {
                _errorProfile.value = BatteryErrorProfile.ReflectionApiBlocked
            }
        }
    }

    /**
     * Defensive Execution: Call this via a routine timer or whenever the app is brought
     * to the foreground to check if the background broadcast stream has gone cold.
     */
    fun checkDataFreshness() {
        if (currentTrackingDeviceAddress != null && lastUpdatedTimeMs > 0L) {
            val timeElapsed = System.currentTimeMillis() - lastUpdatedTimeMs
            if (timeElapsed > STALE_DATA_THRESHOLD_MS) {
                _errorProfile.value = BatteryErrorProfile.StaleDataWarning
            }
        }
    }

    fun stopMonitoring() {
        currentTrackingDeviceAddress = null
        _batteryLevel.value = -1
        lastUpdatedTimeMs = 0L
        _errorProfile.value = BatteryErrorProfile.None
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(batteryReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
    }
}
