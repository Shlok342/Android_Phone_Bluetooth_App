**FileName:** BluetoothPermissionUtils.kt   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/java/com/example/myapplication/util/BluetoothPermissionUtils.kt   
**Tags:** android, permissions, bluetooth, util, kotlin   

**File Summary**
A small Kotlin utility placed in the util package that centralizes the runtime check for the BLUETOOTH_CONNECT permission. It exposes a single function to determine whether the app currently has the Bluetooth connect permission, taking into account Android SDK version differences where the permission only applies on Android S (API 31) and above.

**Function Summaries**
1. **hasBluetoothConnectPermission**
   - Category: function, utility
   - Lines: 10-18
   - **Description**
     - Determines whether the app has the BLUETOOTH_CONNECT runtime permission.
     - Returns true on Android versions prior to S (API level 31) because the BLUETOOTH_CONNECT runtime permission did not exist before that version, and otherwise uses ContextCompat.checkSelfPermission to check the permission state.
   - **Parameters description**
     - Accepts an Android Context used as the calling context for permission checks.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | context | android.content.Context | Context used to call ContextCompat.checkSelfPermission; must be a valid context from the app (Activity, Service, or Application context). |
   - **Returns description**
     - Returns a Boolean indicating whether the BLUETOOTH_CONNECT permission is granted. On Android versions earlier than S it always returns true.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | Boolean | kotlin.Boolean | true if the permission is effectively granted (or not required because SDK < S), false if the permission exists and is not granted. |


**Configuration References**
1. **Android SDK version check (Build.VERSION.SDK_INT / Build.VERSION_CODES.S)**
   - Line: 7,13
   - **What it does:**
     - Determines whether the BLUETOOTH_CONNECT runtime permission applies on the running platform. The code behaves differently when running on API level S (31) or above versus earlier versions.
   - **Default value**
     - N/A

2. **Manifest.permission.BLUETOOTH_CONNECT**
   - Line: 16
   - **What it does:**
     - Represents the platform permission constant being checked. The file depends on this constant to identify which permission to query.
   - **Default value**
     - N/A


**Code Walkthroughs**
1. **Lines:** 13-13
   - **What it does**
     - Checks the device's SDK version and short-circuits the permission check for older Android versions.
   - **Why it matters**
     - BLUETOOTH_CONNECT is only a runtime permission on Android S (API 31) and above; returning true avoids unnecessary permission calls and aligns behavior with older platform expectations.

2. **Lines:** 14-17
   - **What it does**
     - Uses ContextCompat.checkSelfPermission to query the current runtime permission state and compares it to PackageManager.PERMISSION_GRANTED.
   - **Why it matters**
     - This is the standard, compatibility-aware approach to checking runtime permissions in Android; ContextCompat ensures consistent behavior across API levels.


**Style Conventions**
1. **Lines:** 8-18
   - **Guideline**
     - Defines a Kotlin object to provide a stateless utility function, making the function effectively static and accessible without instantiation.
     - Uses an early return for the SDK check which keeps the function concise and readable.
   - **Rationale**
     - Using an object for utility methods is idiomatic Kotlin for grouping related stateless helpers and provides single-instance access without explicit instantiation.
