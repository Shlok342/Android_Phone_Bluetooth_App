**FileName:** FavouriteStore.kt   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/java/com/example/myapplication/util/FavouriteStore.kt   
**Tags:** preferences, android, utility, storage   

**File Summary**
A small Kotlin utility singleton that manages persistent "favorite" flags keyed by an address string using Android SharedPreferences. It exposes two convenience functions: one to query whether an address is marked favorite and one to toggle that boolean value, storing results in a preferences file named "device_favorites". The implementation uses the androidx.core.content.edit extension for concise preference writes.

**Function Summaries**
1. **FavoriteStore (object)**
   - Category: Singleton, Utility
   - Lines: 6-19
   - **Description**
     - Defines a single, process-wide utility object that encapsulates access to a SharedPreferences file used to store boolean "favorite" flags keyed by address strings.
     - Holds the preference file name constant and provides two public API functions (isFavorite and toggle) for reading and updating those flags.
     - Centralizes preference key usage and write behavior so callers need only pass a Context and an address string.
   - **Parameters description**
     - No parameters; this is an object (singleton) exposing functions that accept Context and address parameters.
   - **Returns description**
     - N/A

2. **isFavorite**
   - Category: Function, Query
   - Lines: 9-11
   - **Description**
     - Reads and returns a boolean indicating whether the provided address key is marked as a favorite in the "device_favorites" SharedPreferences file.
     - Performs a readonly access (getBoolean) and returns false if the key is not present.
   - **Parameters description**
     - Accepts an Android Context to access SharedPreferences and an address string used as the key under which the boolean value is stored.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | context | Context | Android Context instance used to obtain the SharedPreferences instance with MODE_PRIVATE scope. |
     | address | String | String key used to look up the favorite boolean in the preferences file. |
   - **Returns description**
     - Returns a Boolean indicating whether the address key exists and is set to true; returns false if the key is absent or explicitly false.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | Boolean | Boolean | The favorite flag for the given address. |

3. **toggle**
   - Category: Function, Mutation
   - Lines: 13-18
   - **Description**
     - Toggles the stored boolean favorite flag for the given address: reads the current value (default false), negates it, writes the new value back to the same preferences file, and returns the new value.
     - Uses the androidx.core.content.edit extension to perform the write within a lambda for concise syntax.
   - **Parameters description**
     - Accepts an Android Context to access SharedPreferences and an address string used as the key for the boolean value to toggle.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | context | Context | Android Context used to obtain the SharedPreferences instance with MODE_PRIVATE scope. |
     | address | String | String key identifying which boolean favorite flag to toggle in the preferences file. |
   - **Returns description**
     - Returns the new boolean value after toggling (true if it was previously false, false if it was previously true).
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | new | Boolean | The updated favorite flag value after the toggle and persistence operation. |


**Configuration References**
1. **PREF_NAME**
   - Line: 7
   - **What it does:**
     - Defines the SharedPreferences file name used by the utility ('device_favorites'). This constant determines where favorite flags are stored and must be consistent across callers.
     - Changing this value would change the preferences file location and thereby affect persisted data visibility.
   - **Default value**
     - device_favorites


**Code Walkthroughs**
1. **Lines:** 9-11
   - **What it does**
     - Chained call that obtains the SharedPreferences instance for PREF_NAME in MODE_PRIVATE and immediately queries a boolean by the provided address key.
   - **Why it matters**
     - The one-line chained call combines getSharedPreferences(...) and getBoolean(...). Highlighting clarifies where the preference file is sourced and that the default false value is supplied if the key is missing.

2. **Lines:** 14-16
   - **What it does**
     - Reads the current boolean, computes the negated value, and persists it using the prefs.edit lambda helper.
   - **Why it matters**
     - The combination of reading, negating, and writing in short form can be easy to miss; the use of the edit lambda (from androidx.core.content.edit) affects how the write is applied (via apply/commit behavior internal to that helper).


**Style Conventions**
1. **Lines:** 6-6
   - **Guideline**
     - The file defines a Kotlin 'object' (singleton) named FavoriteStore which exposes small, focused functions for preference access.
     - Functions are implemented as concise single-expression or short-block functions for readability and brevity.
   - **Rationale**
     - Concise style matches idiomatic Kotlin for small utilities and makes the API surface easy to scan.

2. **Lines:** 0-0
   - **Guideline**
     - File name (provided externally as FavouriteStore.kt) differs in spelling from the object name FavoriteStore defined on line 6.
   - **Rationale**
     - A naming mismatch between file name and object name may affect discoverability in IDEs or when following repository naming conventions.
