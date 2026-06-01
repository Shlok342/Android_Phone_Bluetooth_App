**FileName:** SystemTimeLine.kt   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/java/com/example/myapplication/util/SystemTimeLine.kt   
**Tags:** utility, logging, thread-safety, kotlin, time   

**File Summary**
Defines a small, thread-safe in-memory timeline logger (SystemTimeline) that records timestamped messages up to a fixed capacity. Provides a TimelineEvent data class with a formatted timestamp string, and public functions to log events, retrieve a snapshot list of events, and clear the timeline. Uses synchronization to protect the internal deque and Java date/time utilities for formatting.

**Function Summaries**
1. **SystemTimeline (object)**
   - Category: Singleton, Utility
   - Lines: 7-33
   - **Description**
     - Acts as a singleton utility for recording and holding a bounded list of timestamped events in memory.
     - Exposes functions to log new events, get a snapshot list of events, and clear stored events, while ensuring thread-safety.
   - **Parameters description**
     - No parameters; this is an object/singleton that provides utility functions.
   - **Returns description**
     - N/A for the object itself.

2. **MAX_EVENTS**
   - Category: Constant
   - Lines: 9-9
   - **Description**
     - Defines the upper limit for stored timeline events (300).
     - Used to trim oldest events when capacity is exceeded to bound memory usage.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - N/A.

3. **TimelineEvent**
   - Category: Data class, Model
   - Lines: 11-19
   - **Description**
     - Represents a single timeline entry containing a timestamp (milliseconds since epoch) and a message.
     - Provides a computed 'formatted' property that returns a human-readable time string combined with the message.
   - **Parameters description**
     - Two constructor parameters: a timestamp in milliseconds (optional, defaults to current time) and a message string.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | timestampMs | Long | Unix time in milliseconds for the event. If not provided, defaults to the current system time when the instance is constructed. |
     | message | String | The textual message describing the event; included in the formatted output. |
   - **Returns description**
     - Instances of TimelineEvent; also exposes a 'formatted' property (String) that combines a formatted time and message.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | TimelineEvent | TimelineEvent | Constructed model object representing the logged event. |
     | formatted | String | Computed value that formats timestampMs using a 12-hour clock with AM/PM and appends the message. |

4. **_events**
   - Category: Private storage, ArrayDeque
   - Lines: 21-21
   - **Description**
     - Holds the in-memory deque of TimelineEvent objects with newest events at the front.
     - Access to this structure is synchronized to ensure thread-safety.
   - **Parameters description**
     - N/A.
   - **Returns description**
     - N/A.

5. **log**
   - Category: Function, Mutator
   - Lines: 23-28
   - **Description**
     - Adds a new TimelineEvent containing the provided message to the head of the deque, using the current time as timestamp.
     - If adding the event exceeds MAX_EVENTS, removes the oldest event from the tail to maintain the fixed capacity. The operation is synchronized on the internal deque.
   - **Parameters description**
     - Accepts a message string to create and store a new TimelineEvent.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | message | String | The message to record in the timeline event. |
   - **Returns description**
     - Unit (no return).

6. **getEvents**
   - Category: Function, Accessor
   - Lines: 30-30
   - **Description**
     - Returns a thread-safe snapshot List of stored TimelineEvent objects in current deque order (newest first).
     - The conversion to a List is performed inside a synchronized block to avoid concurrent-modification issues.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - A List<TimelineEvent> representing a snapshot of events.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | List<TimelineEvent> | List | A snapshot list of events (newest first) copied from the internal deque. |

7. **clear**
   - Category: Function, Mutator
   - Lines: 32-32
   - **Description**
     - Clears all stored timeline events from the internal deque. The clear operation is synchronized for thread-safety.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - Unit (no return).


**Code Walkthroughs**
1. **Lines:** 15-17
   - **What it does**
     - Formatted computed property builds a readable string by formatting the timestamp and appending the message using SimpleDateFormat with pattern "hh:mm:ss a".
   - **Why it matters**
     - Formatting is done by creating a new SimpleDateFormat instance per access to avoid shared-state/thread-safety issues with SimpleDateFormat; this also affects performance and locale-awareness.

2. **Lines:** 24-27
   - **What it does**
     - Synchronized block around mutation operations ensures that adding an event and conditionally removing the last event occur atomically with respect to other threads.
   - **Why it matters**
     - Locking on the private _events object prevents concurrent modification and maintains consistency of size trimming logic when MAX_EVENTS is exceeded.

3. **Lines:** 30-30
   - **What it does**
     - Synchronized snapshotting converts the deque to a list inside a lock to ensure the returned list is a consistent view at the moment of the call.
   - **Why it matters**
     - Without synchronization, concurrent mutations could cause inconsistent or partially updated snapshots, or ConcurrentModificationExceptions during iteration.

4. **Lines:** 12-12
   - **What it does**
     - Default value for timestampMs uses System.currentTimeMillis() so that when a caller omits the timestamp, the creation time of the TimelineEvent is recorded automatically.
   - **Why it matters**
     - This default expression is evaluated at construction time for instances where timestampMs is not explicitly provided.


**Style Conventions**
1. **Lines:** 1-7
   - **Guideline**
     - Kotlin top-level package declaration and object declaration follow standard conventions: package at top, then imports, then an object singleton named SystemTimeline.
     - Constants use uppercase with underscores (MAX_EVENTS).
   - **Rationale**
     - Consistent naming and structure improve readability and follow common Kotlin style.

2. **Lines:** 0-0
   - **Guideline**
     - File name provided in input (SystemTimeLine.kt) differs in capitalization from the object name (SystemTimeline).
   - **Rationale**
     - File-to-type name alignment typically helps navigation and clarity; the difference is factual and may be notable during file lookup or IDE navigation.

3. **Lines:** 23-32
   - **Guideline**
     - Small single-expression functions (getEvents, clear) are used where appropriate, and synchronized blocks are used for concise thread-safe operations.
   - **Rationale**
     - This pattern keeps the public API minimal and operations atomic.
