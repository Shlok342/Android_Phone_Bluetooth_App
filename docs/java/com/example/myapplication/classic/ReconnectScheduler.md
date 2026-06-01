**FileName:** ReconnectScheduler.kt   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/java/com/example/myapplication/classic/ReconnectScheduler.kt   
**Tags:** bluetooth, reconnect, coroutines, concurrency, state-management   

**File Summary**
ReconnectScheduler is a small Kotlin utility class that coordinates retrying Bluetooth connections using coroutines. It tracks attempt counts and consecutive failures, applies cooldown logic after repeated failure bursts, and schedules reconnect attempts with simple backoff delays while invoking provided callbacks to update state, log events, and perform the actual connect action.

**Function Summaries**
1. **ReconnectScheduler (constructor & fields)**
   - Category: Class, Constructor, State
   - Lines: 11-28
   - **Description**
     - Defines the ReconnectScheduler class which manages reconnect attempts for a BluetoothDevice using a CoroutineScope.
     - Initializes configuration (maxAttempts) and callback functions (intentional disconnect check, state updates, logging, and connect action).
     - Declares internal state: a coroutine Job for the current reconnect attempt, two AtomicInteger counters (total attempts and consecutive failures), and a volatile timestamp for the last failure.
   - **Parameters description**
     - Constructor parameters provide runtime behavior (scope), limits (maxAttempts), and callback hooks for checking intentional disconnects, updating external state, logging, and performing the connect.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | scope | CoroutineScope | CoroutineScope used to launch reconnect coroutines so scheduling and cancellation occur within the caller's coroutine context. |
     | maxAttempts | Int | Maximum number of reconnect attempts allowed before reporting a failed state. |
     | getIsIntentionalDisconnect | () -> Boolean | Callback used to determine if the disconnection was intentional; if true, reconnect attempts are suppressed. |
     | onUpdateState | (ClassicState) -> Unit | Callback invoked to publish state transitions (e.g., RECONNECTING, FAILED) to the rest of the system. |
     | onLogEvent | (String) -> Unit | Callback used to emit log messages related to reconnect attempts. |
     | onDoConnect | (BluetoothDevice) -> Unit | Callback invoked to perform the actual Bluetooth connect operation for a given device. |
   - **Returns description**
     - No return value; class initialization.

2. **companion object**
   - Category: Constants
   - Lines: 19-21
   - **Description**
     - Holds a constant FAILURE_COOLDOWN_MS which determines the cooldown interval (60 seconds) after repeated failure bursts.

3. **reconnectAttempts (property)**
   - Category: Property, Getter
   - Lines: 28-28
   - **Description**
     - Exposes the current total reconnect attempts as an Int by reading the AtomicInteger backing field.
   - **Returns description**
     - An Int reflecting the value of the internal AtomicInteger counter.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | reconnectAttempts | Int | Current number of reconnect attempts that have been scheduled/executed. |

4. **handleConnectionFailure**
   - Category: Function
   - Lines: 30-34
   - **Description**
     - Called when a connection failure occurs. It increments the consecutive failure counter, updates lastFailureTime, and triggers scheduling of a reconnect for the given device.
   - **Parameters description**
     - Takes a BluetoothDevice that represents the device to be reconnected; no return value.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | device | BluetoothDevice | The Bluetooth device that failed to connect and should be scheduled for reconnect. |
   - **Returns description**
     - Unit (no return).

5. **scheduleReconnect**
   - Category: Function, Scheduler, Coroutine
   - Lines: 35-81
   - **Description**
     - Schedules a reconnect attempt for a BluetoothDevice unless prevented by conditions: an intentional disconnect, an active reconnect job, or exceeding max attempts.
     - Implements failure-burst cooldown logic by comparing consecutiveFailures against a RECONNECT_MAX_ATTEMPTS threshold and the lastFailureTime versus FAILURE_COOLDOWN_MS.
     - When scheduling, launches a coroutine that waits a short initial delay, increments the global attempt counter, computes a small backoff based on attempt number, updates state to RECONNECTING, logs, waits the backoff, and then invokes the connect callback if the disconnect is still unintentional and the coroutine is active.
   - **Parameters description**
     - Accepts a BluetoothDevice to connect; interacts with internal counters and uses provided callbacks to drive state/logging/connection.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | device | BluetoothDevice | The device to attempt reconnection to. |
   - **Returns description**
     - Unit (no direct return).

6. **cancelReconnect**
   - Category: Function
   - Lines: 83-86
   - **Description**
     - Cancels any active reconnect coroutine job and clears the job reference so no reconnect is pending.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - Unit (no return).

7. **reset**
   - Category: Function
   - Lines: 88-91
   - **Description**
     - Resets the total reconnect attempts and consecutive failure counters to zero. Does not cancel running reconnect jobs or modify lastFailureTime.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - Unit (no return).


**Code Walkthroughs**
1. **Lines:** 24-26
   - **What it does**
     - Uses AtomicInteger for thread-safe incrementing of counters and a @Volatile long for lastFailureTime to ensure visibility across threads/coroutines.
   - **Why it matters**
     - AtomicInteger ensures increments are atomic across concurrent coroutines; volatile ensures lastFailureTime updates are visible without synchronization.

2. **Lines:** 36-38
   - **What it does**
     - Early-return guards: suppress reconnect if disconnection is intentional, or if a reconnect coroutine is already active.
   - **Why it matters**
     - Prevents redundant reconnect scheduling and respects explicit user-initiated disconnects.

3. **Lines:** 38-49
   - **What it does**
     - Checks the global reconnectAttempts against maxAttempts and, if exceeded, publishes a FAILED state with FailureReason.MaxReconnectAttempts and returns.
   - **Why it matters**
     - Limits total reconnection attempts to the configured max to avoid infinite retry loops and informs the rest of the system of failure.

4. **Lines:** 51-64
   - **What it does**
     - Implements a cooldown after bursts of consecutive failures by comparing consecutiveFailures against a threshold defined in ClassicConnectionManager and ensuring enough time has passed since last failure.
   - **Why it matters**
     - Prevents rapid repeated reconnection attempts after continuous failures, enforcing a cooldown window to reduce resource churn.

5. **Lines:** 68-80
   - **What it does**
     - Coroutine body: initial short delay, increment attempt counter, choose backoff (800ms, 1600ms, or 3000ms), update state to RECONNECTING, log the attempt, wait the backoff, and call the provided connect callback if still appropriate.
   - **Why it matters**
     - Combines scheduling, state reporting, simple backoff strategy, and safety checks (intentional disconnect and coroutine isActive) before calling the connect action.

6. **Lines:** 70-75
   - **What it does**
     - Backoff selection logic: attempt 1 => 800ms; attempt 2 => 1600ms; attempt 3+ => 3000ms.
   - **Why it matters**
     - Implements a small, discrete backoff schedule for successive attempts to space out retries progressively.

7. **Lines:** 76-79
   - **What it does**
     - State update and logging use the current attempt counter; the final connect invocation is gated by getIsIntentionalDisconnect() and coroutine isActive.
   - **Why it matters**
     - Ensures the system is notified of reconnect progress and avoids performing a connect if the disconnect becomes intentional or the coroutine is cancelled.


**Style Conventions**
1. **Lines:** 36-41
   - **Guideline**
     - There are some spacing/formatting inconsistencies (e.g., 'if (getIsIntentionalDisconnect())         return' and line breaks around the maxAttempts check).
   - **Rationale**
     - Formatting inconsistencies can reduce readability, but logic is still clear.

2. **Lines:** 19-21
   - **Guideline**
     - Constants are grouped in a companion object which is idiomatic Kotlin for static-like constants.
   - **Rationale**
     - Keeps configuration values centralized and accessible without instance state.


**Event Handling**
1. **Reconnect scheduling on failure**
   - Lines: 30-81
   - **Trigger Type:** Internal method calls (e.g., invoked by connection manager when connection fails)
   - **Behavior**
     - handleConnectionFailure increments failure counters and delegates to scheduleReconnect to potentially enqueue a reconnect coroutine.
     - scheduleReconnect enforces guards and cooldowns, then launches a coroutine that eventually triggers onDoConnect if conditions remain appropriate.
   - **Impact**
     - Triggers state transitions (RECONNECTING or FAILED) and may trigger onDoConnect which initiates an external side-effect (attempting a Bluetooth connection).
