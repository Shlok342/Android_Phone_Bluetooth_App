package com.example.myapplication.classic

enum class TransferDirection {
    SEND,
    RECEIVE
}

sealed class FileTransferState {

    object Idle : FileTransferState()

    data class Sending(
        val filename: String,
        val bytesSent: Long,
        val totalBytes: Long
    ) : FileTransferState() {

        val progress: Float
            get() = if (totalBytes == 0L) {
                0f
            } else {
                (bytesSent.toDouble() / totalBytes).toFloat()
            }
    }

    data class Receiving(
        val filename: String,
        val bytesReceived: Long,
        val totalBytes: Long
    ) : FileTransferState() {

        val progress: Float
            get() = if (totalBytes == 0L) {
                0f
            } else {
                (bytesReceived.toDouble() / totalBytes).toFloat()
            }
    }

    data class Done(
        val filename: String,
        val totalBytes: Long,
        val direction: TransferDirection
    ) : FileTransferState()

    data class Failed(
        val filename: String?,
        val reason: String,
        val direction: TransferDirection?
    ) : FileTransferState()

    object Cancelled : FileTransferState()
}