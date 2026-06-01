**FileName:** DeviceNameStore.kt   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/java/com/example/myapplication/util/DeviceNameStore.kt   
**Tags:** preferences, android, utilities, bluetooth   

**File Summary**
Singleton Kotlin object that provides a small API to store, retrieve, remove, and query custom human-friendly names for devices in Android SharedPreferences. It normalizes device addresses to a canonical key form, uses applicationContext-backed SharedPreferences with a dedicated prefs name, and exposes simple convenience methods for common operations (get, save, remove, clearAll, hasCustomName). The implementation uses androidx.core.content.edit for concise SharedPreferences edits.

**Function Summaries**
1. **DeviceNameStore**
   - Category: object,singleton,utility
   - Lines: 6-49
   - **Description**
     - Top-level singleton that groups helper functions to persist and manage custom device names keyed by normalized device addresses.
     - Encapsulates SharedPreferences access and normalization rules so callers only interact with high-level operations (get, save, remove, clearAll, hasCustomName).
   - **Parameters description**
     - No parameters — this is an object container for related functions. Individual functions accept a Context and device address/name where required.
   - **Returns description**
     - N/A for the object itself; its functions return values as described in their individual summaries.

2. **prefs**
   - Category: helper function,private
   - Lines: 10-14
   - **Description**
     - Returns a SharedPreferences instance scoped to the application context using a fixed PREFS_NAME.
     - Centralizes the SharedPreferences retrieval to ensure the same storage file and to use applicationContext (prevents activity/context leaks).
   - **Parameters description**
     - Accepts a Context which is converted to applicationContext to fetch SharedPreferences.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | context | Context | Android Context used to obtain applicationContext and then the SharedPreferences instance. |
   - **Returns description**
     - Returns a SharedPreferences instance bound to the PREFS_NAME in MODE_PRIVATE.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | SharedPreferences | android.content.SharedPreferences | The SharedPreferences instance used to store device custom names. |

3. **normalizeAddress**
   - Category: helper function,private,normalization
   - Lines: 16-17
   - **Description**
     - Normalizes a device address string before using it as a key in SharedPreferences.
     - Applies trimming and uppercasing to enforce a canonical, case-insensitive key format.
   - **Parameters description**
     - Accepts the raw device address string and returns a normalized version for stable lookup and storage.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | address | String | Raw device address (e.g., Bluetooth MAC or other identifier) that will be normalized. |
   - **Returns description**
     - Returns the normalized address string that will be used as the SharedPreferences key.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | normalizedAddress | String | Trimmed and uppercased representation of the input address. |

4. **get**
   - Category: public function,accessor
   - Lines: 19-22
   - **Description**
     - Fetches the stored custom name for the given device address, or null if none is stored.
     - Uses the normalized address as the key to read from the dedicated SharedPreferences file.
   - **Parameters description**
     - Takes an Android Context and a device address; address is normalized before lookup.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | context | Context | Context used to access SharedPreferences (converted to applicationContext internally). |
     | address | String | Device address whose custom name is being requested; will be normalized for lookup. |
   - **Returns description**
     - Returns the stored custom name as a String, or null when no custom name exists for the address.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | name | String? | The custom name value read from SharedPreferences, or null if absent. |

5. **save**
   - Category: public function,mutator
   - Lines: 24-31
   - **Description**
     - Stores or updates a custom name for a given device address in SharedPreferences.
     - Normalizes the address to produce the key and trims the provided name before persisting using the edit extension.
   - **Parameters description**
     - Accepts Context, device address, and desired display name; normalizes/cleans inputs prior to saving.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | context | Context | Context used to access SharedPreferences (converted to applicationContext internally). |
     | address | String | Device address to be used as the key after normalization. |
     | name | String | Custom display name to store; it will be trimmed before persisting. |
   - **Returns description**
     - No return value; side-effect: the provided name is persisted to SharedPreferences.

6. **remove**
   - Category: public function,mutator
   - Lines: 33-36
   - **Description**
     - Removes the custom name entry associated with the provided device address from SharedPreferences.
     - Normalizes the address and removes the corresponding key using SharedPreferences.edit.
   - **Parameters description**
     - Takes Context and device address; only address is normalized and used to remove the key.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | context | Context | Context used to access SharedPreferences. |
     | address | String | Device address whose stored custom name should be removed; normalized internally. |
   - **Returns description**
     - No return value; the function removes the key/value pair from SharedPreferences if present.

7. **clearAll**
   - Category: public function,mutator
   - Lines: 39-42
   - **Description**
     - Clears all entries from the dedicated SharedPreferences file used for device custom names.
     - Useful for resetting stored custom names across the application.
   - **Parameters description**
     - Takes a Context and clears the entire PREFS_NAME SharedPreferences file.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | context | Context | Context used to fetch and clear the SharedPreferences instance. |
   - **Returns description**
     - No return value; side-effect: all stored device custom names are removed.

8. **hasCustomName**
   - Category: public function,query
   - Lines: 45-48
   - **Description**
     - Checks whether a custom name exists for the supplied device address in SharedPreferences.
     - Normalizes the address and uses SharedPreferences.contains to perform the presence check.
   - **Parameters description**
     - Accepts Context and device address; normalizes the address before checking presence.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | context | Context | Context used to access SharedPreferences. |
     | address | String | Device address to check for a stored custom name; normalized internally. |
   - **Returns description**
     - Returns a Boolean indicating whether a custom name key exists in SharedPreferences for the normalized address.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | exists | Boolean | True if a key is present for the normalized address; false otherwise. |


**Configuration References**
1. **PREFS_NAME**
   - Line: 8,12
   - **What it does:**
     - Constant that defines the SharedPreferences filename used to store device custom names.
     - Changing this name changes the storage file location and will decouple previously stored values from the code unless migration is performed.
   - **Default value**
     - "device_custom_names"

2. **Context.MODE_PRIVATE**
   - Line: 13
   - **What it does:**
     - Security mode used when opening SharedPreferences; ensures the preferences file is private to the app process.
   - **Default value**
     - MODE_PRIVATE (standard Android flag)


**Code Walkthroughs**
1. **Lines:** 10-14
   - **What it does**
     - Uses applicationContext.getSharedPreferences with PREFS_NAME and Context.MODE_PRIVATE to obtain the persistent key-value store.
     - Centralizes access so all read/write operations use the same SharedPreferences file and application context.
   - **Why it matters**
     - Using applicationContext avoids leaking Activity contexts and ensures a single shared storage file is used across the app.

2. **Lines:** 16-17
   - **What it does**
     - Trim whitespace and upper-case the address to unify keys (case-insensitive, whitespace-tolerant lookup).
   - **Why it matters**
     - Normalizing addresses prevents duplicate keys differing only by case or surrounding whitespace, ensuring consistent storage/lookup.

3. **Lines:** 24-26
   - **What it does**
     - Prepares inputs for storage: compute normalizedAddress and trim the provided name to remove accidental whitespace.
   - **Why it matters**
     - Trimming names avoids storing values with leading/trailing spaces, which could look odd in UI or cause unnecessary differences.

4. **Lines:** 28-30
   - **What it does**
     - Uses androidx.core.content.edit extension to perform a concise SharedPreferences transaction that calls putString and applies the change.
     - The lambda-based edit is more concise than obtaining an editor, calling putString, and applying/committing explicitly.
   - **Why it matters**
     - The edit extension handles editor creation and commit/apply; it improves readability and reduces boilerplate.

5. **Lines:** 46-47
   - **What it does**
     - Uses SharedPreferences.contains with the normalized key to determine presence without retrieving the value.
     - Efficiently checks existence for conditional UI logic or flow control.
   - **Why it matters**
     - Checking presence via contains avoids unnecessary getString allocations when only existence is required.


**Database Operations**
1. **Save custom name**
   - Type: Put/Update (key-value) | Table: device_custom_names (SharedPreferences)
   - Lines: 28-30
   - **What it does**
     - Puts a String value (cleanedName) into SharedPreferences under the normalizedAddress key to persist a custom name.
   - **Parameters**
```json
{
  "key": "AA:BB:CC:DD:EE:FF",
  "value": "Living Room Speaker"
}
```
   - **Returns**
```json
{
  "success": true,
  "storedKey": "AA:BB:CC:DD:EE:FF",
  "storedValue": "Living Room Speaker"
}
```

2. **Get custom name**
   - Type: Get/Select (key-value) | Table: device_custom_names (SharedPreferences)
   - Lines: 20-21
   - **What it does**
     - Retrieves the String value stored under the normalizedAddress key, or returns null when absent.
   - **Parameters**
```json
{
  "key": "AA:BB:CC:DD:EE:FF"
}
```
   - **Returns**
```json
{"value": "Living Room Speaker"} or {"value": null}
```

3. **Remove custom name**
   - Type: Delete (key) | Table: device_custom_names (SharedPreferences)
   - Lines: 34-36
   - **What it does**
     - Removes the entry for the normalizedAddress key from the SharedPreferences file.
   - **Parameters**
```json
{
  "key": "AA:BB:CC:DD:EE:FF"
}
```
   - **Returns**
```json
{
  "success": true,
  "removedKey": "AA:BB:CC:DD:EE:FF"
}
```

4. **Clear all names**
   - Type: Clear/Reset | Table: device_custom_names (SharedPreferences)
   - Lines: 40-41
   - **What it does**
     - Clears all key/value pairs in the device_custom_names SharedPreferences file, removing all custom names.
   - **Parameters**
```json
{}
```
   - **Returns**
```json
{
  "success": true,
  "cleared": true
}
```

5. **Check name presence**
   - Type: Contains/Exists check | Table: device_custom_names (SharedPreferences)
   - Lines: 46-47
   - **What it does**
     - Checks whether a key for the normalizedAddress exists without retrieving its value.
   - **Parameters**
```json
{
  "key": "AA:BB:CC:DD:EE:FF"
}
```
   - **Returns**
```json
{"exists": true} or {"exists": false}
```


**Style Conventions**
1. **Lines:** 6-49
   - **Guideline**
     - Kotlin object singleton pattern is used to provide a stateless utility API.
     - Functions are short and often use expression bodies for conciseness; private helpers are used to centralize behavior (prefs, normalizeAddress).
   - **Rationale**
     - Consistent, idiomatic Kotlin style improves readability for new developers and centralizes common logic.

2. **Lines:** 28-30
   - **Guideline**
     - Uses androidx.core.content.edit extension with trailing lambda to reduce boilerplate when writing to SharedPreferences.
   - **Rationale**
     - This extension is a common Kotlin idiom for SharedPreferences edits and reduces explicit editor handling.
