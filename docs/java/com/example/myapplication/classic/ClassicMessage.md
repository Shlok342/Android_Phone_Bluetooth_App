**FileName:** ClassicMessage.kt   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/java/com/example/myapplication/classic/ClassicMessage.kt   
**Tags:** model, parsing, binary, immutable, bluetooth   

**File Summary**
Defines immutable message models used by the app for Classic (likely Bluetooth Classic) communication. It provides a sealed ClassicMessage hierarchy with Text, Binary, and ParseError variants (each recording a timestamp). It also defines ParseFailure variants used to describe parse errors encountered when interpreting raw bytes.

**Function Summaries**
1. **ClassicMessage**
   - Category: sealed class, model
   - Lines: 3-65
   - **Description**
     - Root sealed class representing different kinds of messages the system handles (text, binary, parse error).
     - Provides a common abstract property timestampMs that all message variants populate, enabling unified handling and ordering by time.
   - **Parameters description**
     - No constructor parameters; it defines a contract (abstract property) for subclasses.
   - **Returns description**
     - N/A

2. **Text**
   - Category: data class, model
   - Lines: 7-16
   - **Description**
     - Represents a textual message with the original string and a timestamp.
     - Provides a lazily-computed hex representation of the UTF-8 encoding of the raw string for inspection or logging.
   - **Parameters description**
     - Constructor takes the raw string payload and an optional timestamp in milliseconds (defaults to current system time).
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | raw | String | The textual payload received or to be sent. |
     | timestampMs | Long | Timestamp in milliseconds for when the message was created or received; defaults to System.currentTimeMillis() if not provided. |
   - **Returns description**
     - Instantiates a ClassicMessage.Text containing raw and timestamp; also exposes a lazily computed hex string representation.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | hex | String | Space-separated uppercase hex bytes of raw.encodeToByteArray(); computed lazily on first access. |

3. **Binary**
   - Category: class, model, immutable wrapper
   - Lines: 18-38
   - **Description**
     - Represents a binary message (byte array) with timestamp while enforcing immutability via defensive copying.
     - Overrides equals and hashCode to compare underlying byte content correctly (arrays require content-based comparison).
   - **Parameters description**
     - Constructor takes a ByteArray and an optional timestamp in milliseconds (defaults to current system time).
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | bytes | ByteArray | Raw binary payload; copied defensively into an internal array to prevent external mutation. |
     | timestampMs | Long | Timestamp in milliseconds for when the message was created or received; defaults to System.currentTimeMillis() if not provided. |
   - **Returns description**
     - Instantiates a ClassicMessage.Binary exposing a bytes getter that returns a copy, and equality/hashCode based on content.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | bytes | ByteArray | A defensive copy of the internal byte array returned on each access to maintain immutability. |

4. **ParseError**
   - Category: class, model, error
   - Lines: 40-64
   - **Description**
     - Represents a message that failed to parse, carrying a ParseFailure reason and the raw bytes that caused the failure.
     - Performs defensive copying of raw bytes and implements content-based equals/hashCode including the failure reason.
   - **Parameters description**
     - Constructor accepts a ParseFailure reason, the raw bytes that failed to parse, and an optional timestamp (defaults to current system time).
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | reason | ParseFailure | Enumerated reason explaining why parsing failed (see ParseFailure sealed class). |
     | rawBytes | ByteArray | Original raw byte sequence that could not be parsed; copied defensively into internal storage. |
     | timestampMs | Long | Timestamp in milliseconds for when the parse error was created or observed; defaults to System.currentTimeMillis() if not provided. |
   - **Returns description**
     - Instantiates a ClassicMessage.ParseError exposing rawBytes getter that returns a copy; equality and hashCode include both reason and byte content.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | rawBytes | ByteArray | A defensive copy of the internal raw bytes returned on access to preserve immutability. |

5. **ParseFailure**
   - Category: sealed class, enum-like
   - Lines: 67-79
   - **Description**
     - Enumerates the possible reasons a parse operation failed (invalid length, checksum, sequence, unsupported opcode, and generic unknown with message).
     - Used by ParseError to give structured error information to callers.
   - **Parameters description**
     - Variants have either no data (objects) or provide additional context via data class fields (opcode or message).
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | UnsupportedOpcode.opcode | Int | Opcode value that was not recognized; available when the failure is unsupported opcode. |
     | Unknown.message | String | Arbitrary error message for unknown parse failures. |
   - **Returns description**
     - N/A


**Code Walkthroughs**
1. **Lines:** 12-15
   - **What it does**
     - Computes a space-separated uppercase hex string representation of the UTF-8 bytes of the raw text.
     - The computation is lazy: the byte encoding and formatting happen only on first access and the resulting String is cached.
   - **Why it matters**
     - Converts text to a human-readable hex form for logging or diagnostics while avoiding work unless hex is requested.

2. **Lines:** 23-23
   - **What it does**
     - Creates a defensive copy of the input byte array in the Binary class constructor to prevent callers from mutating internal state.
   - **Why it matters**
     - Byte arrays are mutable; copying at construction enforces immutability of the message object.

3. **Lines:** 25-26
   - **What it does**
     - Provides a getter that returns a copy of the internal byte array on each access, preventing external mutation of internal state.
   - **Why it matters**
     - Ensures the object's internal byte storage remains immutable even when callers attempt to modify the returned array.

4. **Lines:** 28-33
   - **What it does**
     - Overrides equals to perform identity check, type check, and then content-based comparison of the internal byte arrays using contentEquals.
   - **Why it matters**
     - Default equals would compare references; arrays require content-based equality, so custom implementation is necessary.

5. **Lines:** 35-37
   - **What it does**
     - Overrides hashCode to produce a hash based on the contents of the internal byte array using contentHashCode.
   - **Why it matters**
     - Arrays do not produce content-based hash codes by default; using contentHashCode ensures hashCode is consistent with equals.

6. **Lines:** 46-49
   - **What it does**
     - Mirrors the Binary pattern: copy constructor input rawBytes into a private array and expose a getter that returns a copy.
     - Protects ParseError's stored raw bytes from external mutation.
   - **Why it matters**
     - Defensive copying is necessary for safe immutability when storing ByteArray references.

7. **Lines:** 51-57
   - **What it does**
     - Overrides equals to include both the ParseFailure reason and content-based comparison of the stored raw bytes.
     - Performs identity and type checks before comparing fields.
   - **Why it matters**
     - Equality must reflect both the failure reason and the exact raw bytes that failed to parse.

8. **Lines:** 59-63
   - **What it does**
     - Overrides hashCode to combine the reason's hashCode and the content hash of the raw bytes using a multiplier to reduce collisions.
     - Keeps hashCode consistent with equals implementation.
   - **Why it matters**
     - Necessary to maintain contract that equal objects produce equal hash codes (important if used in hashed collections).

9. **Lines:** 9-9
   - **What it does**
     - Default timestamp values use System.currentTimeMillis() to record creation/arrival time when not explicitly provided.
   - **Why it matters**
     - Provides convenient automatic timestamps for all message instances without requiring callers to supply them.

10. **Lines:** 13-14
   - **What it does**
     - Uses encodeToByteArray to obtain the platform default UTF-8 bytes and formats each byte as a two-digit uppercase hex string.
   - **Why it matters**
     - Converts the string payload to a deterministic byte representation for hex output.


**Style Conventions**
1. **Lines:** 3-79
   - **Guideline**
     - Uses Kotlin sealed classes to model a closed set of message and parse-failure types; follows idiomatic Kotlin patterns (data class for value types).
     - Consistent defensive copying for ByteArray fields and explicit equals/hashCode overrides where arrays are involved.
   - **Rationale**
     - Sealed classes provide exhaustive when checks and express domain variants clearly; defensive copying enforces immutability for mutable array types.

2. **Lines:** 7-16
   - **Guideline**
     - Text is a data class (auto-generated equals/hashCode/toString) while Binary and ParseError are regular classes with manual equals/hashCode because of array fields.
   - **Rationale**
     - Data class default implementations are fine for immutable value types without array fields; arrays require custom logic for content equality.
