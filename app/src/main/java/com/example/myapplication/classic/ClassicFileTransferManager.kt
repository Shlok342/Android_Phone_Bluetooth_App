package com.example.myapplication.classic

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.CRC32
import kotlin.random.Random

/**
 * Production-grade streaming Bluetooth file transfer over RFCOMM/SPP.
 *
 * ── Packet format (all big-endian) ──────────────────────────────────────────
 *   [0..3]   magic      : 0x42 0x54 0x46 0x54  ("BTFT")
 *   [4..5]   sessionId  : UInt16  (random per transfer; rejects stale packets)
 *   [6..9]   seq        : UInt32  (monotonically increasing per session)
 *   [10]     type       : UInt8
 *   [11..12] payloadLen : UInt16  (0..4096)
 *   [13..14] headerCrc  : CRC-16/CCITT-FALSE over bytes[0..12]
 *   [15..]   payload    : payloadLen bytes
 *   [last 4] payloadCrc : CRC-32 of payload (0 when payloadLen == 0)
 *
 * ── Flow control ─────────────────────────────────────────────────────────────
 *   Sliding-window ACK. Sender holds at most [WINDOW_SIZE] unacknowledged chunks
 *   in flight (enforced by a coroutine Semaphore). Receiver sends a cumulative
 *   ACK every [ACK_EVERY] chunks; each ACK releases the corresponding permits.
 *
 * ── Memory model ─────────────────────────────────────────────────────────────
 *   - Send side streams via ContentResolver InputStream in [CHUNK_PAYLOAD]-byte
 *     blocks; never holds the full file in memory.
 *   - Receive side streams directly to a MediaStore OutputStream; never accumulates
 *     the full file in memory.
 *   - Incoming bytes are held in a fixed [RingBuffer] (no resize, no GC churn).
 *   - CRC-32 is computed incrementally on both sides.
 *
 * ── Parser ───────────────────────────────────────────────────────────────────
 *   State machine reads header, validates CRC-16, then reads exactly
 *   (payloadLen + FOOTER_SIZE) bytes. Payload data can NEVER trigger a false
 *   header match because a valid header requires both magic AND a correct CRC-16.
 *   On mismatch the read pointer advances by one byte (sync recovery).
 */
class ClassicFileTransferManager(
    private val connectionManager: ClassicConnectionManager,
    private val context: Context,
    private val scope: CoroutineScope
) {

    // ── Protocol constants ─────────────────────────────────────────────────────

    companion object {
        private val MAGIC = byteArrayOf(0x42, 0x54, 0x46, 0x54)

        const val TYPE_HELLO:    Byte = 0x01
        const val TYPE_ACK:      Byte = 0x02
        const val TYPE_CHUNK:    Byte = 0x03
        const val TYPE_DONE:     Byte = 0x04
        const val TYPE_DONE_ACK: Byte = 0x05
        const val TYPE_ERROR:    Byte = 0x06

        private const val OFF_SESSION = 4
        private const val OFF_SEQ     = 6
        private const val OFF_TYPE    = 10
        private const val OFF_PLEN    = 11
        private const val OFF_HCRC    = 13
        const val HEADER_SIZE         = 15
        const val FOOTER_SIZE         = 4    // payload CRC-32
        const val CHUNK_PAYLOAD       = 1024 // file bytes per CHUNK packet
        const val WINDOW_SIZE         = 8    // max unacknowledged chunks in flight
        const val ACK_EVERY           = 4    // receiver ACKs every N chunks
        private const val RING_CAP    = 256 * 1024   // 256 KB receive ring
        private const val TIMEOUT_MS  = 15_000L
        private const val POLL_MS     = 200L

        /**
         * CRC-16/CCITT-FALSE: poly=0x1021, init=0xFFFF, no reflection.
         * Used to validate packet headers; prevents accidental magic-byte
         * collisions inside payload data.
         */

        fun crc16(data: ByteArray, offset: Int, len: Int): Int {
            var crc = 0xFFFF
            for (i in offset until offset + len) {
                crc = crc xor ((data[i].toInt() and 0xFF) shl 8)
                repeat(8) {
                    crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                }
                crc = crc and 0xFFFF
            }
            return crc
        }
    }

    // ── Ring buffer ────────────────────────────────────────────────────────────
    /**
     * Fixed-capacity circular buffer with absolute (non-wrapping) indices.
     * Single writer (rawBytes collector) / single reader (parser coroutine).
     * peekAt supports zero-copy inspection at any logical offset without
     * advancing the read pointer, eliminating repeated toByteArray() allocations.
     */
    // ADD this property to the class:
    @Volatile private var avgAckLatencyMs: Long = 0L
    private fun handleParseFailure(
        seq: Int,
        failure: ParseFailure,
        filename: String? = null,
        direction: TransferDirection = TransferDirection.RECEIVE
    ) {

        val (errorCode, message) = when (failure) {

            ParseFailure.InvalidLength -> {
                0x02.toByte() to "Malformed packet length"
            }

            ParseFailure.InvalidChecksum -> {
                0x05.toByte() to "Packet checksum mismatch"
            }

            is ParseFailure.UnsupportedOpcode -> {
                0x06.toByte() to "Unsupported opcode: ${failure.opcode}"
            }

            is ParseFailure.Unknown -> {
                0x01.toByte() to failure.message
            }

            ParseFailure.InvalidSequence -> {
                0x07.toByte() to "Invalid packet sequence"
            }

        }



        _state.value = FileTransferState.Failed(
            filename = filename,
            reason = message,
            direction = direction
        )
    }



    private class RingBuffer(val cap: Int) {
        private val buf  = ByteArray(cap)
        private var wAbs = 0L   // absolute write position (never resets)
        private var rAbs = 0L   // absolute read  position (never resets)

        @get:Synchronized val available: Int get() = (wAbs - rAbs).toInt()
        @get:Synchronized val free:      Int get() = cap - available

        @Synchronized
        fun write(src: ByteArray, srcOff: Int, len: Int): Boolean {
            if (len > free) return false
            val pos  = (wAbs % cap).toInt()
            val tail = cap - pos
            if (len <= tail) {
                src.copyInto(buf, pos, srcOff, srcOff + len)
            } else {
                src.copyInto(buf, pos, srcOff, srcOff + tail)
                src.copyInto(buf, 0, srcOff + tail, srcOff + len)
            }
            wAbs += len
            return true
        }

        /**
         * Copy [len] bytes at logical offset [logOff] from the current read position
         * into [dst] at [dstOff]. Does NOT advance the read pointer.
         */
        @Synchronized
        fun peekAt(dst: ByteArray, dstOff: Int, logOff: Int, len: Int): Boolean {
            if (logOff + len > available) return false
            val abs  = rAbs + logOff
            val pos  = (abs % cap).toInt()
            val tail = cap - pos
            if (len <= tail) {
                buf.copyInto(dst, dstOff, pos, pos + len)
            } else {
                buf.copyInto(dst, dstOff, pos, pos + tail)
                buf.copyInto(dst, dstOff + tail, 0, len - tail)
            }
            return true
        }

        @Synchronized fun byteAt(logOff: Int): Byte = buf[((rAbs + logOff) % cap).toInt()]
        @Synchronized fun consume(n: Int)           { rAbs += n }
        @Synchronized fun reset()                   { wAbs = 0; rAbs = 0 }
    }

    // ── Internal packet type ───────────────────────────────────────────────────
    private class Pkt(
        val session: Short,
        val seq:     Int,
        val type:    Byte,
        val payload: ByteArray
    )



    // ── Public state ───────────────────────────────────────────────────────────
    private val _state = MutableStateFlow<FileTransferState>(FileTransferState.Idle)
    val state: StateFlow<FileTransferState> = _state.asStateFlow()

    /** Invoked (on an IO thread) when an inbound file is fully written to disk. */
    var onFileReceived: ((filename: String, uri: Uri) -> Unit)? = null

    // ── Internal plumbing ──────────────────────────────────────────────────────
    private val ring      = RingBuffer(RING_CAP)
    private val dataReady = Channel<Unit>(Channel.CONFLATED) // signals parser

    // Typed dispatch channels (parser → transfer coroutines)
    private val ackCh     = Channel<Pkt>(64)
    private val chunkCh   = Channel<Pkt>(WINDOW_SIZE * 4)
    private val controlCh = Channel<Pkt>(16) // HELLO, DONE, DONE_ACK, ERROR


    @Volatile private var activeSession: Short = 0

    private var parserJob:   Job? = null
    private var transferJob: Job? = null

    // ── Init ──────────────────────────────────────────────────────────────────
    init {
        // Write incoming raw bytes into the ring buffer; signal the parser.
        scope.launch(Dispatchers.IO) {
            connectionManager.rawBytes.collect { bytes ->
                var written  = false
                var attempts = 0
                while (!written && attempts++ < 100) {
                    written = ring.write(bytes, 0, bytes.size)
                    if (!written) delay(5L)  // back-pressure: ring full
                }
                if (!written) ring.reset()   // unrecoverable overflow: clear
                dataReady.trySend(Unit)
            }
        }
        startParser()
    }

    // ── Parser coroutine ──────────────────────────────────────────────────────
    /**
     * Runs permanently on Dispatchers.IO. Reads the ring buffer sequentially,
     * validates each header with CRC-16, then reads the exact payload byte count
     * and validates it with CRC-32. Advances by one byte on any mismatch
     * (sync recovery). Dispatches valid packets to typed channels.
     */
    private fun startParser() {
        parserJob?.cancel()
        parserJob = scope.launch(Dispatchers.IO) {
            val hBuf = ByteArray(HEADER_SIZE)
            val fBuf = ByteArray(FOOTER_SIZE)

            while (isActive) {
                // ── Wait for a full header ─────────────────────────────────────
                if (!awaitAvailable(HEADER_SIZE)) continue

                // Fast magic check (no copy) before committing to a full header read
                if (ring.byteAt(0) != MAGIC[0] || ring.byteAt(1) != MAGIC[1] ||
                    ring.byteAt(2) != MAGIC[2] || ring.byteAt(3) != MAGIC[3]) {
                    ring.consume(1)
                    yield()
                    continue
                }

                // Copy header; validate CRC-16 over bytes [0..12]
                ring.peekAt(hBuf, 0, 0, HEADER_SIZE)
                val hCrcExpect = rU16(hBuf, OFF_HCRC)
                val hCrcActual = crc16(hBuf, 0, OFF_HCRC)
                if (hCrcExpect != hCrcActual) {
                    // Valid magic, bad CRC: almost certainly noise — slide forward
                    ring.consume(1)
                    yield()
                    continue
                }

                // Valid header — extract fields
                val session    = rU16(hBuf, OFF_SESSION).toShort()
                val seq        = rU32(hBuf, OFF_SEQ)
                val type       = hBuf[OFF_TYPE]
                val payloadLen = rU16(hBuf, OFF_PLEN)
                val totalSize  = HEADER_SIZE + payloadLen + FOOTER_SIZE

                // ── Wait for payload + footer ──────────────────────────────────
                if (!awaitAvailable(totalSize)) {
                    // Timed out waiting for payload bytes: desync, re-scan
                    ring.consume(1)
                    continue
                }

                // Read payload (zero-copy direct from ring)
                val payload = ByteArray(payloadLen)
                if (payloadLen > 0) ring.peekAt(payload, 0, HEADER_SIZE, payloadLen)

                // Validate payload CRC-32
                ring.peekAt(fBuf, 0, HEADER_SIZE + payloadLen, FOOTER_SIZE)
                val pCrcExpect = rU32Long(fBuf, 0)
                val pCrcActual = if (payloadLen > 0) {
                    val c = CRC32(); c.update(payload); c.value
                } else 0L

                if (payloadLen > 0 && pCrcExpect != pCrcActual) {
                    ring.consume(1)   // payload corrupt: re-scan
                    continue
                }

                // Consume exactly totalSize bytes; dispatch to typed channel
                ring.consume(totalSize)

                val pkt = Pkt(session, seq, type, payload)
                when (type) {
                    TYPE_ACK   -> ackCh.trySend(pkt)
                    TYPE_CHUNK -> chunkCh.trySend(pkt)
                    TYPE_HELLO -> {
                        // Inbound file transfer request: only accept when idle
                        if (_state.value is FileTransferState.Idle) {
                            activeSession = session
                            transferJob?.cancel()
                            transferJob = scope.launch(Dispatchers.IO) { doReceive(pkt) }
                        }
                    }
                    else -> controlCh.trySend(pkt) // DONE, DONE_ACK, ERROR
                }
            }
        }
    }

    /**
     * Suspends until [n] bytes are available in the ring buffer or [TIMEOUT_MS]
     * elapses. Uses a CONFLATED signal channel so many writes coalesce into a
     * single wake-up, avoiding busy-wait.
     */
    private suspend fun awaitAvailable(n: Int): Boolean {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (ring.available < n) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) return false
            withTimeoutOrNull(minOf(remaining, POLL_MS)) { dataReady.receive() }
        }
        return true
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    fun sendFile(uri: Uri) {
        if (_state.value !is FileTransferState.Idle) return
        transferJob?.cancel()
        transferJob = scope.launch(Dispatchers.IO) { doSend(uri) }
    }

    private suspend fun doSend(uri: Uri) {
        val filename = resolveFilename(uri) ?: "file_${System.currentTimeMillis()}"
        val fileSize = resolveFileSize(uri)
        val session  = Random.nextInt(1, 32767).toShort()
        activeSession = session
        var nextSeq   = 0

        _state.value = FileTransferState.Sending(filename, 0L, fileSize)
        connectionManager.setTransferMode(true)
        drainChannels()

        // Semaphore enforces window: sender blocks when WINDOW_SIZE chunks are in flight
        val window = Semaphore(WINDOW_SIZE)
        // Deferred completed when receiver ACKs the HELLO packet
        val helloAcked = CompletableDeferred<Unit>()
        var lastAcked  = -1  // seq of last ACKed chunk (-1 = none yet)

        // ACK processor: runs in parallel with chunk sending.
        // Releases one window permit per newly-acknowledged chunk.
        val doSendJob = currentCoroutineContext()[Job.Key]!!
        val ackJob = scope.launch {
            for (ack in ackCh) {
                if (ack.payload.size < 4) {

                    handleParseFailure(
                        seq = ack.seq,
                        failure = ParseFailure.InvalidLength,
                        filename = filename,
                        direction = TransferDirection.SEND
                    )

                    doSendJob.cancel()   // ← ADD
                    break
                }
                val ackedSeq = rU32(ack.payload, 0)

                if (!helloAcked.isCompleted && ackedSeq == 0) {
                    // This is the HELLO ACK
                    helloAcked.complete(Unit)
                    lastAcked = 0
                    continue
                }

                if (ackedSeq < lastAcked) {

                    handleParseFailure(
                        seq = ack.seq,
                        failure = ParseFailure.InvalidSequence,
                        filename = filename,
                        direction = TransferDirection.SEND
                    )
                    doSendJob.cancel()   // ← ADD
                    break
                }

                val newly = ackedSeq - lastAcked

                if (newly > 0) {
                    lastAcked = ackedSeq
                    repeat(newly) { window.release() }
                }
            }
        }

        try {
            // ── HELLO handshake ────────────────────────────────────────────────
            sendPacket(session, nextSeq++, TYPE_HELLO, buildHelloPayload(filename, fileSize))
            withTimeoutOrNull(TIMEOUT_MS) { helloAcked.await() }
                ?: throw IOException(
                    "Remote device did not respond to handshake. " +
                            "Only Android devices running this app can receive files."
                )
            // nextSeq is now 1; chunks start at seq=1

            // ── Stream file chunks ─────────────────────────────────────────────
            val fileCrc   = CRC32()
            var bytesSent = 0L
            val buf       = ByteArray(CHUNK_PAYLOAD)

            context.contentResolver.openInputStream(uri)
                ?.use { inStream ->
                    var chunkSeq = nextSeq
                    var n: Int
                    while (inStream.read(buf).also { n = it } != -1) {
                        val payload = buf.copyOf(n)   // snapshot before buf is reused
                        fileCrc.update(payload)

                        // Backpressure: block until a window slot is free.
                        // No delay() needed — this is true flow control.
                        val t0 = System.currentTimeMillis()
                        withTimeoutOrNull(TIMEOUT_MS) { window.acquire() }
                            ?: throw IOException("Flow-control timeout — receiver stopped ACKing")
                        val ackLatency = System.currentTimeMillis() - t0
                        if (ackLatency > 0) {
                            avgAckLatencyMs = (avgAckLatencyMs * 3 + ackLatency) / 4
                        }
                        if (avgAckLatencyMs > 500L) {
                            delay((avgAckLatencyMs / WINDOW_SIZE).coerceIn(20L, 200L))
                        }
                        sendPacket(session, chunkSeq++, TYPE_CHUNK, payload)
                        bytesSent += n
                        _state.value = FileTransferState.Sending(filename, bytesSent, fileSize)
                    }
                    nextSeq = chunkSeq
                }
                ?: throw IOException("Cannot open InputStream for URI")

            // Drain window: all outstanding chunks must be ACKed before DONE
            repeat(WINDOW_SIZE) { window.acquire() }


            val donePay = ByteArray(4).also { wU32(it, 0, fileCrc.value.toInt()) }
            sendPacket(session, nextSeq++, TYPE_DONE, donePay)

            val doneAck = withTimeoutOrNull(TIMEOUT_MS) {
                var p: Pkt? = null
                while (p == null || p.type != TYPE_DONE_ACK) {
                    p = controlCh.tryReceive().getOrNull()
                    if (p == null) delay(10L)
                }
                p
            } ?: throw IOException("DONE_ACK timeout — receiver did not confirm")

            val ok = doneAck.payload.isNotEmpty() && doneAck.payload[0] == 0x00.toByte()
            if (!ok) throw IOException("Receiver reported file CRC mismatch")

            _state.value = FileTransferState.Done(filename, bytesSent, TransferDirection.SEND)

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val reason = when {
                e.message?.contains("Flow-control timeout") == true ->
                    "Device too slow to keep up — try a smaller file or move closer"
                e.message?.contains("Write queue full") == true ->
                    "Send buffer full — connection may be congested"
                else -> e.message ?: "Send failed"
            }
            _state.value = FileTransferState.Failed(filename, reason, TransferDirection.SEND)
        } finally {
            ackJob.cancel()
            connectionManager.setTransferMode(false)
            delay(2000L)
            _state.value = FileTransferState.Idle
        }
    }

    // ── Receive ───────────────────────────────────────────────────────────────

    private suspend fun doReceive(hello: Pkt) {
        val session = hello.session

        // Parse HELLO payload: fileSize(8) + filenameLen(1) + filename(n)
        val pay = hello.payload

        val fileSize    = rU64(pay, 0)
        val nameLen     = pay[8].toInt() and 0xFF
        if (pay.size < 9 + nameLen) {

            handleParseFailure(
                seq = hello.seq,
                failure = ParseFailure.InvalidLength,
                direction = TransferDirection.RECEIVE
            )

            connectionManager.setTransferMode(false)
            return
        }
        val filename    = if (nameLen > 0 && pay.size >= 9 + nameLen)
            pay.copyOfRange(9, 9 + nameLen).decodeToString()
        else "bt_recv_${System.currentTimeMillis()}"

        _state.value = FileTransferState.Receiving(filename, 0L, fileSize)
        connectionManager.setTransferMode(true)

        // ACK HELLO immediately
        sendPacket(session, hello.seq, TYPE_ACK, buildAckPayload(0, WINDOW_SIZE))

        // Open streaming output directly to MediaStore (or legacy path)
        val (outStream, fileUri) = openOutputStream(filename) ?: run {
            sendPacket(session, 0, TYPE_ERROR, byteArrayOf(0x03))
            _state.value = FileTransferState.Failed(filename,"Failed to open Output Stream",
                TransferDirection.SEND )
            connectionManager.setTransferMode(false)
            return
        }

        val fileCrc        = CRC32()   // updated incrementally — no full-file CRC at end
        var bytesReceived  = 0L
        var chunksReceived = 0
        var lastSeq        = 0         // seq=0 = HELLO; first chunk is seq=1
        var success        = false

        try {
            outStream.use { out ->
                while (true) {
                    val pkt = withTimeoutOrNull(TIMEOUT_MS) {
                        // Poll chunk and control channels; RFCOMM is ordered so chunks
                        // arrive in seq order. We could also use select{} here.
                        var p: Pkt? = null
                        while (p == null) {
                            p = chunkCh.tryReceive().getOrNull()
                                ?: controlCh.tryReceive().getOrNull()
                            if (p == null) delay(5L)
                        }
                        p
                    } ?: throw IOException("Receive timeout — no data for ${TIMEOUT_MS}ms")

                    if (pkt.session != session) continue

                    when (pkt.type) {
                        TYPE_CHUNK -> {
                            // Write payload directly to disk — no RAM accumulation
                            out.write(pkt.payload)
                            // Incremental CRC-32: O(payloadLen) per chunk, O(1) memory
                            fileCrc.update(pkt.payload)
                            bytesReceived  += pkt.payload.size
                            chunksReceived++
                            lastSeq         = pkt.seq
                            _state.value    = FileTransferState.Receiving(filename, bytesReceived, fileSize)

                            // Send cumulative ACK every ACK_EVERY chunks (flow control)
                            if (chunksReceived % ACK_EVERY == 0) {
                                sendPacket(session, pkt.seq, TYPE_ACK,
                                    buildAckPayload(pkt.seq, WINDOW_SIZE))
                            }
                        }

                        TYPE_DONE -> {
                            // Send final ACK for any un-ACKed tail chunks
                            if (chunksReceived % ACK_EVERY != 0) {
                                sendPacket(session, lastSeq, TYPE_ACK,
                                    buildAckPayload(lastSeq, WINDOW_SIZE))
                            }

                            // Verify file-level CRC sent by sender
                            val expectedCrc = rU32Long(pkt.payload, 0)
                            val status: Byte = if (fileCrc.value == expectedCrc) 0x00 else 0x01
                            sendPacket(session, pkt.seq, TYPE_DONE_ACK, byteArrayOf(status))
                            success = (status == 0x00.toByte())
                            break
                        }

                        TYPE_ERROR -> throw IOException(
                            "Sender reported error code: ${pkt.payload.firstOrNull() ?: 0}"
                        )
                        else -> {

                            handleParseFailure(
                                seq = pkt.seq,
                                failure = ParseFailure.UnsupportedOpcode(pkt.type.toInt()),
                                filename = filename,
                                direction = TransferDirection.RECEIVE
                            )

                            break
                        }
                    }
                }
            }

            if (success) {
                finalizeFile(fileUri)
                _state.value = FileTransferState.Done(filename, bytesReceived, TransferDirection.RECEIVE)
                onFileReceived?.invoke(filename, fileUri)
            } else {
                deleteFile(fileUri)
                _state.value = FileTransferState.Failed(filename,"CRC mismatch — file corrupt",
                    TransferDirection.RECEIVE)
            }

        } catch (e: CancellationException) {
            runCatching { deleteFile(fileUri) }
            throw e
        } catch (e: Exception) {
            runCatching { deleteFile(fileUri) }
            _state.value = FileTransferState.Failed(filename,e.message ?: "Receive failed",
                TransferDirection.RECEIVE)
            runCatching { sendPacket(session, 0, TYPE_ERROR, byteArrayOf(0x04)) }
        } finally {
            connectionManager.setTransferMode(false)
            delay(2000L)
            _state.value = FileTransferState.Idle
        }
    }

    // ── Packet serialisation ──────────────────────────────────────────────────

    private fun sendPacket(session: Short, seq: Int, type: Byte, payload: ByteArray) {
        val pLen   = payload.size
        val packet = ByteArray(HEADER_SIZE + pLen + FOOTER_SIZE)
        MAGIC.copyInto(packet, 0)
        wU16(packet, OFF_SESSION, session.toInt() and 0xFFFF)
        wU32(packet, OFF_SEQ, seq)
        packet[OFF_TYPE] = type
        wU16(packet, OFF_PLEN, pLen)
        wU16(packet, OFF_HCRC, crc16(packet, 0, OFF_HCRC))
        payload.copyInto(packet, HEADER_SIZE)
        val pCrc = if (pLen > 0) { val c = CRC32(); c.update(payload); c.value } else 0L
        wU32(packet, HEADER_SIZE + pLen, pCrc.toInt())
        if (!connectionManager.sendData(packet)) {
            throw IOException("Write queue full — transfer aborted")
        }
    }

    // ── Payload builders ──────────────────────────────────────────────────────

    private fun buildHelloPayload(filename: String, fileSize: Long): ByteArray {
        val nameBytes = filename.encodeToByteArray().let {
            if (it.size > 255) it.copyOf(255) else it
        }
        val out = ByteArray(8 + 1 + nameBytes.size)
        wU64(out, 0, fileSize)
        out[8] = nameBytes.size.toByte()
        nameBytes.copyInto(out, 9)
        return out
    }

    private fun buildAckPayload(ackedSeq: Int, window:Int): ByteArray =
        ByteArray(8).also { wU32(it, 0, ackedSeq); wU32(it, 4, window) }

    // ── MediaStore I/O ────────────────────────────────────────────────────────

    private fun openOutputStream(filename: String): Pair<OutputStream, Uri>? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri    = context.contentResolver
                .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            val stream = context.contentResolver.openOutputStream(uri) ?: return null
            Pair(stream, uri)
        } else {
            @Suppress("DEPRECATION")
            val dir  = Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            val file = File(dir, filename)
            Pair(file.outputStream(), Uri.fromFile(file))
        }
    } catch (_: Exception) { null }

    private fun finalizeFile(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val v = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            context.contentResolver.update(uri, v, null, null)
        }
    }

    private fun deleteFile(uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    // ── URI helpers ───────────────────────────────────────────────────────────

    private fun resolveFilename(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val col = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && col >= 0) c.getString(col) else null
        }
    }.getOrNull()

    private fun resolveFileSize(uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val col = c.getColumnIndex(OpenableColumns.SIZE)
            if (c.moveToFirst() && col >= 0) c.getLong(col) else -1L
        } ?: -1L
    }.getOrElse { -1L }

    // ── Bit-manipulation helpers ──────────────────────────────────────────────

    private fun rU16(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0xFF) shl 8) or (b[o+1].toInt() and 0xFF)

    private fun rU32(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o+1].toInt() and 0xFF) shl 16) or
                ((b[o+2].toInt() and 0xFF) shl  8) or  (b[o+3].toInt() and 0xFF)

    private fun rU32Long(b: ByteArray, o: Int=0) = rU32(b, o).toLong() and 0xFFFFFFFFL

    private fun rU64(b: ByteArray, o: Int=0): Long {
        var v = 0L
        for (i in 0..7) v = (v shl 8) or (b[o + i].toLong() and 0xFF)
        return v
    }

    private fun wU16(b: ByteArray, o: Int, v: Int) {
        b[o]   = ((v shr 8) and 0xFF).toByte(); b[o+1] = (v and 0xFF).toByte()
    }

    private fun wU32(b: ByteArray, o: Int, v: Int) {
        b[o]   = ((v shr 24) and 0xFF).toByte(); b[o+1] = ((v shr 16) and 0xFF).toByte()
        b[o+2] = ((v shr  8) and 0xFF).toByte(); b[o+3] =  (v and 0xFF).toByte()
    }

    private fun wU64(b: ByteArray, o: Int=0, v: Long) {
        for (i in 0..7) b[o + i] = ((v shr (56 - i * 8)) and 0xFF).toByte()
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /** Drain stale packets from all channels before a new transfer. */
    private fun drainChannels() {
        while (ackCh.tryReceive().isSuccess)     { /* discard */ }
        while (chunkCh.tryReceive().isSuccess)   { /* discard */ }
        while (controlCh.tryReceive().isSuccess) { /* discard */ }
    }

    fun reset() {
        avgAckLatencyMs = 0L
        transferJob?.cancel()
        ring.reset()
        drainChannels()
        connectionManager.setTransferMode(false)
        _state.value = FileTransferState.Idle
    }
}