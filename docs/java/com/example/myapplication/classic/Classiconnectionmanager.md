**FileName:** Classiconnectionmanager.kt   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/java/com/example/myapplication/classic/Classiconnectionmanager.kt   
**Tags:** bluetooth, connectivity, coroutines, io, messaging   

**File Summary**
ClassicConnectionManager is a Kotlin class that manages a classic (RFCOMM) Bluetooth connection lifecycle for the Android app. It maintains connection state via StateFlow/SharedFlow, handles connect/disconnect, read/write loops, timeouts, reconnection scheduling, and exposes parsed messages and raw byte streams for consumers (e.g., file transfer manager). The file contains flow-based event/message channels, coroutine-based timeouts and loops, and integrates with several internal components (parser, write queue, socket factory, reconnect scheduler).

**Function Summaries**
1. **ConnectionInfo**
   - Category: data class
   - Lines: 21-25
   - **Description**
     - Simple data holder representing current connection state plus device address and name.
     - Used to expose combined connection metadata via a StateFlow for UI or other consumers.
   - **Parameters description**
     - Holds the current ClassicState and optional device address and device name strings.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | state | ClassicState | Current high-level state of the connection (idle/connecting/connected/etc.). |
     | address | String | Bluetooth MAC address of the connected device, empty string if none. |
     | deviceName | String | Human-readable device name, empty string if none. |
   - **Returns description**
     - Instantiates and holds connection metadata for consumers.

2. **ClassicConnectionManager (class)**
   - Category: class, connection manager
   - Lines: 27-385
   - **Description**
     - Primary manager that controls Bluetooth RFCOMM socket lifecycle, read/write, timeouts, and reconnect logic.
     - Exposes StateFlow for connection state, SharedFlows for parsed messages, raw bytes, and textual events; coordinates parser, write queue, and reconnect scheduler to maintain a robust connection.
   - **Parameters description**
     - Constructed with an Android Context used for permission checks and accessing Bluetooth system services.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | appContext | Context | Application context used for permission checks and cancelling discovery via the BluetoothManager. |
   - **Returns description**
     - Provides an object that can be used to connect to BluetoothDevice(s), send data, receive messages, and manage disconnection/reconnect behavior.

3. **companion object**
   - Category: constants, static helper
   - Lines: 30-49
   - **Description**
     - Defines constants used for timeouts (connection/read/write) and max reconnect attempts.
     - Provides cancelDiscovery(context) helper to safely stop Bluetooth discovery with permission checks.
   - **Parameters description**
     - cancelDiscovery accepts a Context parameter to access BluetoothManager and adapter.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | context | Context | Context used to retrieve BluetoothManager and perform permission-guarded adapter operations. |
   - **Returns description**
     - cancelDiscovery performs side effects (cancels discovery if permitted); the companion object also exposes constants.

4. **state flows and message flows initialization**
   - Category: state management, flows
   - Lines: 54-90
   - **Description**
     - Initializes MutableStateFlow for connection state and connectionInfo, and MutableSharedFlow channels for parsed messages, events, and raw bytes.
     - Flow configurations include buffer sizes and overflow strategies to avoid backpressure blocking producers.
   - **Parameters description**
     - No external parameters — internal fields used by class methods to publish state and data.
   - **Returns description**
     - Fields provide reactive streams (state, connectionInfo, messages, events, rawBytes) for external consumers.

5. **transfer mode flag**
   - Category: state flag
   - Lines: 91-99
   - **Description**
     - Tracks whether the manager is in 'transfer mode' (raw byte streaming) which disables message parsing.
     - logEvent helper formats events with local time and emits to the events SharedFlow.
   - **Parameters description**
     - setTransferMode toggles boolean flag isTransferMode; logEvent takes a message string.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | enabled | Boolean | If true, switch to transfer mode (parser disabled); if false, parsing is enabled. |
   - **Returns description**
     - Setters are side-effecting; logEvent emits formatted events to _events SharedFlow.

6. **Sockets / Streams fields**
   - Category: io resources
   - Lines: 101-104
   - **Description**
     - Holds references to the current BluetoothSocket and its input/output streams. These are set when a connection is established and cleared on disconnect.
   - **Parameters description**
     - No parameters; fields are mutated by connect/disconnect code paths.
   - **Returns description**
     - Fields represent active I/O channels for reading/writing bytes to the remote device.

7. **parser and write queue fields**
   - Category: parser, write queuing
   - Lines: 106-108
   - **Description**
     - Initializes the message parser implementation (newline-based) and declares a nullable write queue used to serialize writes and enforce write timeouts.
   - **Parameters description**
     - No function parameters. The parser implementation and writeQueue field are used by connection lifecycle code.
   - **Returns description**
     - Provides parsed message callbacks and enqueued write operations when active.

8. **coroutine jobs and reconnect scheduler setup**
   - Category: concurrency, reconnection
   - Lines: 110-123
   - **Description**
     - Creates a CoroutineScope with IO dispatcher to run connection tasks, and constructs a ReconnectScheduler which coordinates reconnect attempts, state updates, and logs.
     - Declares Job references used to manage connect/read/timeout coroutines.
   - **Parameters description**
     - ReconnectScheduler is constructed with callbacks to this manager: getIsIntentionalDisconnect, onUpdateState, onLogEvent, onDoConnect.
   - **Returns description**
     - Sets up the asynchronous environment and exposes reconnectAttempts as a read-only property.

9. **reconnect state fields**
   - Category: state tracking
   - Lines: 127-129
   - **Description**
     - Tracks the last attempted BluetoothDevice to support scheduled reconnects and a flag marking whether a disconnect was intentional (to avoid automatic reconnects).
   - **Parameters description**
     - No parameters; fields modified by connect/disconnect flows.
   - **Returns description**
     - Used to decide whether reconnectScheduler should attempt reconnections.

10. **lastReadTime**
   - Category: read state
   - Lines: 135-135
   - **Description**
     - Volatile timestamp updated when bytes are read; used to detect read inactivity and trigger timeouts.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - Single long field representing epoch millis of last successful read.

11. **updateState**
   - Category: state helper
   - Lines: 142-151
   - **Description**
     - Atomically updates internal _state and also refreshes _connectionInfo to keep the connection metadata consistent with the state.
     - Simplifies state transitions by centralizing the two flows update logic.
   - **Parameters description**
     - Accepts a ClassicState representing the new connection state.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | state | ClassicState | New state to set on the manager and connection info flow. |
   - **Returns description**
     - Performs side effects by mutating flows; no return value.

12. **forceDisconnect**
   - Category: disconnect helper
   - Lines: 153-168
   - **Description**
     - Logs the disconnect (or failure reason), performs internal cleanup via disconnectInternal(), and updates the state to DISCONNECTED or FAILED(reason).
     - Used to centralize failure handling and ensure consistent state after unexpected disconnects.
   - **Parameters description**
     - Optional FailureReason parameter to indicate a failure cause.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | reason | FailureReason? | Optional reason enumerated to mark the failure state. If null, treated as a normal disconnect. |
   - **Returns description**
     - Performs side effects; no return value.

13. **connect**
   - Category: public API, synchronized
   - Lines: 170-177
   - **Description**
     - Public synchronized API to initiate a manual connection to a given BluetoothDevice. Resets reconnect scheduling and delegates to doConnect.
     - Marks disconnect as unintentional to enable reconnect behavior on failures.
   - **Parameters description**
     - Takes a BluetoothDevice to connect to and starts the connection flow asynchronously.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | device | BluetoothDevice | Target remote Bluetooth device to which to connect. |
   - **Returns description**
     - Triggers asynchronous connection and returns immediately (Unit).

14. **doConnect**
   - Category: internal connect
   - Lines: 179-224
   - **Description**
     - Performs the actual connection work inside a coroutine: avoids duplicating if already connecting/connected, cancels existing resources, sets metadata, starts connection timeout, checks runtime permission, cancels discovery, waits, optionally waits for bonding, creates socket via SocketFactory, wires streams, and calls onConnected on success.
     - On exceptions, cancels timeout and delegates reconnect handling to reconnectScheduler if appropriate.
   - **Parameters description**
     - Receives a BluetoothDevice and performs async connection lifecycle actions using managerScope.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | device | BluetoothDevice | Device to connect to. |
   - **Returns description**
     - No return; side-effectful — sets up socket/streams and triggers onConnected on success, or schedules reconnect on failure.

15. **onConnected**
   - Category: connected path setup
   - Lines: 230-254
   - **Description**
     - Called once a socket is established. Logs event, cancels connection timeout, resets reconnect scheduler, initializes parser callback to emit parsed messages, starts WriteQueue (with error callback that forces disconnect and schedules reconnect), updates state to CONNECTED, and starts the read and inactivity timeout loops.
     - Initializes lastReadTime to current time to seed inactivity checks.
   - **Parameters description**
     - No parameters; operates on fields set by doConnect (streams, socket, metadata).
   - **Returns description**
     - Side-effectful; no return value.

16. **startConnectionTimeout**
   - Category: timeout coroutine
   - Lines: 256-270
   - **Description**
     - Starts a coroutine that delays for CONNECTION_TIMEOUT_MS then logs and, if still in CONNECTING state, cleans up and asks reconnectScheduler to handle failure.
     - Ensures that a stuck connection attempt doesn't hang indefinitely.
   - **Parameters description**
     - No explicit parameters; uses lastDevice and internal state to decide actions.
   - **Returns description**
     - Starts/cancels internal Job; no return value.

17. **cancelConnectionTimeout**
   - Category: timeout helper
   - Lines: 272-275
   - **Description**
     - Cancels and clears the connection timeout job if it exists.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - Side-effectful; no return value.

18. **startInactivityTimeout**
   - Category: timeout coroutine
   - Lines: 277-290
   - **Description**
     - Starts a repeating check that periodically verifies read activity. If the elapsed time since lastReadTime exceeds READ_INACTIVITY_MS, forceDisconnect is invoked and reconnect is scheduled.
     - Runs only while state is CONNECTED and uses periodic delay of READ_INACTIVITY_CHECK_MS to reduce overhead.
   - **Parameters description**
     - No parameters; uses lastReadTime and reconnectScheduler/lastDevice fields.
   - **Returns description**
     - Starts/cancels internal job; no return value.

19. **startReading**
   - Category: read loop
   - Lines: 292-314
   - **Description**
     - Launches a coroutine that continuously reads from the socket inputStream into a buffer while connected. On successful reads, updates lastReadTime, emits raw bytes through _rawBytes, and feeds the parser with data unless in transfer mode. On IOException, triggers disconnect and reconnect if appropriate.
     - Performs a buffer.copyOfRange to produce a correctly-sized byte array for emission.
   - **Parameters description**
     - No parameters; relies on inputStream and state flows.
   - **Returns description**
     - Runs asynchronously; no return value.

20. **sendData**
   - Category: write API
   - Lines: 316-320
   - **Description**
     - Public method to enqueue byte array writes into the WriteQueue. Accepts an optional callback for write result and returns Boolean indicating whether enqueue succeeded.
     - Encapsulates write queuing and timeout behavior provided by the WriteQueue implementation.
   - **Parameters description**
     - Accepts a ByteArray to send and an optional callback to receive the WriteResult.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | data | ByteArray | Payload to send over the Bluetooth socket. |
     | onResult | (WriteQueue.WriteResult) -> Unit ? | Optional callback invoked with the result of the write operation. |
   - **Returns description**
     - Boolean indicating whether the data was successfully enqueued; false if no active writeQueue.

21. **disconnect**
   - Category: public API
   - Lines: 325-332
   - **Description**
     - Public synchronized method to manually disconnect: marks the disconnect as intentional, cancels scheduled reconnects and discovery, then forces a disconnect to clean up resources and update state.
     - Prevents reconnectScheduler from automatically reconnecting after a user-requested disconnect.
   - **Parameters description**
     - No parameters required.
   - **Returns description**
     - Side-effectful (Unit).

22. **destroy**
   - Category: lifecycle cleanup
   - Lines: 334-338
   - **Description**
     - Teardown method intended to be called when manager is no longer needed: marks disconnect as intentional, performs internal disconnect cleanup, and cancels the manager coroutine scope to stop background jobs.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - Performs cleanup and cancels coroutines; no return value.

23. **disconnectInternal**
   - Category: resource cleanup
   - Lines: 340-361
   - **Description**
     - Internal cleanup routine that cancels timeouts, closes socket and streams (with IO exception suppression), cancels running jobs, clears metadata fields, stops the write queue, and resets the parser.
     - Used by forceDisconnect, destroy, and in some failure code paths to ensure resources are released safely.
   - **Parameters description**
     - No parameters; operates on instance fields.
   - **Returns description**
     - Side-effectful cleanup; no return value.

24. **hasConnectPermission**
   - Category: permission helper
   - Lines: 363-369
   - **Description**
     - Checks runtime permission BLUETOOTH_CONNECT for Android S+; returns true for older platform versions where the permission is not required.
     - Used to gate operations that require BLUETOOTH_CONNECT at runtime (fetching device name, opening sockets).
   - **Parameters description**
     - No parameters; uses appContext field.
   - **Returns description**
     - Boolean — true if connection-related permission is granted or not required for current platform version.

25. **resolveAddress**
   - Category: device helper
   - Lines: 373-373
   - **Description**
     - Returns the BluetoothDevice.address directly; lightweight helper to isolate access.
   - **Parameters description**
     - Takes a BluetoothDevice and returns its address string.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | device | BluetoothDevice | Source device from which to read .address. |
   - **Returns description**
     - String containing the device address.

26. **resolveDeviceName**
   - Category: device helper, permission-guarded
   - Lines: 375-377
   - **Description**
     - Safely obtains the device name if BLUETOOTH_CONNECT permission is available; otherwise returns "Unknown". Catches SecurityException to avoid crashing if permission state changes during execution.
     - Used to populate connectionInfo.deviceName.
   - **Parameters description**
     - Takes a BluetoothDevice and returns an appropriate display name string.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | device | BluetoothDevice | Device instance to query for name. |
   - **Returns description**
     - String representing the device name or 'Unknown' when permission is missing/unavailable.

27. **isConnected**
   - Category: query helper
   - Lines: 379-379
   - **Description**
     - Convenience method returning whether the current state is CONNECTED.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - Boolean true if connected, else false.

28. **resetToIdle**
   - Category: state helper
   - Lines: 380-384
   - **Description**
     - If the manager is in DISCONNECTED or FAILED state, reset top-level state back to IDLE. Intended to be used to enable retry flows from UI or higher-level controller after a terminal disconnect/failure.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - Side-effectful; no return value.


**Configuration References**
1. **Manifest.permission.BLUETOOTH_SCAN**
   - Line: 42,43,44
   - **What it does:**
     - Checked before calling adapter.cancelDiscovery() to ensure scanning-related operations are allowed on Android S+.
     - If not granted on Android S+, cancelDiscovery is skipped to avoid SecurityException.
   - **Default value**
     - N/A

2. **Manifest.permission.BLUETOOTH_CONNECT**
   - Line: 365,366,367,368,376
   - **What it does:**
     - Checked at runtime on Android S+ before performing operations that require BLUETOOTH_CONNECT (creating sockets, getting device name).
     - Used to guard functionality and return safe defaults when the permission is not present.
   - **Default value**
     - N/A


**Code Walkthroughs**
1. **Lines:** 38-46
   - **What it does**
     - Performs permission-guarded cancellation of Bluetooth discovery using the system BluetoothManager/adapter.
     - Uses SDK version check to decide whether BLUETOOTH_SCAN permission must be explicitly checked before calling adapter.cancelDiscovery().
   - **Why it matters**
     - Permission behavior differs across Android versions; this code carefully avoids SecurityException by checking runtime permission for SDK S+ and using nullable casts to guard against missing services.

2. **Lines:** 111-118
   - **What it does**
     - Instantiates ReconnectScheduler with callbacks into this manager for state updates, logging, and performing a connection attempt.
     - Provides a getIsIntentionalDisconnect lambda so the scheduler can avoid reconnects when a user intentionally disconnected.
   - **Why it matters**
     - ReconnectScheduler is a central coordinator for retry logic; understanding the callbacks provided here is necessary to follow reconnect flows.

3. **Lines:** 236-239
   - **What it does**
     - Assigns parser.onMessageParsed to emit parsed messages into the _messages SharedFlow.
     - Enables parsed-message consumers to receive high-level ClassicMessage objects produced by the parser.
   - **Why it matters**
     - This is a key bridge between low-level byte parsing and higher-level message consumers; modifying the parser callback affects message delivery.

4. **Lines:** 241-249
   - **What it does**
     - Initializes WriteQueue with a write timeout and an onWriteError callback that forces a disconnect and schedules reconnect when a write fails while connected.
     - Starts the write queue providing a supplier that returns outputStream to be used for actual writes.
   - **Why it matters**
     - WriteQueue handles write serialization and timeouts; its error callback directly triggers connection failure and reconnect behavior so changes here alter resiliency.

5. **Lines:** 294-303
   - **What it does**
     - Read loop reads into a fixed 1024-byte buffer; on positive byte count it updates lastReadTime, emits a correctly sized array to rawBytes, and conditionally feeds the parser when not in transfer mode.
     - Handles IOException to gracefully disconnect and schedule reconnects if appropriate.
   - **Why it matters**
     - Key I/O loop: buffer reuse and copying behavior, conditional parser feeding (transfer mode), and error handling determine throughput and reconnection triggers.

6. **Lines:** 282-286
   - **What it does**
     - Inactivity check compares current time to lastReadTime; if exceeded threshold, the manager forces a disconnect and schedules a reconnect.
     - Loop uses periodic checks rather than per-read timers to reduce overhead.
   - **Why it matters**
     - Inactivity detection is critical for reliability; it's implemented using a while loop with periodic sleep, which affects detection latency and resource usage.

7. **Lines:** 262-266
   - **What it does**
     - After connection timeout delay, if still CONNECTING, silently cleans up and asks reconnectScheduler to handle the connection failure for the last device.
     - Avoids leaving partially created resources when a connection attempt takes too long.
   - **Why it matters**
     - Timeout handling ensures connection attempts don't hang indefinitely and informs reconnect logic to retry.

8. **Lines:** 342-361
   - **What it does**
     - disconnectInternal performs thorough cleanup: cancels timeouts, closes socket/streams with exception suppression, cancels background jobs, clears metadata and stops writeQueue and parser.
     - Resets the object to a clean state ready for a new connection attempt.
   - **Why it matters**
     - Resource cleanup is often a source of leaks; this block is comprehensive and central to correct lifecycle management.


**Style Conventions**
1. **Lines:** 54-61
   - **Guideline**
     - Private mutable flows use leading underscore naming convention (_state, _messages) and public immutable views expose StateFlow/SharedFlow without underscore. This matches common Kotlin style for encapsulating mutable state.
   - **Rationale**
     - Provides clear separation between internal mutation and external read-only access, improving maintainability.

2. **Lines:** 171-177
   - **Guideline**
     - Synchronized methods (connect, disconnect) are used to avoid concurrent modifications to connection lifecycle. @Synchronized ensures single-threaded entry for these operations.
   - **Rationale**
     - Synchronized access reduces concurrency bugs for lifecycle operations that manipulate shared state.

3. **Lines:** 44-47
   - **Guideline**
     - Several try/catch blocks suppress exceptions (catch (_: Exception) {}) to avoid crashes; this is a defensive style but hides error details from logs. Some catch blocks do printStackTrace() while others do not.
   - **Rationale**
     - Consistency in error handling is notable — silent suppression is used in places and explicit logging in others; understanding this is important for debugging.


**Event Handling**
1. **parser -> messages emission**
   - Lines: 236-239
   - **Trigger Type:** MessageParser (internal)
   - **Behavior**
     - When parser parses a complete message it invokes onMessageParsed which emits a ClassicMessage into the _messages SharedFlow. This decouples byte-level reads from high-level message consumers.
     - Consumers subscribe to messages SharedFlow to process incoming protocol-level messages; emission is non-blocking (tryEmit) and messages may be dropped if flow buffer is full.
   - **Impact**
     - Delivers parsed messages to UI/logic; dropped messages could cause missed events if consumers are slow.

2. **reconnect scheduler callbacks**
   - Lines: 111-118
   - **Trigger Type:** ReconnectScheduler (internal)
   - **Behavior**
     - ReconnectScheduler triggers doConnect when scheduling reconnects and uses onUpdateState and onLogEvent to modify manager state and log events. It also reads isIntentionalDisconnect to avoid scheduling reconnects after manual disconnects.
     - This wiring lets reconnect logic run independently but still control connection lifecycle through provided callbacks.
   - **Impact**
     - Controls automated reconnect behavior after failures and interacts with state flow for observability.
