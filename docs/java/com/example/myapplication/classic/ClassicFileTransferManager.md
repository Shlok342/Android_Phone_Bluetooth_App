**FileName:** ClassicFileTransferManager.kt   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/java/com/example/myapplication/classic/ClassicFileTransferManager.kt   
**Tags:** bluetooth, file-transfer, coroutines, streaming, media-store   

**File Summary**
ClassicFileTransferManager implements a production-grade streaming file transfer protocol over Bluetooth RFCOMM/SPP. It provides a framed packet format with header CRC-16 and payload CRC-32, a fixed-capacity ring buffer for incoming bytes, a parser coroutine that validates and dispatches packets, and coroutines to send and receive files without holding entire files in memory (streaming via ContentResolver/MediaStore). The file includes flow-control (sliding window + semaphore), incremental CRC computation, and MediaStore/legacy storage helpers for writing received files.

**Function Summaries**
1. **Companion object & crc16**
   - Category: constants, utility
   - Lines: 72-113
   - **Description**
     - Defines protocol constants (magic bytes, packet types, offsets, sizes, flow-control parameters, timeouts, ring capacity).
     - Provides crc16(data, offset, len) implementing CRC-16/CCITT-FALSE used to validate packet headers and prevent accidental header matches inside payloads.
   - **Parameters description**
     - crc16(data, offset, len) takes a byte array and range to compute a 16-bit CRC over.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | data | ByteArray | Byte array containing bytes to checksum. |
     | offset | Int | Start index in data to compute CRC from. |
     | len | Int | Number of bytes to include in CRC. |
   - **Returns description**
     - Returns the computed 16-bit CRC as Int.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | crc | Int | CRC-16 value (0..0xFFFF) computed over the specified bytes. |

2. **RingBuffer**
   - Category: class, data-structure, concurrency
   - Lines: 166-211
   - **Description**
     - Implements a fixed-capacity circular buffer with absolute (monotonically increasing) read/write indices to avoid index wrapping and GC churn.
     - Provides thread-safe operations for a single writer and single reader: write, peekAt (zero-copy inspection relative to read pointer), byteAt, consume, reset, and properties for available/free space.
   - **Parameters description**
     - Constructor takes capacity (cap) and creates internal byte array. Methods accept byte arrays and offsets for reading/writing.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | cap | Int | Capacity (size) of the underlying circular buffer in bytes. |
   - **Returns description**
     - Methods return Booleans for success where applicable and void for consume/reset.

3. **Pkt**
   - Category: data-class-like internal
   - Lines: 214-219
   - **Description**
     - Small internal holder representing a parsed packet with session id, sequence number, type, and payload.
     - Used to dispatch typed packets across channels (ack, chunk, control).
   - **Parameters description**
     - Fields set at construction: session short, seq int, type byte, payload byte array.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | session | Short | Session identifier for the transfer; used to demultiplex packets. |
     | seq | Int | Packet sequence number (monotonic within session). |
     | type | Byte | Packet type (HELLO, ACK, CHUNK, DONE, DONE_ACK, ERROR). |
     | payload | ByteArray | Packet payload bytes (length determined by header). |
   - **Returns description**
     - N/A

4. **Public state & callbacks**
   - Category: state
   - Lines: 223-229
   - **Description**
     - Exposes a StateFlow<FileTransferState> representing current transfer state and a nullable callback onFileReceived invoked when an inbound file is fully written.
     - Holds MutableStateFlow for internal updates and exposes an immutable StateFlow for consumers.
   - **Parameters description**
     - No parameters; these are fields used by other methods and external observers.
   - **Returns description**
     - N/A

5. **Internal plumbing: channels, ring, jobs**
   - Category: state, concurrency
   - Lines: 230-244
   - **Description**
     - Declares ring buffer instance, signaling channel (dataReady), typed dispatch channels (ackCh, chunkCh, controlCh), and active session and job references for parser and transfer coroutines.
     - Channels are sized to bound memory and reflect expected protocol behavior.
   - **Parameters description**
     - N/A
   - **Returns description**
     - N/A

6. **Initializer: rawBytes collector**
   - Category: coroutine, event listener
   - Lines: 245-261
   - **Description**
     - Launches a coroutine on Dispatchers.IO that collects bytes from connectionManager.rawBytes, writes them into the RingBuffer with limited attempts/back-pressure, resets the ring on unrecoverable overflow, and signals the parser via a conflated Channel.
     - Starts the parser coroutine.
   - **Parameters description**
     - No parameters; uses constructor-scoped connectionManager and scope.
   - **Returns description**
     - N/A

7. **startParser**
   - Category: coroutine, parser, state-machine
   - Lines: 270-348
   - **Description**
     - Runs permanently on Dispatchers.IO. Sequentially reads the ring buffer, performs fast magic-byte checks then copies and validates full headers with CRC-16, waits for payload+footer, validates payload CRC-32, consumes total packet bytes, creates Pkt objects and dispatches them to typed channels.
     - Performs sync-recovery by advancing the read pointer by one byte on mismatches and yields to avoid busy loops. HELLO packets start doReceive when idle.
   - **Parameters description**
     - No parameters; uses ring buffer and channels declared on the class.
   - **Returns description**
     - N/A (launches and stores Job reference).

8. **awaitAvailable**
   - Category: suspending helper
   - Lines: 355-363
   - **Description**
     - Suspends until at least n bytes are available in the ring buffer or TIMEOUT_MS is reached. Uses a conflated dataReady channel so many writes coalesce into single wake-ups and polls with short timeouts to avoid busy-waiting.
     - Returns boolean indicating success (true if bytes available) or timeout (false).
   - **Parameters description**
     - Parameter n is the required number of bytes to be available.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | n | Int | Minimum number of bytes required in the ring buffer before returning true. |
   - **Returns description**
     - Boolean indicating whether the required bytes are available before timeout.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | result | Boolean | true if n bytes became available; false if deadline elapsed before availability. |

9. **sendFile**
   - Category: public API, launcher
   - Lines: 367-371
   - **Description**
     - Public method to initiate sending a file identified by a Uri. Returns immediately; if a transfer is already active it returns early. Launches doSend in an IO coroutine as transferJob.
   - **Parameters description**
     - Accepts a content Uri pointing to the file to be sent.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | uri | Uri | Content URI of the file to send (resolved using ContentResolver). |
   - **Returns description**
     - No return value; side effect starts transferJob.

10. **doSend**
   - Category: suspending, transfer (sender)
   - Lines: 373-517
   - **Description**
     - Performs the full send-side flow: resolves file metadata, prepares session/seq numbers, sets state and transfer mode, drains stale channels, runs an ACK processing coroutine that releases window permits, performs HELLO handshake and waits for HELLO ACK, streams file as CHUNK packets honoring sliding-window flow-control (Semaphore) and measuring avg ACK latency to adjust pacing, sends DONE with file CRC and waits for DONE_ACK, and updates state on success/failure.
     - Handles errors and maps exceptions into human-friendly failure states; cleans up by cancelling ackJob and resetting transfer mode/state in finally block.
   - **Parameters description**
     - Accepts a content Uri and uses class context and connectionManager to read and send file contents.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | uri | Uri | Content URI for file to send; opened via ContentResolver for streaming. |
   - **Returns description**
     - No explicit return; updates _state and may throw CancellationException to propagate coroutine cancellation.

11. **ACK processor (inside doSend)**
   - Category: coroutine, flow-control
   - Lines: 392-435
   - **Description**
     - Consumes ackCh channel messages. Validates ack payload length and ack sequence monotonicity. Completes helloAcked when seq==0 (HELLO ACK). Releases window semaphore permits corresponding to newly cumulative-acked chunks. On parse failures / invalid sequences, calls handleParseFailure and cancels the sending coroutine's Job.
   - **Parameters description**
     - Runs as a coroutine launched from doSend, reading Pkt objects from ackCh.
   - **Returns description**
     - No return; side effects include releasing Semaphore permits and completing helloAcked.

12. **doReceive**
   - Category: suspending, transfer (receiver)
   - Lines: 522-656
   - **Description**
     - Handles an inbound HELLO Pkt to receive a file: parses HELLO payload to get file size and filename, sets state and transfer mode, ACKs the HELLO, opens a streaming OutputStream (MediaStore or legacy file), incrementally receives CHUNK packets and writes payload directly to the output stream while updating incremental CRC, sends cumulative ACKs every ACK_EVERY chunks, handles DONE by verifying file-level CRC and replies with DONE_ACK indicating success or failure, and calls onFileReceived on success.
     - On errors it attempts to delete partial files and reports failure states; finally resets transfer mode and state.
   - **Parameters description**
     - Accepts a Pkt representing the HELLO packet and uses it to start a receive session.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | hello | Pkt | HELLO packet that initiated the inbound transfer, containing file size and filename data. |
   - **Returns description**
     - No return; updates _state and performs side effects (writes file, sends ACKs).

13. **sendPacket**
   - Category: serializer, IO
   - Lines: 661-676
   - **Description**
     - Serializes a packet into the protocol framing: writes magic, session, seq, type, payload length, header CRC-16, payload bytes and payload CRC-32 footer, then asks connectionManager to send the resulting byte array. Throws IOException when connectionManager.sendData returns false.
     - Ensures big-endian encoding per protocol spec.
   - **Parameters description**
     - Takes session id, sequence number, type byte, and payload bytes to construct a full framed packet.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | session | Short | Session identifier for the packet. |
     | seq | Int | Sequence number to encode. |
     | type | Byte | Opcode/type byte for the packet. |
     | payload | ByteArray | Packet payload bytes to include in the packet body. |
   - **Returns description**
     - No return; throws IOException on write queue full.

14. **buildHelloPayload**
   - Category: utility
   - Lines: 680-689
   - **Description**
     - Constructs the HELLO payload containing 8-byte fileSize, 1-byte filename length, and filename bytes (capped at 255 bytes).
     - Used by send-side to offer file metadata to the receiver.
   - **Parameters description**
     - Takes filename and filesize and encodes into byte array.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | filename | String | Filename to be sent; will be truncated to 255 bytes if needed. |
     | fileSize | Long | Size of file in bytes to encode as 8-byte big-endian value. |
   - **Returns description**
     - ByteArray representing HELLO payload.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | out | ByteArray | Encoded HELLO payload: [8 bytes fileSize][1 byte nameLen][nameLen bytes filename]. |

15. **buildAckPayload**
   - Category: utility
   - Lines: 691-692
   - **Description**
     - Builds an 8-byte ACK payload containing cumulative acked sequence (4 bytes) and window size (4 bytes).
     - Used by both sender and receiver to exchange flow-control information.
   - **Parameters description**
     - Accepts ackedSeq (int) and window (int).
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | ackedSeq | Int | Cumulative sequence number acknowledged. |
     | window | Int | Sender window size suggestion / remaining permits count field. |
   - **Returns description**
     - ByteArray of length 8: [ackedSeq (4 bytes)][window (4 bytes)].

16. **openOutputStream**
   - Category: IO helper, storage
   - Lines: 696-715
   - **Description**
     - Opens a streaming OutputStream for the receiver: for Android Q+ uses MediaStore.Downloads insert and returns openOutputStream and Uri, setting IS_PENDING=1 while writing; for legacy devices writes to Environment.DIRECTORY_DOWNLOADS and returns a File Uri.
     - Catches exceptions and returns null on failure.
   - **Parameters description**
     - Takes filename string and returns Pair<OutputStream, Uri> or null.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | filename | String | Desired filename for saved download. |
   - **Returns description**
     - Pair of OutputStream and Uri for the target file, or null if creation/opening fails.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | pair | Pair<OutputStream, Uri>? | Output stream and Uri to write to, or null on error. |

17. **finalizeFile**
   - Category: IO helper, storage
   - Lines: 717-722
   - **Description**
     - Marks MediaStore download entry as complete by clearing IS_PENDING when on Android Q+; no-op on older versions.
     - Used after successful receive to make the file visible to users/apps.
   - **Parameters description**
     - Takes Uri to update MediaStore entry.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | uri | Uri | MediaStore Uri to update. |
   - **Returns description**
     - No return; performs ContentResolver.update on Android Q+.

18. **deleteFile**
   - Category: IO helper, storage
   - Lines: 724-726
   - **Description**
     - Attempts to delete a given Uri from ContentResolver, wrapped with runCatching to ignore errors.
     - Used to clean up partially received files on error/cancellation.
   - **Parameters description**
     - Takes Uri to delete.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | uri | Uri | Uri of the file to delete. |
   - **Returns description**
     - No return; side-effect only.

19. **resolveFilename**
   - Category: utility, content-resolver
   - Lines: 730-735
   - **Description**
     - Queries ContentResolver for OpenableColumns.DISPLAY_NAME to derive a filename for a given Uri; returns null if query fails.
     - Used by send-side to present a filename in HELLO.
   - **Parameters description**
     - Accepts a content Uri and queries metadata via ContentResolver.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | uri | Uri | Uri to query for DISPLAY_NAME. |
   - **Returns description**
     - String filename or null if not resolvable.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | filename | String? | Resolved display name for the Uri or null on failure. |

20. **resolveFileSize**
   - Category: utility, content-resolver
   - Lines: 737-742
   - **Description**
     - Queries ContentResolver for OpenableColumns.SIZE; returns file size in bytes or -1 on failure.
     - Used by sender to advertise file size in HELLO.
   - **Parameters description**
     - Accepts a Uri and queries size column.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | uri | Uri | Uri to query for SIZE. |
   - **Returns description**
     - Long: file size or -1 on failure.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | size | Long | Resolved file size in bytes or -1 if unknown. |

21. **bit-manipulation helpers (rU16, rU32, rU32Long, rU64, wU16, wU32, wU64)**
   - Category: utilities, encoding
   - Lines: 746-772
   - **Description**
     - Little wrappers to read/write big-endian unsigned integers to/from byte arrays used by packet header and payload encoding/decoding.
     - Used throughout parser and serializer to keep code compact and consistent.
   - **Parameters description**
     - Each helper takes a byte array and offset; writers take a value to write; readers return numeric values.
   - **Returns description**
     - Return types vary: Int or Long depending on width.

22. **drainChannels**
   - Category: utility
   - Lines: 776-781
   - **Description**
     - Clears any pending messages from ackCh, chunkCh, and controlCh using tryReceive(), discarding stale packets before starting a new transfer.
     - Helps avoid processing old packets from previous sessions.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - No return; side-effect discards channel messages.

23. **reset**
   - Category: utility, lifecycle
   - Lines: 783-790
   - **Description**
     - Resets internal state: clears avgAckLatencyMs, cancels ongoing transfer job, resets ring buffer, drains channels, disables transfer mode on connection manager, and sets public state to Idle.
     - Intended to restore manager to initial idle state.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - No return; performs cleanup side-effects.

24. **handleParseFailure**
   - Category: utility, error handling
   - Lines: 124-162
   - **Description**
     - Maps ParseFailure sealed cases to protocol error codes and messages, then updates the public _state to FileTransferState.Failed with appropriate filename, message and transfer direction.
     - Centralizes receive/send parse-error handling and state updates.
   - **Parameters description**
     - Accepts sequence, ParseFailure object, optional filename and direction to build an error state.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | seq | Int | Sequence number of the packet that failed parsing. |
     | failure | ParseFailure | Kind of parse failure encountered (InvalidLength, InvalidChecksum, UnsupportedOpcode, Unknown, InvalidSequence). |
     | filename | String? | Optional filename context for the failure state. |
     | direction | TransferDirection | Indicates whether the failure occurred during send or receive. |
   - **Returns description**
     - No return; updates _state to a Failed state.


**Configuration References**
1. **TIMEOUT_MS**
   - Line: 93
   - **What it does:**
     - Timeout used by awaitAvailable, doSend/doReceive waiting for ACKs and payloads. Controls how long the code waits for bytes or peer responses before giving up.
   - **Default value**
     - 15000

2. **POLL_MS**
   - Line: 94
   - **What it does:**
     - Maximum poll interval used in awaitAvailable to limit blocking duration when waiting for additional bytes; used with a conflated signal channel to avoid busy-waiting.
   - **Default value**
     - 200

3. **WINDOW_SIZE**
   - Line: 90
   - **What it does:**
     - Protocol window size controlling how many CHUNK packets can be outstanding; used to size ack/chunk channels and as the Semaphore limit for sender flow-control.
   - **Default value**
     - 8

4. **CHUNK_PAYLOAD**
   - Line: 89
   - **What it does:**
     - Defines the chunk payload size used for reading file bytes and constructing CHUNK packets. Affects per-chunk overhead and memory footprint.
   - **Default value**
     - 1024


**Code Walkthroughs**
1. **Lines:** 102-112
   - **What it does**
     - Implements CRC-16/CCITT-FALSE in a pure byte-wise algorithm without table lookup.
     - Used to validate packet headers; ensures header correctness even if magic bytes appear inside payload.
   - **Why it matters**
     - Non-obvious bitwise algorithm that must match receiver implementation exactly; any modification changes protocol compatibility.

2. **Lines:** 171-187
   - **What it does**
     - RingBuffer.write performs either a single contiguous copy or two-part wrap-around copy depending on tail size and updates absolute write position.
     - Write fails when there is insufficient free space, enabling back-pressure handling upstream.
   - **Why it matters**
     - Careful use of absolute indices avoids modulo wrap-around bugs and GC pressure; concurrency is controlled with synchronized.

3. **Lines:** 193-206
   - **What it does**
     - peekAt copies bytes at a logical offset (relative to rAbs) into a destination array without advancing the read pointer.
     - Handles wrap-around similarly to write and returns false if requested bytes are not yet available.
   - **Why it matters**
     - Zero-copy inspection semantics are key to avoid allocations in parser and to allow header validation without consuming bytes.

4. **Lines:** 280-296
   - **What it does**
     - Fast magic-byte check avoids copying header if first four bytes do not match the constant MAGIC; on mismatch the code consumes one byte and yields to allow forward resynchronization.
     - Helps parser to recover sync when bytes are noisy or partially missing.
   - **Why it matters**
     - This check reduces unnecessary array copies and improves resilience to misaligned input streams.

5. **Lines:** 318-327
   - **What it does**
     - Validates payload CRC-32 by computing on the payload copy and comparing with footer; on mismatch consumes one byte (rescan) instead of discarding entire header.
     - Prevents payload data from masquerading as valid headers because header includes CRC-16.
   - **Why it matters**
     - Robustness: only fully validated packets are consumed; otherwise parser slides and re-scans to find next header candidate.

6. **Lines:** 403-425
   - **What it does**
     - ACK processing validates incoming ack payload length, identifies HELLO ACK (ackedSeq==0), and ensures ack sequences are monotonic; on invalid conditions handleParseFailure is invoked and the sending coroutine's Job is cancelled via doSendJob.cancel().
     - Releases semaphore permits for newly-acked chunks to allow sender to proceed.
   - **Why it matters**
     - Cancellation of the sender's Job on protocol errors is an important control-flow mechanism to abort transfer promptly.

7. **Lines:** 463-472
   - **What it does**
     - Sender measures ack latency when acquiring a window permit; updates an exponential moving average (avgAckLatencyMs) and introduces pacing delays when latency is high to avoid congestion.
     - This provides a simple adaptive pacing mechanism based on observed ACK response time.
   - **Why it matters**
     - Adaptive pacing logic influences throughput and latency; understanding the smoothing factor and thresholds is important for tuning.

8. **Lines:** 696-706
   - **What it does**
     - For Android Q+, openOutputStream inserts a MediaStore Download entry with IS_PENDING=1 and returns the open OutputStream and Uri so the file can be streamed directly into MediaStore.
     - This avoids temporary files and integrates with scoped storage behavior.
   - **Why it matters**
     - MediaStore path differs by SDK version and has important side effects (visibility and permissions).


**Style Conventions**
1. **Lines:** 72-92
   - **Guideline**
     - Uses companion object to hold constants and utility function; constants use uppercase naming for clarity.
     - Packet offsets and sizes are declared as constants to keep protocol definitions centralized and readable.
   - **Rationale**
     - Keeps protocol parameters in one place for easy auditing and modification if protocol changes are needed.

2. **Lines:** 166-211
   - **Guideline**
     - Synchronized methods on RingBuffer are used explicitly instead of higher-level concurrency primitives, reflecting a design for single writer / single reader and minimizing overhead.
     - Absolute indices (wAbs/rAbs) chosen for simplicity and to avoid integer wrapping logic.
   - **Rationale**
     - Explicit synchronization simplifies reasoning about concurrency and avoids subtle race conditions under low-level buffer operations.

3. **Lines:** 270-348
   - **Guideline**
     - Parser uses a while(isActive) loop and yield() after consuming a single byte for resynchronization to avoid busy loops. Uses suspending awaitAvailable instead of blocking waits.
     - Coroutines are launched on Dispatchers.IO for blocking IO operations.
   - **Rationale**
     - Follows coroutine best practices for long-running IO-bound tasks and cooperative cancellation.

4. **Lines:** 746-772
   - **Guideline**
     - Helper functions for reading and writing unsigned integers use explicit big-endian bit operations for clarity and correctness.
     - Naming convention rU16/wU16 etc. is compact and consistent across helpers.
   - **Rationale**
     - Consistent helper naming reduces duplication and clarifies endianness.


**Event Handling**
1. **rawBytes collector**
   - Lines: 246-259
   - **Trigger Type:** connectionManager.rawBytes (Flow<ByteArray>)
   - **Behavior**
     - Collects raw incoming bytes from connectionManager.rawBytes Flow and writes them into the ring buffer. On each chunk, attempts multiple writes with small delays if the ring is full, resets the ring on unrecoverable overflow, and signals the parser using a conflated dataReady channel.
     - This acts as the entrypoint for all incoming byte streams and back-pressures upstream by resetting on overflow.
   - **Impact**
     - Feeds parser; overflow handling can drop data by resetting ring, potentially affecting transfer reliability.

2. **parser dispatch**
   - Lines: 270-346
   - **Trigger Type:** Internal parser coroutine reading ring buffer
   - **Behavior**
     - Parser reads validated packets and dispatches them to typed channels: ackCh for ACKs, chunkCh for CHUNK payloads, and controlCh for DONE/DONE_ACK/ERROR. HELLO packets trigger creation of a receive job if state is Idle.
     - Channels decouple parser from transfer logic and provide concurrency boundaries between parsing and per-transfer coroutines.
   - **Impact**
     - Determines which coroutine handles each packet type and starts/stops transfer coroutines (doReceive/doSend).

3. **ACK consumer**
   - Lines: 393-435
   - **Trigger Type:** ackCh Channel
   - **Behavior**
     - ACK processor coroutine consumes ackCh, validates ack payloads, completes hello handshake, enforces monotonicity of cumulative ACKs and releases Semaphore permits corresponding to newly acknowledged sequences; cancels sender job on parse failures or invalid sequence.
     - Helps implement sliding-window flow control and handshake completion.
   - **Impact**
     - Controls sender progress; cancellation on invalid acks aborts transfer.
