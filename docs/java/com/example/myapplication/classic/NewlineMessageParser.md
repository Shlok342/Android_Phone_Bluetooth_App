**FileName:** NewlineMessageParser.kt   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/java/com/example/myapplication/classic/NewlineMessageParser.kt   
**Tags:** parser, messaging, bluetooth, kotlin, streaming   

**File Summary**
NewlineMessageParser is a Kotlin class that implements a MessageParser to parse newline-delimited UTF-8 text messages from a byte stream. It buffers incoming bytes, extracts complete lines terminated by LF ('\n'), trims optional CR ('\r'), emits parsed text messages via a callback, and reports parse errors or buffer overflows as ClassicMessage.ParseError. The parser retains any incomplete trailing bytes between feeds so they can be completed by subsequent input.

**Function Summaries**
1. **NewlineMessageParser**
   - Category: class, parser
   - Lines: 5-68
   - **Description**
     - Defines a message parser that consumes raw byte chunks and produces parsed ClassicMessage instances when full newline-terminated text lines are assembled.
     - Manages an internal buffer, enforces a maximum buffer size, and exposes a reset function to clear state.
   - **Parameters description**
     - Constructor parameter controls the maximum allowed buffered bytes to avoid unbounded memory growth.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | maxBufferBytes | Int | Maximum allowed size (in bytes) for the internal buffer. Defaults to 64 * 1024 (65536). If buffering exceeds this, the parser drops buffered bytes and emits a ParseError. |
   - **Returns description**
     - This is a class definition; there are no direct return values.

2. **onMessageParsed property**
   - Category: callback, event
   - Lines: 9-9
   - **Description**
     - Callback property invoked whenever the parser produces a parsed message (ClassicMessage.Text) or a parse error (ClassicMessage.ParseError).
     - It is nullable and intended to be set by the consumer to receive parsing results asynchronously.
   - **Parameters description**
     - Not a function — a property of type ((ClassicMessage) -> Unit)? that accepts a ClassicMessage when set.

3. **buffer (ByteArrayOutputStream)**
   - Category: state, buffer
   - Lines: 10-10
   - **Description**
     - Internal byte buffer used to accumulate incoming bytes across feed() calls until complete newline-terminated lines can be extracted.
     - Initialized with capacity 512 but grows as required up to maxBufferBytes (checked on feed).
   - **Parameters description**
     - Not a function — an internal mutable buffer used by methods in this class.

4. **feed**
   - Category: function, input-consumer
   - Lines: 12-26
   - **Description**
     - Accepts a ByteArray and a length indicating how many bytes from that array to consume, appends them to the internal buffer, enforces the maxBufferBytes limit, and then initiates line processing.
     - If adding the bytes would exceed the max buffer size, the existing buffer contents are dropped and a ClassicMessage.ParseError with reason 'Buffer overflow' is emitted; the incoming bytes are not appended in that overflow case.
   - **Parameters description**
     - Two parameters: the bytes array containing incoming data, and the number of bytes from that array to write into the buffer.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | bytes | ByteArray | Source byte array containing incoming data to be appended to the parser's buffer. |
     | length | Int | Number of bytes from 'bytes' to write into the buffer starting at index 0. |
   - **Returns description**
     - Unit. The function has no return value; it emits parsed messages via the onMessageParsed callback.

5. **processLines**
   - Category: function, processing
   - Lines: 28-63
   - **Description**
     - Reads the current content of the internal buffer, scans for LF ('\n') bytes to identify complete lines, decodes each line as UTF-8 (removing any trailing CR), and emits ClassicMessage.Text for non-empty decoded lines via onMessageParsed.
     - On UTF-8 decoding failure for a line, it emits ClassicMessage.ParseError with reason 'Invalid UTF-8' and includes the raw bytes for that line. After processing it resets the buffer and retains any unprocessed trailing bytes (partial line) by copying them back into the buffer.
   - **Parameters description**
     - No parameters; uses the internal buffer state to extract and handle complete lines.
   - **Returns description**
     - Unit. Emits parsed messages or errors via the onMessageParsed callback; leaves incomplete trailing bytes in the internal buffer for later feeds.

6. **reset**
   - Category: function, lifecycle
   - Lines: 65-67
   - **Description**
     - Clears the internal buffer, discarding any accumulated but unprocessed bytes.
     - Provides a simple way to reinitialize the parser state without creating a new instance.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - Unit. No return value; side-effect is buffer reset.


**Code Walkthroughs**
1. **Lines:** 13-23
   - **What it does**
     - Checks whether appending the incoming bytes would exceed the configured maximum buffer size and handles overflow by dropping buffered bytes and emitting a ParseError.
   - **Why it matters**
     - This overflow handling is critical to prevent unbounded memory growth and defines the parser's behavior under backpressure; it also deliberately drops existing buffered data and emits an error which consumers must handle.

2. **Lines:** 32-38
   - **What it does**
     - Implements a manual linear scan for the next newline byte (LF) starting from the current cursor position; when found, loop breaks and sets newlineIndex.
   - **Why it matters**
     - Manual byte-level scanning is central to correctly extracting newline-terminated frames from a raw byte array and avoids allocations per byte; this is the core of line detection.

3. **Lines:** 40-46
   - **What it does**
     - Copies the bytes for the found line, decodes them as UTF-8, trims a trailing CR if present, and emits a ClassicMessage.Text if the resulting string is non-empty.
   - **Why it matters**
     - Conversion from raw bytes to text and trimming CR handles both LF and CRLF line endings; the non-empty check prevents emitting empty-message events for blank lines.

4. **Lines:** 48-55
   - **What it does**
     - Catches MalformedInputException thrown during decoding and emits a ClassicMessage.ParseError containing the offending raw bytes and an 'Invalid UTF-8' reason.
   - **Why it matters**
     - Explicit error emission for decoding failures ensures that consumers are notified about invalid byte sequences and receive the raw bytes for diagnostics or logging.

5. **Lines:** 58-62
   - **What it does**
     - After processing complete lines, the buffer is reset and any trailing bytes that were not part of a complete line are written back into the buffer so they can be completed by future feed() calls.
   - **Why it matters**
     - Retaining the incomplete tail between feeds preserves message boundaries across partial reads from the transport layer.


**Style Conventions**
1. **Lines:** 35-36
   - **Guideline**
     - Single-line if with semicolon-style or brace-free body is used for the newline detection (inline break).
     - Use of explicit byte comparisons via '\n'.code.toByte() makes the intended delimiter explicit and portable.
   - **Rationale**
     - The concise control flow is idiomatic Kotlin, but the compact style may reduce immediate readability for newcomers.

2. **Lines:** 10-10
   - **Guideline**
     - Internal buffer is constructed with ByteArrayOutputStream(512), providing an initial capacity hint rather than leaving default.
     - Properties and methods follow Kotlin naming conventions and leverage nullable function-typed property for the callback.
   - **Rationale**
     - Initial buffer sizing and naming are explicit and consistent with common Kotlin code conventions.


**Event Handling**
1. **onMessageParsed callback**
   - Lines: 9-63
   - **Trigger Type:** Internal parser logic
   - **Behavior**
     - onMessageParsed is a nullable callback set by consumers to receive parsed ClassicMessage instances: ClassicMessage.Text for successfully decoded non-empty lines, and ClassicMessage.ParseError for buffer overflows or invalid UTF-8 sequences.
     - Invocations occur in three logical situations: when a buffer overflow is detected (lines 13-23), for each successfully decoded line (lines 43-46), and when a line fails UTF-8 decoding (lines 48-55). The parser never returns values directly but uses this callback for all outputs.
   - **Impact**
     - Triggers downstream processing or error handling in consumer code; represents the sole mechanism by which parsing results are communicated.
