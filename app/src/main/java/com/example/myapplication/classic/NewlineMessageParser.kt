package com.example.myapplication.classic
import java.io.ByteArrayOutputStream
import java.nio.charset.MalformedInputException

class NewlineMessageParser(
    private val maxBufferBytes: Int = 64 * 1024
) : MessageParser {

    override var onMessageParsed: ((ClassicMessage) -> Unit)? = null
    private val buffer = ByteArrayOutputStream(512)

    override fun feed(bytes: ByteArray, length: Int) {
        if (buffer.size() + length > maxBufferBytes) {
            val dropped = buffer.toByteArray()
            buffer.reset()
            onMessageParsed?.invoke(
                ClassicMessage.ParseError(
                    reason = ParseFailure.Unknown("Buffer overflow"),
                    rawBytes = dropped
                )
            )
            return
        }
        buffer.write(bytes, 0, length)
        processLines()
    }

    private fun processLines() {
        val data = buffer.toByteArray()
        var cursor = 0

        while (cursor < data.size) {

            var newlineIndex = -1
            for (i in cursor until data.size) {
                if (data[i] == '\n'.code.toByte()) { newlineIndex = i; break }
            }
            if (newlineIndex == -1) break

            val lineBytes = data.copyOfRange(cursor, newlineIndex)
            cursor = newlineIndex + 1

            try {
                val line = lineBytes.toString(Charsets.UTF_8).trimEnd('\r')
                if (line.isNotEmpty()) {
                    onMessageParsed?.invoke(ClassicMessage.Text(raw = line))
                }
            } catch (_: MalformedInputException) {
                onMessageParsed?.invoke(
                    ClassicMessage.ParseError(
                        reason = ParseFailure.Unknown("Invalid UTF-8"),
                        rawBytes = lineBytes
                    )
                )
            }
        }

        // Retain unprocessed tail
        buffer.reset()
        if (cursor < data.size) {
            buffer.write(data, cursor, data.size - cursor)
        }
    }

    override fun reset() {
        buffer.reset()
    }
}