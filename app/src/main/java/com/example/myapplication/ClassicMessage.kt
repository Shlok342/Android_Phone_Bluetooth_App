package com.example.myapplication

sealed class ClassicMessage {
    abstract val timestampMs: Long

    data class Text(
        val raw: String,
        val hex: String,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : ClassicMessage()

    data class Binary(
        val bytes: ByteArray,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : ClassicMessage() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Binary

            if (timestampMs != other.timestampMs) return false
            if (!bytes.contentEquals(other.bytes)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = timestampMs.hashCode()
            result = 31 * result + bytes.contentHashCode()
            return result
        }
    }


    data class ParseError(
        val reason: String,
        val rawBytes: ByteArray,
        override val timestampMs: Long = System.currentTimeMillis()
    ) : ClassicMessage() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ParseError

            if (timestampMs != other.timestampMs) return false
            if (reason != other.reason) return false
            if (!rawBytes.contentEquals(other.rawBytes)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = timestampMs.hashCode()
            result = 31 * result + reason.hashCode()
            result = 31 * result + rawBytes.contentHashCode()
            return result
        }
    }
}