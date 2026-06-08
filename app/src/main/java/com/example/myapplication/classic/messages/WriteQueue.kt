package com.example.myapplication.classic.messages

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.OutputStream

class WriteQueue(
    private val scope: CoroutineScope,
    private val writeTimeoutMs: Long = 5_000L,
    private val maxQueueSize: Int = 64,
    private val maxRetries: Int = 2
) {
    sealed class WriteResult {
        object Success : WriteResult()
        data class Failure(val reason: String) : WriteResult()
    }

    private class QueueEntry(
        data: ByteArray,
        val onResult: ((WriteResult) -> Unit)? = null
    ) {

        val data: ByteArray = data.copyOf()

        override fun equals(other: Any?): Boolean {

            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as QueueEntry

            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            return data.contentHashCode()
        }
    }

    private var channel = Channel<QueueEntry>(capacity = maxQueueSize)
    private var processorJob: Job? = null

    var onWriteError: ((String) -> Unit)? = null

    @Volatile private var stopped = false

    fun start(outputStreamProvider: () -> OutputStream?) {
        stopped = false
        if (processorJob?.isActive == true) {
            log("start() ignored: processor already active")
            return
        }
        processorJob = scope.launch {
            for (entry in channel) {
                if (!isActive) break
                processEntry(entry, outputStreamProvider)
            }
        }
    }

    fun stop() {
        stopped = true
        processorJob?.cancel()
        processorJob = null
        while (true) {
            val entry = channel.tryReceive().getOrNull() ?: break
            entry.onResult?.invoke(WriteResult.Failure("Queue stopped"))
        }
        channel.close()
        channel = Channel(capacity = maxQueueSize)
    }

    fun enqueue(data: ByteArray, onResult: ((WriteResult) -> Unit)? = null): Boolean {
        if (stopped) {                                          // ← ADD
            onResult?.invoke(WriteResult.Failure("Queue stopped"))
            return false
        }
        return channel.trySend(QueueEntry(data, onResult)).isSuccess
    }

    fun drain() {
        while (channel.tryReceive().isSuccess) { /* discard */ }
    }
    private fun log(message: String) {
        Log.d(
            "WriteQueue",
            message
        )
    }
    private suspend fun processEntry(
        entry: QueueEntry,
        outputStreamProvider: () -> OutputStream?
    ) {
        var attempt = 0
        while (attempt <= maxRetries) {
            delay(100L * attempt)
            val stream = outputStreamProvider()
            if (stream == null) {
                entry.onResult?.invoke(WriteResult.Failure("No output stream"))
                onWriteError?.invoke("Write skipped: no output stream")
                return
            }
            try {
                withTimeout(writeTimeoutMs) { stream.write(entry.data) }
                withContext(Dispatchers.Main) {
                    entry.onResult?.invoke(
                        WriteResult.Success
                    )
                }
                return
            } catch (_: TimeoutCancellationException) {

                withContext(Dispatchers.Main) {
                    onWriteError?.invoke(
                        "Retry ${attempt + 1}/$maxRetries"
                    )
                }

                attempt++

                if (attempt > maxRetries) {

                    withContext(Dispatchers.Main) {

                        entry.onResult?.invoke(
                            WriteResult.Failure(
                                "Write timed out"
                            )
                        )

                        onWriteError?.invoke(
                            "Write timed out after $maxRetries retries"
                        )
                    }
                }
            } catch (_: IOException) {
                entry.onResult?.invoke(WriteResult.Failure("IO error"))
                withContext(Dispatchers.Main) {
                    onWriteError?.invoke(
                        "Write IO error"
                    )
                }
                return
            }
        }
    }
}