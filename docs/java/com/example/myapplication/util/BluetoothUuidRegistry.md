**FileName:** BluetoothUuidRegistry.kt   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/java/com/example/myapplication/util/BluetoothUuidRegistry.kt   
**Tags:** bluetooth, uuid, utilities, kotlin   

**File Summary**
Kotlin singleton object that maintains small registries of known Bluetooth SIG UUIDs (services and characteristics) and exposes helper functions to resolve a java.util.UUID to a human-readable name. It contains two immutable maps populated with canonical 128-bit Bluetooth UUID strings and two accessors that perform case-insensitive lookups returning a fallback string when a UUID is not found.

**Function Summaries**
1. **BluetoothUuidRegistry (object)**
   - Category: singleton, utility
   - Lines: 5-31
   - **Description**
     - Defines a singleton utility which groups a registry of known Bluetooth service and characteristic UUIDs together with lookup helpers.
     - Provides centralized mapping and name-resolution helpers for other parts of the app that need to display or log human-readable names for Bluetooth UUIDs.
   - **Parameters description**
     - No parameters for the object itself; it holds internal data and exposes functions that accept UUID parameters.
   - **Returns description**
     - The object itself does not return values; it exposes functions that return strings for lookups.

2. **knownServices (map)**
   - Category: private immutable map, data
   - Lines: 7-13
   - **Description**
     - An immutable map from canonical 128-bit Bluetooth Service UUID strings (lowercase) to human-readable service names.
     - Contains a small set of common services (Generic Access, Generic Attribute, Device Information, Battery Service, Human Interface Device).
   - **Parameters description**
     - No parameters; this is a constant data structure.
   - **Returns description**
     - Not a function; this is a private property used by lookup functions.

3. **knownCharacteristics (map)**
   - Category: private immutable map, data
   - Lines: 15-20
   - **Description**
     - An immutable map from canonical 128-bit Bluetooth Characteristic UUID strings (lowercase) to human-readable characteristic names.
     - Contains a few common characteristics (Battery Level, Manufacturer Name, Model Number, Firmware Revision).
   - **Parameters description**
     - No parameters; this is a constant data structure.
   - **Returns description**
     - Not a function; this is a private property used by lookup functions.

4. **getServiceName**
   - Category: function, public
   - Lines: 22-25
   - **Description**
     - Accepts a java.util.UUID and returns a human-readable name for known Bluetooth services.
     - Performs a case-insensitive lookup by converting the UUID to its string representation, lowercasing it, and using it as the map key. Returns 'Unknown Service' when the UUID is not present in the registry.
   - **Parameters description**
     - Single parameter: the UUID to resolve into a service name.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | uuid | java.util.UUID | The UUID object representing a Bluetooth service; its string form is used as the lookup key after lowercasing. |
   - **Returns description**
     - Returns a String containing the resolved service name, or 'Unknown Service' if not found.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | String | String | The human-readable service name or the fallback 'Unknown Service'. |

5. **getCharacteristicName**
   - Category: function, public
   - Lines: 27-30
   - **Description**
     - Accepts a java.util.UUID and returns a human-readable name for known Bluetooth characteristics.
     - Performs a case-insensitive lookup by converting the UUID to its string representation, lowercasing it, and using it as the map key. Returns 'Unknown Characteristic' when the UUID is not present in the registry.
   - **Parameters description**
     - Single parameter: the UUID to resolve into a characteristic name.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | uuid | java.util.UUID | The UUID object representing a Bluetooth characteristic; its string form is used as the lookup key after lowercasing. |
   - **Returns description**
     - Returns a String containing the resolved characteristic name, or 'Unknown Characteristic' if not found.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | String | String | The human-readable characteristic name or the fallback 'Unknown Characteristic'. |


**Code Walkthroughs**
1. **Lines:** 7-13
   - **What it does**
     - Declares an immutable map of service UUID strings to readable names. Keys are full 128-bit UUIDs in canonical textual format.
   - **Why it matters**
     - This mapping is the authoritative registry used by getServiceName; callers rely on exact string matches (after lowercasing) to resolve names.

2. **Lines:** 15-20
   - **What it does**
     - Declares an immutable map of characteristic UUID strings to readable names. Keys follow the same canonical format as the service keys.
   - **Why it matters**
     - This mapping is the authoritative registry used by getCharacteristicName; keys must match the lowercased toString() of UUIDs.

3. **Lines:** 22-24
   - **What it does**
     - Converts the incoming UUID to a string and calls lowercase() before performing a map lookup, returning a fallback if no match exists.
   - **Why it matters**
     - The conversion and lowercasing implement case-insensitive matching against the map keys which are stored in lowercase string form.

4. **Lines:** 27-29
   - **What it does**
     - Same as above but for characteristic lookups: uuid.toString().lowercase() used as the key with a fallback value.
   - **Why it matters**
     - Ensures consistent lookup behavior between services and characteristics using canonical lowercased keys.


**Style Conventions**
1. **Lines:** 5-31
   - **Guideline**
     - Uses Kotlin 'object' to create a singleton utility, appropriate for a shared registry.
     - Immutable properties use mapOf and private visibility to encapsulate registry data, and public functions expose read-only access.
   - **Rationale**
     - This style ensures a single canonical registry instance and prevents accidental mutation from outside code.

2. **Lines:** 22-29
   - **Guideline**
     - Function and property naming is concise and descriptive (getServiceName, getCharacteristicName, knownServices, knownCharacteristics).
     - String keys are stored in lowercase; lookups explicitly lowercase the UUID string to match that representation.
   - **Rationale**
     - Consistency between key formatting and lookup behavior is important for correct resolution.
