**FileName:** FileTransferState.kt   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/java/com/example/myapplication/classic/FileTransferState.kt   
**Tags:** model, state, file-transfer, bluetooth, kotlin   

**File Summary**
Defines a concise set of types representing the states of a file transfer in the Classic (non-LE) Bluetooth portion of the app. It includes an enum for transfer direction and a sealed class with variants for Idle, Sending, Receiving, Done, Failed, and Cancelled; Sending and Receiving expose a derived progress property. This file serves as the canonical state model used by UI and transfer logic to represent and observe file transfer progress and outcomes.

**Function Summaries**
1. **TransferDirection**
   - Category: enum
   - Lines: 3-6
   - **Description**
     - Represents the direction of a file transfer — SEND or RECEIVE.
     - Used by state objects (e.g., Done and Failed) to indicate whether a completed or failed transfer was outbound or inbound.
   - **Parameters description**
     - No parameters; this is an enum with fixed values.
   - **Returns description**
     - No return value; the enum type itself is used as a field in other state classes.

2. **FileTransferState (sealed)**
   - Category: sealed class, state model
   - Lines: 8-53
   - **Description**
     - Root sealed class defining all possible runtime states of a file transfer operation.
     - Allows exhaustive when expressions and type-safe handling of transfer state variations in calling code (UI, services, viewmodels).
   - **Parameters description**
     - No constructor parameters on the sealed class itself; specific states carry their own data.
   - **Returns description**
     - Represents the polymorphic union type for transfer states; instances are the concrete states defined below.

3. **Idle**
   - Category: object (singleton state)
   - Lines: 10-10
   - **Description**
     - Stateless singleton representing that no file transfer is currently active.
     - Useful as an initial or reset state.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - An instance of FileTransferState indicating idle state.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | Idle | FileTransferState | Singleton object indicating no active transfer. |

4. **Sending**
   - Category: data class, progress property
   - Lines: 12-24
   - **Description**
     - Represents an in-progress outbound file transfer with progress tracking.
     - Carries filename, bytesSent and totalBytes; exposes a derived Float progress property (0.0 to 1.0) computed from those values.
   - **Parameters description**
     - Holds the filename and numeric counters used to compute progress for an outbound transfer.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | filename | String | Name of the file being sent; used for display and logging. |
     | bytesSent | Long | Number of bytes successfully written/sent so far. |
     | totalBytes | Long | Total size in bytes of the file being sent; used as the denominator for progress. |
   - **Returns description**
     - Instances represent an in-progress send state; also provides a progress Float property derived from bytesSent/totalBytes.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | Sending instance | FileTransferState.Sending | Data instance carrying send progress information. |
     | progress | Float | Derived proportion (0.0 to 1.0) of bytesSent / totalBytes, guarded against division by zero. |

5. **Receiving**
   - Category: data class, progress property
   - Lines: 26-38
   - **Description**
     - Represents an in-progress inbound file transfer with progress tracking.
     - Carries filename, bytesReceived and totalBytes; exposes a derived Float progress property (0.0 to 1.0) computed from those values.
   - **Parameters description**
     - Holds the filename and numeric counters used to compute progress for an inbound transfer.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | filename | String | Name of the file being received; used for display and logging. |
     | bytesReceived | Long | Number of bytes successfully received so far. |
     | totalBytes | Long | Total size in bytes of the file being received; used as the denominator for progress. |
   - **Returns description**
     - Instances represent an in-progress receive state; also provides a progress Float property derived from bytesReceived/totalBytes.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | Receiving instance | FileTransferState.Receiving | Data instance carrying receive progress information. |
     | progress | Float | Derived proportion (0.0 to 1.0) of bytesReceived / totalBytes, guarded against division by zero. |

6. **Done**
   - Category: data class
   - Lines: 40-44
   - **Description**
     - Represents a successfully completed file transfer.
     - Includes the filename, final totalBytes, and TransferDirection to indicate whether the completed transfer was inbound or outbound.
   - **Parameters description**
     - Carries final metadata for a completed transfer.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | filename | String | Name of the file that completed transfer. |
     | totalBytes | Long | Final byte count for the transferred file. |
     | direction | TransferDirection | Indicates SEND or RECEIVE for the completed transfer. |
   - **Returns description**
     - Instance indicates a successful transfer completion and conveys final metadata.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | Done | FileTransferState.Done | Completed transfer state containing file metadata and direction. |

7. **Failed**
   - Category: data class
   - Lines: 46-50
   - **Description**
     - Represents a failed transfer attempt; includes optional filename, a textual reason, and optional direction.
     - Nullable filename and direction allow representing failures that occur before metadata is available or direction is unknown.
   - **Parameters description**
     - Provides diagnostic information about a failed transfer; some fields are nullable to accommodate partial/failing contexts.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | filename | String? | Optional file name if known; may be null if failure happened before filename resolution. |
     | reason | String | Human-readable explanation or error message describing why the transfer failed. |
     | direction | TransferDirection? | Optional direction indicating whether the attempted transfer was SEND or RECEIVE; may be null if unknown. |
   - **Returns description**
     - Instance indicates a failed transfer and carries diagnostic fields to aid error handling and display.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | Failed | FileTransferState.Failed | Failed transfer state with optional filename and direction plus a reason. |

8. **Cancelled**
   - Category: object (singleton state)
   - Lines: 52-52
   - **Description**
     - Stateless singleton representing that a transfer was canceled by the user or system.
     - Distinct from Failed to allow consumers to treat cancellations differently from errors.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - An instance of FileTransferState indicating cancellation.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | Cancelled | FileTransferState | Singleton object indicating transfer was cancelled. |


**Code Walkthroughs**
1. **Lines:** 18-23
   - **What it does**
     - Computes the outbound send progress as a Float in the range 0.0 to 1.0.
     - Guards against division-by-zero by returning 0f when totalBytes equals zero.
   - **Why it matters**
     - Derived property uses bytesSent-to-totalBytes ratio and converts Long to Double then Float to avoid integer division and preserve fractional progress.

2. **Lines:** 32-37
   - **What it does**
     - Computes the inbound receive progress as a Float in the range 0.0 to 1.0.
     - Guards against division-by-zero by returning 0f when totalBytes equals zero.
   - **Why it matters**
     - Same rationale as the Sending progress getter; conversion to Double then Float prevents integer division truncation and yields a precise fractional progress value.

3. **Lines:** 46-50
   - **What it does**
     - Defines a failure state that may lack filename or direction information.
     - Allows representing early or partial failures where metadata is unavailable.
   - **Why it matters**
     - Nullable filename and direction provide flexibility in error representation but require consumers to null-check before use.


**Style Conventions**
1. **Lines:** 8-53
   - **Guideline**
     - Uses Kotlin sealed class and data class idioms to model a closed set of states; this enables exhaustive when checks and concise immutable state objects.
     - Naming is clear and follows typical Kotlin/Android conventions (PascalCase for types, UPPER_SNAKE or Pascal for enum values).
   - **Rationale**
     - Sealed class + data classes improve readability and safety for state handling across UI and services.

2. **Lines:** 46-50
   - **Guideline**
     - Nullable types (String? and TransferDirection?) used for fields that may not always be available during failures.
     - Consumers must handle nullability when reading these fields.
   - **Rationale**
     - Explicit nullability communicates that failure contexts can be partial and prevents implicit assumptions about available metadata.
