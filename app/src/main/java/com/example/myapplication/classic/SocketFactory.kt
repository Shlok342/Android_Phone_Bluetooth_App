package com.example.myapplication.classic

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.util.UUID

object SocketFactory {

    private val sppUUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    fun createSocket(device: BluetoothDevice): BluetoothSocket {

        try {
            log("Trying insecure RFCOMM...")

            val insecureSocket =
                device.createInsecureRfcommSocketToServiceRecord(sppUUID)

            insecureSocket.connect()

            log("Insecure RFCOMM success")

            return insecureSocket

        } catch (e: IOException) {

            log("Insecure RFCOMM failed: ${e.message}")
        }

        try {
            log("Trying secure RFCOMM...")

            val secureSocket =
                device.createRfcommSocketToServiceRecord(sppUUID)

            secureSocket.connect()

            log("Secure RFCOMM success")

            return secureSocket

        } catch (e: IOException) {

            log("Secure RFCOMM failed: ${e.message}")
        }

        log("Trying reflection fallback...")

        val fallbackSocket = createFallbackSocket(device)

        fallbackSocket.connect()

        return fallbackSocket
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
    private fun log(message: String) {
        Log.d(
            "ClassicConnectionManager",
            message
        )
    }
}