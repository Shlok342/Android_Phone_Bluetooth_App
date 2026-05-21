package com.example.myapplication

class NewlineMessageParser(
    private val maxBufferBytes: Int = 64 * 1024
) : MessageParser {
    override var onMessageParsed: ((ClassicMessage) -> Unit)? = null
    private val buffer = mutableListOf<Byte>()

    override fun feed(bytes: ByteArray, length: Int) {

        buffer.addAll(bytes.take(length))

        if (buffer.size > maxBufferBytes) {

            val dropped = buffer.toByteArray()

            buffer.clear()

            onMessageParsed?.invoke(
                ClassicMessage.ParseError(
                    reason = ParseFailure.Unknown(
                        "Buffer overflow"
                    ),
                    rawBytes = dropped
                )
            )

            return
        }

        while (true) {

            val newlineIndex =
                buffer.indexOf('\n'.code.toByte())

            if (newlineIndex == -1) break

            val lineBytes =
                buffer.take(newlineIndex).toByteArray()

            buffer.subList(
                0,
                newlineIndex + 1
            ).clear()

            try {

                val line = lineBytes
                    .toString(Charsets.UTF_8)
                    .removeSuffix("\r")

                if (line.isNotEmpty()) {
                    emit(line)
                }

            } catch (_: java.nio.charset.MalformedInputException) {

                onMessageParsed?.invoke(
                    ClassicMessage.ParseError(
                        reason = ParseFailure.Unknown(
                            "Invalid UTF-8"
                        ),
                        rawBytes = lineBytes
                    )
                )
            }
        }
    }


    override fun reset() {
        buffer.clear()
    }

    private fun emit(line: String) {

        onMessageParsed?.invoke(
            ClassicMessage.Text(raw = line)
        )
    }
}