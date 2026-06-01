**FileName:** WriteQueue.kt   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/java/com/example/myapplication/classic/WriteQueue.kt   
**Tags:** bluetooth, io, coroutines, queue, concurrency   

**File Summary**
WriteQueue is a coroutine-based, bounded write queue that serializes writes to an OutputStream. It provides enqueue/start/stop/drain operations, retry and timeout handling for each write, and optional callbacks for per-write results and global write errors. The implementation uses Kotlin Coroutines Channel for buffering and a single processor coroutine to perform writes on demand.

**Function Summaries**
1. **WriteQueue**
   - Category: Class
   - Lines: 17-153
   - **Description**
     - Encapsulates a single-producer single-consumer write queue that serializes byte-array writes to an OutputStream provided on-demand.
     - Manages an internal channel used as a bounded queue, a coroutine job that processes queued entries, and provides configuration for write timeout, queue capacity, and retry attempts.
   - **Parameters description**
     - Constructor takes a CoroutineScope to launch the processing coroutine and three configuration parameters (write timeout in ms, maximum queue size, and maximum retries) with defaults.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | scope | CoroutineScope | CoroutineScope used to launch the processor coroutine that reads from the internal channel and performs writes. |
     | writeTimeoutMs | Long | Maximum time in milliseconds to wait for each write operation before timing out. Default is 5000 ms. |
     | maxQueueSize | Int | Capacity of the internal Channel used for buffering write requests. Default is 64. |
     | maxRetries | Int | Maximum number of retry attempts for a timed-out write. Default is 2. |
   - **Returns description**
     - Instantiates and holds state for write queuing and processing; no return value.

2. **WriteResult**
   - Category: Sealed class
   - Lines: 23-26
   - **Description**
     - Represents the outcome of a single queued write.
     - Two variants: Success (no data) and Failure containing a reason string.
   - **Parameters description**
     - No parameters (sealed class used to model result types).
   - **Returns description**
     - Used by per-entry callbacks to report success or failure.

3. **QueueEntry**
   - Category: Private nested class, data holder
   - Lines: 28-48
   - **Description**
     - Internal container for a queued write: holds a defensive copy of the data and an optional callback for per-write result.
     - Implements equals/hashCode based on the byte-array content so entries with identical payloads compare equal.
   - **Parameters description**
     - Constructed with a ByteArray payload and an optional result callback invoked on completion or failure.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | data | ByteArray | Byte payload to be written; stored as a defensive copy to avoid external mutation affecting queued data. |
     | onResult | ((WriteResult) -> Unit)? | Optional callback invoked with the write result (Success or Failure) after attempt(s) complete. |
   - **Returns description**
     - Acts as a queued element; no return value.

4. **start**
   - Category: Function
   - Lines: 57-69
   - **Description**
     - Starts the processor coroutine if not already active. The coroutine iterates over the channel and calls processEntry for each QueueEntry.
     - If the processor is already running, the call is ignored and a log message is emitted.
   - **Parameters description**
     - Accepts a zero-argument lambda that returns an OutputStream?; this provider is invoked for each entry to obtain the current stream to write to.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | outputStreamProvider | () -> OutputStream? | Provider lambda invoked by the processor before each write to retrieve the current OutputStream; can return null if no stream is available. |
   - **Returns description**
     - No return value; side-effect is launching a coroutine stored in processorJob.

5. **stop**
   - Category: Function
   - Lines: 71-77
   - **Description**
     - Stops processing by setting an internal stopped flag, cancelling and clearing the processor job, closing the current channel and replacing it with a fresh channel of the same capacity.
     - Resets internal state so that the queue can be restarted later.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - No return value; performs cancellation and channel recreation as side effects.

6. **enqueue**
   - Category: Function
   - Lines: 79-85
   - **Description**
     - Attempts to add a new write entry to the internal channel non-blockingly. If stopped, immediately invokes the per-entry callback with a Failure and returns false.
     - Uses Channel.trySend to avoid suspension and to indicate whether the enqueue succeeded based on channel capacity.
   - **Parameters description**
     - Accepts the byte-array payload and an optional per-entry result callback. Returns a boolean indicating whether the queue accepted the entry.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | data | ByteArray | Payload to enqueue. A defensive copy is made by QueueEntry. |
     | onResult | ((WriteResult) -> Unit)? | Optional callback to be invoked when the write completes or fails. |
   - **Returns description**
     - Boolean indicating whether the item was successfully enqueued (true) or rejected (false).
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | success | Boolean | True if trySend succeeded and item was queued; false otherwise (including when queue is stopped). |

7. **drain**
   - Category: Function
   - Lines: 87-89
   - **Description**
     - Non-blocking purge of the channel: repeatedly attempts to tryReceive and discards items until the channel is empty.
     - Used to clear outstanding buffered items.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - No return value; side-effect is emptying the channel buffer.

8. **log**
   - Category: Function
   - Lines: 90-95
   - **Description**
     - Wrapper for android.util.Log.d to emit debug logs prefixed with the class tag 'WriteQueue'.
   - **Parameters description**
     - Accepts a single message string and logs it at debug level.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | message | String | Message text to log via android.util.Log.d with 'WriteQueue' as the tag. |
   - **Returns description**
     - No return value.

9. **processEntry**
   - Category: suspend function
   - Lines: 96-152
   - **Description**
     - Performs the actual write for a single QueueEntry. It retrieves the OutputStream via the provided provider, attempts the write using withTimeout, and handles timeouts and IOExceptions.
     - Implements retry logic with an incremental delay between attempts and invokes per-entry and global callbacks on the main dispatcher for UI-thread safety.
   - **Parameters description**
     - Takes a QueueEntry (containing payload and callback) and the OutputStream provider. It's a suspend function that performs blocking IO within coroutine context using withTimeout and withContext to switch to Main for callbacks.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | entry | QueueEntry | The queued write entry containing the data to write and optional per-write callback. |
     | outputStreamProvider | () -> OutputStream? | Lambda that returns the OutputStream to write to for this attempt; may return null to indicate no stream is available. |
   - **Returns description**
     - No return value; invokes callbacks to report success or failure as side effects.


**Configuration References**
1. **writeTimeoutMs**
   - Line: 19
   - **What it does:**
     - Configures the per-write timeout used with withTimeout; impacts how long the processor will wait for stream.write before treating it as a timeout.
   - **Default value**
     - 5000

2. **maxQueueSize**
   - Line: 20,50,76
   - **What it does:**
     - Defines the capacity used for the internal Channel which bounds the number of queued entries and influences backpressure on enqueue operations.
   - **Default value**
     - 64

3. **maxRetries**
   - Line: 21,101,127,138
   - **What it does:**
     - Controls how many retry attempts are made for a timed-out write before reporting a terminal failure; affects retry loop termination and error messages.
   - **Default value**
     - 2

4. **onWriteError**
   - Line: 53,106,120,137,145
   - **What it does:**
     - Optional callback invoked on the Main dispatcher to report write-related errors and retry notifications; allows external observers to react to write problems (e.g., UI updates or logging).
   - **Default value**
     - N/A


**Code Walkthroughs**
1. **Lines:** 33-33
   - **What it does**
     - Creates a defensive copy of the input ByteArray when constructing a QueueEntry.
   - **Why it matters**
     - Protects queued data from external mutation after enqueue; ensures equality and hashCode computation are stable and reflect the snapshot of data at enqueue time.

2. **Lines:** 35-47
   - **What it does**
     - Overrides equals and hashCode so QueueEntry equality is determined by the byte array content.
     - Uses contentEquals and contentHashCode to compare array contents rather than reference equality.
   - **Why it matters**
     - Byte arrays in Kotlin/Java default to reference equality; content-based equality is required to correctly compare payloads and use entries in hashed collections.

3. **Lines:** 50-50
   - **What it does**
     - Initializes a Channel with a fixed capacity equal to maxQueueSize to serve as the bounded queue for write entries.
   - **Why it matters**
     - Bounded channel enforces capacity limits and backpressure on enqueue operations via trySend.

4. **Lines:** 57-69
   - **What it does**
     - Starts a coroutine that consumes the channel using for (entry in channel) and processes entries with processEntry.
     - Checks coroutine isActive to allow cooperative cancellation inside loop.
   - **Why it matters**
     - Using a single consumer coroutine serializes writes and ensures only one writer interacts with the OutputStream at a time; check for isActive enables early exit if the job is cancelled.

5. **Lines:** 58-58
   - **What it does**
     - Sets the stopped flag to false when start() is called.
   - **Why it matters**
     - Resets internal state to allow enqueue to accept new entries after a prior stop().

6. **Lines:** 71-76
   - **What it does**
     - stop() sets stopped true, cancels the processor job, nulls it out, closes the channel and then replaces it with a new Channel of the same capacity.
   - **Why it matters**
     - Clearing the channel discards existing buffered entries and resets the queue to a known empty state.

7. **Lines:** 79-85
   - **What it does**
     - enqueue uses trySend to attempt a non-suspending enqueue; if the queue is in stopped state it immediately returns failure and invokes callback.
   - **Why it matters**
     - Non-blocking enqueue avoids suspending the caller and provides immediate feedback about capacity or stopped status.

8. **Lines:** 87-89
   - **What it does**
     - drain repeatedly tryReceives and discards until empty using a tight loop.
   - **Why it matters**
     - This is a non-suspending busy loop clearing buffered items; it's straightforward but can spin if not used carefully.

9. **Lines:** 100-125
   - **What it does**
     - Retry loop: attempts write up to maxRetries with incremental delay of 100ms * attempt before each try, obtaining the stream each attempt.
     - If the provider returns null, the entry is failed immediately.
   - **Why it matters**
     - Incremental delay implements a simple backoff between retries; obtaining the stream each attempt allows for transient reconnection between tries.

10. **Lines:** 110-116
   - **What it does**
     - Performs the actual write operation inside withTimeout to limit the duration of stream.write(entry.data).
     - On success, invokes entry.onResult on Dispatchers.Main to deliver the Success callback on the main thread.
   - **Why it matters**
     - withTimeout ensures a write that blocks won't hang the processor indefinitely; switching to Main dispatcher ensures UI-safe callback invocation.

11. **Lines:** 117-141
   - **What it does**
     - Handles TimeoutCancellationException: reports a retry message via onWriteError on Main and increments attempt; if retries exhausted, invokes per-entry Failure and a summary onWriteError on Main.
     - Attempts are bounded by maxRetries.
   - **Why it matters**
     - Distinguishes timeout conditions and coordinates retry counting and error reporting to the main thread for potential UI updates or logs.

12. **Lines:** 142-149
   - **What it does**
     - Handles IOException by invoking the entry callback with Failure and calling onWriteError on Main to report the IO error.
   - **Why it matters**
     - IOExceptions are treated as terminal failures for that entry and do not trigger further retries.


**Style Conventions**
1. **Lines:** 58-58
   - **Guideline**
     - The file contains inline comments like ' // ← ADD' indicating recent changes or notes in the code.
     - Use of @Volatile on 'stopped' indicates thread-safe reads/writes across coroutines and possibly other threads.
   - **Rationale**
     - The stop/start flags and comments are notable for understanding lifecycle state and recent edits.

2. **Lines:** 90-95
   - **Guideline**
     - Logging is centralized via a small private log(message: String) wrapper around android.util.Log.d with a fixed tag 'WriteQueue'.
   - **Rationale**
     - N/A

3. **Lines:** 63-69
   - **Guideline**
     - Processor coroutine uses 'for (entry in channel)' which is idiomatic for channel consumption and cooperates with channel.close().
   - **Rationale**
     - N/A
