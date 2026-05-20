package com.example.myapplication

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.IOException
import java.io.OutputStream

class WriteQueue(
    private val scope: CoroutineScope,
    private val writeTimeoutMs: Long = 5_000L,
    maxQueueSize: Int = 64,
    private val maxRetries: Int = 2
) {
    sealed class WriteResult {
        object Success : WriteResult()
        data class Failure(val reason: String) : WriteResult()
    }

    private data class QueueEntry(
        val data: ByteArray,
        val onResult: ((WriteResult) -> Unit)? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as QueueEntry

            if (!data.contentEquals(other.data)) return false
            if (onResult != other.onResult) return false

            return true
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + (onResult?.hashCode() ?: 0)
            return result
        }
    }

    private val channel = Channel<QueueEntry>(capacity = maxQueueSize)
    private var processorJob: Job? = null

    var onWriteError: ((String) -> Unit)? = null

    fun start(outputStreamProvider: () -> OutputStream?) {
        if (processorJob?.isActive == true) return
        processorJob = scope.launch {
            for (entry in channel) {
                if (!isActive) break
                processEntry(entry, outputStreamProvider)
            }
        }
    }

    fun stop() {
        processorJob?.cancel()
        processorJob = null
        drain()
    }

    fun enqueue(data: ByteArray, onResult: ((WriteResult) -> Unit)? = null): Boolean =
        channel.trySend(QueueEntry(data, onResult)).isSuccess

    fun drain() {
        while (channel.tryReceive().isSuccess) { /* discard */ }
    }

    private suspend fun processEntry(
        entry: QueueEntry,
        outputStreamProvider: () -> OutputStream?
    ) {
        var attempt = 0
        while (attempt <= maxRetries) {
            val stream = outputStreamProvider()
            if (stream == null) {
                entry.onResult?.invoke(WriteResult.Failure("No output stream"))
                onWriteError?.invoke("Write skipped: no output stream")
                return
            }
            try {
                withTimeout(writeTimeoutMs) { stream.write(entry.data) }
                entry.onResult?.invoke(WriteResult.Success)
                return
            } catch (_: TimeoutCancellationException) {
                attempt++
                if (attempt > maxRetries) {
                    entry.onResult?.invoke(WriteResult.Failure("Write timed out"))
                    onWriteError?.invoke("Write timed out after $maxRetries retries")
                }
            } catch (_: IOException) {
                entry.onResult?.invoke(WriteResult.Failure("IO error"))
                onWriteError?.invoke("Write IO error")
                return
            }
        }
    }
}