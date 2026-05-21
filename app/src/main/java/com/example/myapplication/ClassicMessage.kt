package com.example.myapplication

sealed class ClassicMessage {

    abstract val timestampMs: Long

    data class Text(
        val raw: String,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : ClassicMessage() {

        val hex: String by lazy {
            raw.encodeToByteArray()
                .joinToString(" ") { "%02X".format(it) }
        }
    }

    class Binary(
        bytes: ByteArray,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : ClassicMessage() {

        private val _bytes = bytes.copyOf()

        val bytes: ByteArray
            get() = _bytes.copyOf()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Binary) return false

            return _bytes.contentEquals(other._bytes)
        }

        override fun hashCode(): Int {
            return _bytes.contentHashCode()
        }
    }

    class ParseError(
        val reason: ParseFailure,
        rawBytes: ByteArray,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : ClassicMessage() {

        private val _rawBytes = rawBytes.copyOf()

        val rawBytes: ByteArray
            get() = _rawBytes.copyOf()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ParseError) return false

            return reason == other.reason &&
                    _rawBytes.contentEquals(other._rawBytes)
        }

        override fun hashCode(): Int {
            var result = reason.hashCode()
            result = 31 * result + _rawBytes.contentHashCode()
            return result
        }
    }
}

sealed class ParseFailure {
    object InvalidLength : ParseFailure()
    object InvalidChecksum : ParseFailure()

    data class UnsupportedOpcode(
        val opcode: Int
    ) : ParseFailure()

    data class Unknown(
        val message: String
    ) : ParseFailure()
}