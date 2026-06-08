package com.example.myapplication.classic.messages

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.annotation.RequiresPermission
import com.example.myapplication.classic.helpers.ConnectionSecurity
import java.io.IOException
import java.util.UUID
data class SocketResult(
    val socket: BluetoothSocket? = null,
    val security: ConnectionSecurity? = null,
    val userErrorMessage: String? = null,
    val technicalLog: String? = null
)
object SocketFactory {

    private val sppUUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    // Added context parameter to satisfy modern Bluetooth API requirements
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun createSocket(device: BluetoothDevice, context:Context): SocketResult {

        val errors = mutableListOf<String>()

        try {
            val insecureSocket =
                device.createInsecureRfcommSocketToServiceRecord(sppUUID)

            insecureSocket.connect()

            return SocketResult(
                socket = insecureSocket,
                security = ConnectionSecurity.INSECURE
            )
        } catch (e: IOException) {
            errors.add("Insecure RFCOMM: ${e.message}\n${e.stackTraceToString()}")
        }

        try {
            val secureSocket =
                device.createRfcommSocketToServiceRecord(sppUUID)

            secureSocket.connect()

            return SocketResult(
                socket = secureSocket,
                security = ConnectionSecurity.SECURE
            )
        } catch (e: IOException) {
            errors.add("Secure RFCOMM: ${e.message}\n${e.stackTraceToString()}")
        }

        try {
            val fallbackSocket =
                createFallbackSocket(device)

            fallbackSocket.connect()

            return SocketResult(
                socket = fallbackSocket,
                security = ConnectionSecurity.UNKNOWN
            )
        } catch (e: Exception) {
            errors.add("Fallback Reflection: ${e.message}\n${e.stackTraceToString()}")

            // Modern context-based replacement for BluetoothAdapter.getDefaultAdapter()
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter
            val isBluetoothOff = bluetoothAdapter == null || !bluetoothAdapter.isEnabled

            val userMessage = if (isBluetoothOff) {
                "Connection failed because Bluetooth was turned off. Please turn it back on and try again."
            } else {
                "Unable to connect to ${device.name ?: "the device"}. Please ensure it is turned on, nearby, and ready to pair."
            }

            return SocketResult(
                userErrorMessage = userMessage,
                technicalLog = errors.joinToString("\n---\n")
            )
        }
    }

    private fun createFallbackSocket(
        device: BluetoothDevice
    ): BluetoothSocket {
        return device.javaClass
            .getMethod(
                "createRfcommSocket",
                Int::class.javaPrimitiveType
            )
            .invoke(device, 1) as BluetoothSocket
    }
}
