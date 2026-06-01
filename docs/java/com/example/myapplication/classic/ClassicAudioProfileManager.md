**FileName:** ClassicAudioProfileManager.kt   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/java/com/example/myapplication/classic/ClassicAudioProfileManager.kt   
**Tags:** bluetooth, audio, a2dp, coroutines, permissions   

**File Summary**
This Kotlin file implements a manager for Bluetooth A2DP (classic audio) profile interactions on Android. It defines audio connection states, a data class for connection metadata, and ClassicAudioProfileManager which monitors A2DP service availability, tracks connection/playing state, resolves codec and device names, and performs automatic reconnect attempts with backoff using coroutines. The file uses reflection for some A2DP operations and includes runtime permission checks for BLUETOOTH_CONNECT on newer Android versions.

**Function Summaries**
1. **AudioProfileState**
   - Category: sealed class, model
   - Lines: 14-32
   - **Description**
     - Enumerates the possible states of the audio profile manager (IDLE, CONNECTING, CONNECTED, PLAYING, DISCONNECTED, RECONNECTING with attempt count, FAILED with reason).
     - Provides typed state variants for use across the manager and for exposing state via StateFlow.
   - **Parameters description**
     - N/A
   - **Returns description**
     - N/A

2. **AudioConnectionInfo**
   - Category: data class, model
   - Lines: 34-39
   - **Description**
     - Holds runtime metadata about the current audio connection: state, device address, device name, and codec name.
     - Used to expose richer connection details via a StateFlow to consumers.
   - **Parameters description**
     - Primary constructor parameters initialize stored fields for state, address, deviceName and codecName.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | state | AudioProfileState | Current audio profile state. |
     | address | String | Bluetooth device MAC address or empty string if not available. |
     | deviceName | String | Human-readable Bluetooth device name or empty string. |
     | codecName | String | Name of the active audio codec or fallback text like "Unknown". |
   - **Returns description**
     - N/A

3. **ClassicAudioProfileManager**
   - Category: class, manager
   - Lines: 41-382
   - **Description**
     - Main class that manages A2DP audio profile lifecycle, state exposure, codec resolution, device name resolution, reconnect logic and resource cleanup.
     - Maintains coroutine scope, state flows for external observers, A2DP proxy, current connected device, and reconnect attempts; responds to service and connection state changes.
   - **Parameters description**
     - Constructor takes an Android Context used to obtain BluetoothManager/Adapter and permission checks.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | context | Context | Android Context used for system services and permission checks. |
   - **Returns description**
     - Class instance providing methods and StateFlows to observe and control A2DP-related state.

4. **companion object**
   - Category: constants
   - Lines: 45-47
   - **Description**
     - Holds class-scoped constants; currently defines MAX_RECONNECT_ATTEMPTS = 3 used to cap automatic reconnect tries.
   - **Parameters description**
     - N/A
   - **Returns description**
     - N/A

5. **properties and initialization**
   - Category: state, fields, init
   - Lines: 49-76
   - **Description**
     - Defines coroutine scope (SupervisorJob + IO dispatcher), atomic reconnect attempt counter, MutableStateFlows for state and connection info, Bluetooth adapter and A2DP proxy variables, currently connected device, and reconnect job handle.
     - Initializes bluetoothAdapter via BluetoothManager and requests A2DP profile proxy during init block (lines 154-160).
   - **Parameters description**
     - N/A
   - **Returns description**
     - N/A

6. **resolveCodec**
   - Category: private function, reflection, codec detection
   - Lines: 78-129
   - **Description**
     - Determine the codec name used for a connected Bluetooth device. On Android < Q, returns "SBC" immediately. On Q+, attempts to call hidden A2DP methods via reflection to obtain codec status/config and map codec type constants to friendly names.
     - Handles errors and unknown values gracefully by returning "Unknown" or a fallback string including numeric codec type.
   - **Parameters description**
     - Takes a BluetoothDevice and returns a human-readable codec name; accesses a2dp proxy via reflection.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | device | BluetoothDevice | Target device for which the codec information should be resolved. |
   - **Returns description**
     - Returns the codec name as String (e.g., "SBC", "AAC", "aptX", "LDAC" or "Unknown" variants).
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | codecName | String | Human-readable codec name or "Unknown" fallback if information is unavailable or reflection fails. |

7. **profileListener (BluetoothProfile.ServiceListener)**
   - Category: listener, event handler
   - Lines: 130-160
   - **Description**
     - Listens for A2DP profile proxy connection and disconnection events. On service connected, stores the A2DP proxy and checks currently connected devices. On service disconnected, nulls the proxy, updates state to DISCONNECTED, and re-requests the A2DP proxy to auto-recover.
   - **Parameters description**
     - Implemented callback methods receive profile ints and proxy instances from Android Bluetooth framework.
   - **Returns description**
     - N/A

8. **checkCurrentlyConnectedDevices**
   - Category: private function, device discovery
   - Lines: 166-190
   - **Description**
     - Queries the A2DP proxy for currently connected devices and, if found and permission granted, marks the first device as connected and updates internal state and connection info.
     - Catches and ignores SecurityException if permission is not available.
   - **Parameters description**
     - No parameters; uses class-level a2dp proxy and permission helper.
   - **Returns description**
     - No return; updates manager state flows and connectedDevice field.

9. **onA2dpConnectionStateChanged**
   - Category: public function, event handler
   - Lines: 192-228
   - **Description**
     - Handles A2DP connection state change events for a device. Updates isIntentionalDisconnect and reconnectAttempts when connected, updates state to CONNECTING/CONNECTED/DISCONNECTED accordingly, stores connectedDevice, and triggers scheduleReconnect() when disconnected.
     - Intended to be called by an external receiver or the Bluetooth framework integration when connection state changes.
   - **Parameters description**
     - Takes optional BluetoothDevice and integer state constant (from BluetoothProfile) and updates internal state based on the value.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | device | BluetoothDevice? | Device whose connection state changed; can be null. |
     | state | Int | Connection state constant (e.g., BluetoothProfile.STATE_CONNECTED). |
   - **Returns description**
     - No return; updates state flows and may schedule reconnects.

10. **onA2dpPlayingStateChanged**
   - Category: public function, event handler
   - Lines: 230-253
   - **Description**
     - Handles A2DP playback state changes (PLAYING vs NOT_PLAYING) and updates the exposed state to PLAYING or CONNECTED accordingly.
     - Should be invoked when the A2DP playback state transitions so UI/consumers can reflect playback vs connected-only states.
   - **Parameters description**
     - Accepts optional BluetoothDevice and integer playback state from BluetoothA2dp constants.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | device | BluetoothDevice? | Device whose playback state changed; can be null. |
     | state | Int | Playback state constant (e.g., BluetoothA2dp.STATE_PLAYING). |
   - **Returns description**
     - No return; updates state flows.

11. **scheduleReconnect**
   - Category: private function, reconnect logic, coroutine
   - Lines: 255-292
   - **Description**
     - Schedules an automatic reconnect attempt for the connectedDevice when a disconnect occurs, subject to an intentional disconnect guard and a maximum retry limit.
     - Cancels any existing reconnect job, increments an AtomicInteger attempt counter inside a coroutine on the managerScope with a backoff delay (1s, 2s, 4s), updates state to RECONNECTING(attempt), and calls tryReconnect(device).
   - **Parameters description**
     - No parameters; operates on class-level connectedDevice and reconnectAttempts.
   - **Returns description**
     - No return; launches coroutine job stored in reconnectJob or updates FAILED state when attempts exceed limit.

12. **tryReconnect**
   - Category: private function, reflection, connection attempt
   - Lines: 294-319
   - **Description**
     - Attempts to reconnect to a BluetoothDevice by invoking a hidden/connect method on the A2DP profile proxy via reflection, if the proxy exists and permission is available. On reflection or invocation failure, sets the manager state to FAILED.
     - Uses unchecked casts and reflection because connect method is not part of public API for BluetoothA2dp.
   - **Parameters description**
     - Takes BluetoothDevice to attempt connection; returns no value but updates state flows on failure.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | device | BluetoothDevice | Target device for reconnect invocation via reflection. |
   - **Returns description**
     - No return; updates state to FAILED on exception.

13. **updateState**
   - Category: private function, state update
   - Lines: 321-335
   - **Description**
     - Centralized state updater: sets internal _state MutableStateFlow and refreshes _connectionInfo with address, resolved device name and codec name (calls resolveName and resolveCodec).
     - Used by event handlers and reconnect logic to keep state and connection metadata consistent.
   - **Parameters description**
     - Accepts an AudioProfileState and optional BluetoothDevice (defaults to connectedDevice) and updates two StateFlows.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | state | AudioProfileState | New audio profile state to apply. |
     | device | BluetoothDevice? | Device associated with the state; defaults to the manager's connectedDevice if not provided. |
   - **Returns description**
     - No return; updates MutableStateFlows.

14. **resolveName**
   - Category: private function, device metadata
   - Lines: 337-354
   - **Description**
     - Safely resolves a BluetoothDevice's display name considering runtime permission checks; returns "Unknown" if device null, permission denied, or SecurityException occurs.
     - Called by updateState to populate AudioConnectionInfo.deviceName.
   - **Parameters description**
     - Accepts optional BluetoothDevice and returns a String name or fallback.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | device | BluetoothDevice? | Device whose name should be returned; may be null. |
   - **Returns description**
     - Returns device name or "Unknown" when not accessible.

15. **hasConnectPermission**
   - Category: private function, permission check
   - Lines: 356-366
   - **Description**
     - Determines whether BLUETOOTH_CONNECT permission is granted on devices running Android S or newer. For older SDK versions, returns true because the runtime permission does not apply.
     - Used before calling A2DP proxy methods or reading device attributes.
   - **Parameters description**
     - No parameters; uses context and Build.VERSION.SDK_INT to decide.
   - **Returns description**
     - Returns Boolean true when BLUETOOTH_CONNECT is granted (or not required by SDK), false otherwise.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | hasPermission | Boolean | True if permission is present (or not required), false otherwise. |

16. **destroy**
   - Category: public function, cleanup
   - Lines: 368-381
   - **Description**
     - Cleans up manager resources: cancels ongoing reconnect job, closes A2DP profile proxy via bluetoothAdapter.closeProfileProxy, and cancels the manager coroutine scope.
     - Intended to be called when the manager is no longer needed to release system resources and stop background coroutines.
   - **Parameters description**
     - No parameters; uses class-level variables to perform cleanup.
   - **Returns description**
     - No return; performs resource cleanup and cancels coroutines.


**Configuration References**
1. **Build.VERSION.SDK_INT**
   - Line: 82,358
   - **What it does:**
     - Used to branch behavior based on Android SDK level: resolveCodec returns SBC immediately for pre-Q devices, and hasConnectPermission bypasses runtime permission checks on versions before S.
     - Impacts whether reflection is used for codec resolution and whether BLUETOOTH_CONNECT permission must be checked at runtime.
   - **Default value**
     - N/A

2. **Manifest.permission.BLUETOOTH_CONNECT**
   - Line: 363,364
   - **What it does:**
     - Checked by hasConnectPermission() to determine if the app can access Bluetooth device and profile information on Android S+.
     - Controls whether A2DP proxy and device name/codec resolution are attempted.
   - **Default value**
     - N/A


**Code Walkthroughs**
1. **Lines:** 86-101
   - **What it does**
     - Uses reflection to call a2dp.getCodecStatus(device) and then getCodecConfig() and getCodecType() on the returned objects; each reflective call is null-checked and may short-circuit to return "Unknown".
     - This sequence extracts a numeric codec type from hidden APIs only available at runtime on certain Android versions.
   - **Why it matters**
     - Reflection is used because the codec status/config APIs are not part of the public SDK surface; this is a brittle area that can fail across OEM implementations, hence the defensive null checks and exception handling.

2. **Lines:** 103-123
   - **What it does**
     - Maps numeric codecType constants (from BluetoothCodecConfig) to user-friendly codec names such as SBC, AAC, aptX, aptX HD, and LDAC. Unknown numeric values are returned as "Unknown (value)".
     - Suppresses deprecation warnings to use older BluetoothCodecConfig constants where necessary.
   - **Why it matters**
     - Mapping uses constants that may change across API levels; the code provides readable labels for common codecs.

3. **Lines:** 303-308
   - **What it does**
     - Uses reflection to obtain a 'connect' method on the A2DP proxy and invoke it with the BluetoothDevice to perform a reconnect operation.
     - This is necessary because the connect API is not public on BluetoothA2dp in some SDK levels, so reflection is used to attempt a connection.
   - **Why it matters**
     - Reflective connect invocation is inherently fragile and may fail; the method catches exceptions and emits a FAILED state when invocation is unsuccessful.

4. **Lines:** 255-257
   - **What it does**
     - Short-circuits reconnect scheduling if isIntentionalDisconnect is true, preventing automatic reconnect attempts after an intentional disconnect.
     - This guard avoids attempting reconnection when the user or caller explicitly disconnected the device.
   - **Why it matters**
     - This is a behavior control flag ensuring reconnect attempts only occur for unintentional disconnects.

5. **Lines:** 358-365
   - **What it does**
     - Checks Android SDK version and, for Android S and above, queries ContextCompat.checkSelfPermission for BLUETOOTH_CONNECT permission before allowing profile operations.
     - Older Android versions bypass the runtime permission check and return true.
   - **Why it matters**
     - Runtime permission handling changed on Android S; code must account for this to avoid SecurityExceptions when accessing Bluetooth APIs.


**Style Conventions**
1. **Lines:** 78-129
   - **Guideline**
     - Suppresses lint and deprecation warnings using @SuppressLint and @Suppress annotations where reflection and deprecated constants are used.
     - Large try/catch blocks intentionally swallow exceptions and return 'Unknown' strings as fallbacks.
   - **Rationale**
     - Suppression applied because the code relies on hidden APIs and constants that may be flagged by static analysis but are used defensively.

2. **Lines:** 49-56
   - **Guideline**
     - Uses Kotlin coroutines with a dedicated managerScope (SupervisorJob + IO dispatcher) and exposes state via Kotlin Flow (MutableStateFlow -> asStateFlow public).
     - Naming convention uses underscore prefix for private MutableStateFlow backing properties (_state, _connectionInfo) and public immutable exposures (state, connectionInfo).
   - **Rationale**
     - This is a common Kotlin pattern for immutable external state exposure and structured concurrency.

3. **Lines:** 51-51
   - **Guideline**
     - Marks isIntentionalDisconnect as @Volatile to ensure visibility across threads.
     - AtomicInteger used for reconnectAttempts to allow thread-safe increment and reads.
   - **Rationale**
     - Ensures concurrency-safe access to flags and counters used by coroutines and potentially multiple threads.


**Event Handling**
1. **A2DP service listener**
   - Lines: 130-160
   - **Trigger Type:** Android Bluetooth framework (BluetoothManager via getProfileProxy)
   - **Behavior**
     - Implements BluetoothProfile.ServiceListener to receive callbacks when the A2DP proxy service is connected or disconnected. On service connected, sets the a2dp proxy and inspects currently connected devices. On service disconnected, clears the proxy, updates state to DISCONNECTED and re-requests the A2DP proxy to attempt recovery.
   - **Impact**
     - Triggers internal state updates, discovery of currently connected devices, and automatic re-requesting of profile proxy to self-recover from service disconnections.

2. **onA2dpConnectionStateChanged**
   - Lines: 192-228
   - **Trigger Type:** A2DP connection state change callbacks (intended to be called by higher-level Bluetooth event handlers)
   - **Behavior**
     - Handles device-level A2DP connection state transitions: sets CONNECTING/CONNECTED/DISCONNECTED states, resets reconnectAttempts on successful connection, updates connectedDevice, and schedules reconnect attempts on unintentional disconnects.
     - Designed to be invoked when the A2DP connection state changes (via external receiver or directly by the Bluetooth framework integration).
   - **Impact**
     - Drives state flow updates and reconnect behavior; affects UI/consumers observing connectionInfo and state flows.

3. **onA2dpPlayingStateChanged**
   - Lines: 230-253
   - **Trigger Type:** A2DP playback state notifications (BluetoothA2dp constants)
   - **Behavior**
     - Handles playback state changes in the A2DP profile and transitions between PLAYING and CONNECTED states, which allows consumers to distinguish active playback from idle connection.
     - Should be called where playback state events are available.
   - **Impact**
     - Updates user-visible playback vs connected state; no side effects besides state flow updates.
