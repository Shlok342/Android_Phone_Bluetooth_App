**FileName:** CharacteristicPropertyParser.kt   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/java/com/example/myapplication/util/CharacteristicPropertyParser.kt   
**Tags:** bluetooth, util, parser, kotlin, gatt   

**File Summary**
Kotlin utility that parses Bluetooth GATT characteristic property bitflags into a human-readable list of property names. It inspects an integer bitmask and returns which standard BluetoothGattCharacteristic property flags are set (READ, WRITE, WRITE_NO_RESPONSE, NOTIFY, INDICATE). Implemented as a singleton object for simple reuse across the app.

**Function Summaries**
1. **parse**
   - Category: function, utility, parser
   - Lines: 7-32
   - **Description**
     - Accepts an integer bitmask representing Bluetooth GATT characteristic properties and returns a list of string labels for each recognized property bit that is set.
     - Performs bitwise checks against standard BluetoothGattCharacteristic PROPERTY_* constants and accumulates matching property names into a mutable list which is returned as the result.
   - **Parameters description**
     - A single integer parameter representing a bitmask of BluetoothGattCharacteristic properties.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | properties | Int | Bitmask of BluetoothGattCharacteristic property flags. Each bit represents a capability (e.g., read, write, notify). |
   - **Returns description**
     - A list of human-readable property names (strings) corresponding to the bits set in the input bitmask.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | parsed | List<String> | List containing zero or more of the strings: READ, WRITE, WRITE_NO_RESPONSE, NOTIFY, INDICATE; order corresponds to the checks in the function. |


**Code Walkthroughs**
1. **Lines:** 11-29
   - **What it does**
     - Perform bitwise AND checks between the input bitmask and BluetoothGattCharacteristic PROPERTY_* constants to determine which properties are present.
     - If a check yields a non-zero result, the corresponding uppercase string label is appended to the result list.
   - **Why it matters**
     - Bitwise operations are non-obvious to new readers; highlighting clarifies that each check detects a specific bit flag in the integer and that non-zero indicates the flag is set.


**Style Conventions**
1. **Lines:** 5-32
   - **Guideline**
     - Defined as a Kotlin object (singleton) to provide a single reusable parser instance without needing instantiation.
     - Uses descriptive uppercase string labels for properties, matching common Bluetooth terminology and keeping return values simple and language-agnostic.
   - **Rationale**
     - Singleton pattern via Kotlin object is appropriate for stateless utility functions; uppercase labels make the parsed output predictable for UI or logging consumers.

2. **Lines:** 9-31
   - **Guideline**
     - Uses mutableListOf<String>() internally and returns it directly (as a List<String>).
     - Function signature returns List<String>, exposing an immutable interface even though a mutable list is used internally.
   - **Rationale**
     - Returning the list as a List type hides mutability from callers, following a common Kotlin practice to prefer immutable types in public APIs.
