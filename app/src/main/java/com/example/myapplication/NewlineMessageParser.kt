package com.example.myapplication

class NewlineMessageParser(
    private val maxBufferBytes: Int = 64 * 1024
) : MessageParser {

    override var onMessageParsed: ((ClassicMessage) -> Unit)? = null

    private val buffer = StringBuilder()

    override fun feed(bytes: ByteArray, length: Int) {
        val chunk = try {
            String(bytes, 0, length, Charsets.UTF_8)
        } catch (_: Exception) {
            onMessageParsed?.invoke(
                ClassicMessage.ParseError(
                    reason = "Invalid UTF-8",
                    rawBytes = bytes.copyOf(length)
                )
            )
            return
        }

        if (buffer.length + chunk.length > maxBufferBytes) {
            val dropped = buffer.toString()
            buffer.clear()
            onMessageParsed?.invoke(
                ClassicMessage.ParseError(
                    reason = "Buffer overflow — dropped ${dropped.length} bytes",
                    rawBytes = dropped.toByteArray()
                )
            )
        }

        buffer.append(chunk)

        while (buffer.contains('\n')) {
            val end = buffer.indexOf('\n')
            val line = buffer.substring(0, end).trim()
            buffer.delete(0, end + 1)
            if (line.isNotEmpty()) emit(line)
        }
    }

    override fun reset() {
        buffer.clear()
    }

    private fun emit(line: String) {
        val hex = line.toByteArray(Charsets.UTF_8)
            .joinToString(" ") { "%02X".format(it) }
        onMessageParsed?.invoke(ClassicMessage.Text(raw = line, hex = hex))
    }
}