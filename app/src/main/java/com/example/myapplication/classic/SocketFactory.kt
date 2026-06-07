package com.example.myapplication.classic

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.example.myapplication.util.BluetoothConnectionException
import java.io.IOException
import java.util.UUID
data class SocketResult(
    val socket: BluetoothSocket,
    val security: ConnectionSecurity
)
object SocketFactory {

    private val sppUUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    fun createSocket(device: BluetoothDevice): SocketResult {

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

            errors.add(
                "Insecure RFCOMM: ${e.message}"
            )


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

            errors.add(
                "Secure RFCOMM: ${e.message}"
            )


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

            errors.add(
                "Fallback RFCOMM: ${e.message}"
            )

            throw BluetoothConnectionException(
                errors.joinToString("\n")
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