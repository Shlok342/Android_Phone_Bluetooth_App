**FileName:** DeviceInsightManager.kt   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/java/com/example/myapplication/insights/DeviceInsightManager.kt   
**Tags:** bluetooth, gatt, audio, telemetry, singleton   

**File Summary**
Singleton Kotlin object that tracks runtime insights for Bluetooth devices in the app. It maintains in-memory sessions per device, records app-level and per-device events, captures GATT service/characteristic details, MTU/RSSI updates, disconnect info, and some audio capability metadata for connected devices. The file relies on Android Bluetooth and audio APIs and internal utilities to translate UUIDs and characteristic properties.

**Function Summaries**
1. **DeviceInsightManager (singleton state)**
   - Category: singleton, state
   - Lines: 12-15
   - **Description**
     - Defines a single shared manager object for collecting and exposing device insights across the app.
     - Holds two primary in-memory collections: sessions (map of MAC -> DeviceInsightSession) and appEvents (time-ordered list of DeviceEvent).
   - **Parameters description**
     - No parameters; this is module-level state.
   - **Returns description**
     - No return values; state is mutated and read by other functions.

2. **onAppEvent**
   - Category: event handler, telemetry
   - Lines: 17-20
   - **Description**
     - Record a timestamped application-level event message in the appEvents list.
     - Keeps the list capped at 200 entries to limit memory growth.
   - **Parameters description**
     - Accepts a single message string describing the app-level event.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | message | String | Human-readable event message to store in the global appEvents list with current timestamp. |
   - **Returns description**
     - No return; the function appends to internal state.

3. **getAppEvents**
   - Category: accessor
   - Lines: 22-22
   - **Description**
     - Return the current list of recorded application events.
     - Provides callers a read-only List<DeviceEvent> view of appEvents.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - Returns the list of DeviceEvent objects currently stored.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | List<DeviceEvent> | List<DeviceEvent> | An ordered list of application-level events with timestamps and messages. |

4. **onDeviceConnected**
   - Category: event handler, session creation
   - Lines: 24-37
   - **Description**
     - Create and store a new DeviceInsightSession when a Bluetooth device connects.
     - Collects device metadata (name with permission-safe access, MAC address), transport type, connection timestamp, and detects audio capabilities for the device.
   - **Parameters description**
     - Takes the connected BluetoothDevice, a transport string (e.g., LE/classic), and Android Context for system services.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | device | BluetoothDevice | Android BluetoothDevice representing the connected device; used to read name and address. |
     | transport | String | String describing the transport type for the connection, stored in the session.transportType field. |
     | context | Context | Android Context used to resolve system services (AudioManager) during audio capability detection. |
   - **Returns description**
     - No return; a DeviceInsightSession is created and saved to the internal sessions map keyed by device.address.

5. **onGattServicesDiscovered**
   - Category: gatt processing, data extraction
   - Lines: 39-60
   - **Description**
     - Populate the DeviceInsightSession.services list with discovered GATT services and their characteristics when a GATT discovery completes.
     - Resolves human-readable service/characteristic names using BluetoothUuidRegistry and parses characteristic properties using CharacteristicPropertyParser.
   - **Parameters description**
     - Accepts the BluetoothDevice (to locate the session) and BluetoothGatt which contains discovered services.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | device | BluetoothDevice | BluetoothDevice used to find the corresponding session in the sessions map. |
     | gatt | BluetoothGatt | Contains the discovered services and characteristics; iterated to build ServiceInsight and CharacteristicInsight objects. |
   - **Returns description**
     - No return; updates the session.services collection in-place.

6. **updateMtu**
   - Category: updater
   - Lines: 62-64
   - **Description**
     - Update the stored MTU value for a device session when the MTU changes.
   - **Parameters description**
     - Takes the BluetoothDevice whose session should be updated and the new MTU int value.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | device | BluetoothDevice | Device whose session will be updated (identified by device.address). |
     | mtu | Int | The negotiated MTU size to store on the session. |
   - **Returns description**
     - No return; updates session.mtu if session exists.

7. **updateRssi**
   - Category: updater
   - Lines: 66-68
   - **Description**
     - Update the RSSI value on a session identified by a MAC address string.
   - **Parameters description**
     - Takes the deviceAddress string key and an integer RSSI value.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | deviceAddress | String | MAC address string used to locate the session in the sessions map. |
     | rssi | Int | Received signal strength indicator value to store on the session. |
   - **Returns description**
     - No return; updates session.rssi when session exists.

8. **addDeviceEvent**
   - Category: event handler
   - Lines: 70-72
   - **Description**
     - Add a timestamped DeviceEvent message to a specific device's session event list.
     - Does nothing if the session is not present.
   - **Parameters description**
     - Takes a deviceAddress string and event message to append to that session's events list.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | deviceAddress | String | MAC address string to locate the device session. |
     | message | String | The event message to append to the session events list. |
   - **Returns description**
     - No return; appends DeviceEvent to session.events if session exists.

9. **onDisconnected**
   - Category: event handler
   - Lines: 74-79
   - **Description**
     - Record disconnect timestamp and reason on the device's session when a device disconnects.
     - Leaves session in map but marks it with disconnectedAt and disconnectReason.
   - **Parameters description**
     - Takes deviceAddress and a disconnect reason string.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | deviceAddress | String | MAC address string used to locate the session. |
     | reason | String | Human-readable reason explaining why the device disconnected. |
   - **Returns description**
     - No return; mutates session fields if session exists.

10. **getSession**
   - Category: accessor
   - Lines: 81-81
   - **Description**
     - Fetch a DeviceInsightSession by MAC address.
     - Returns null when no session exists for the provided MAC.
   - **Parameters description**
     - Accepts a MAC address string and returns the matching session or null.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | macAddress | String | MAC address string key to look up a session. |
   - **Returns description**
     - Returns the DeviceInsightSession for the given MAC or null.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | DeviceInsightSession? | DeviceInsightSession? | The session object if found, otherwise null. |

11. **getAllSessions**
   - Category: accessor
   - Lines: 82-82
   - **Description**
     - Return a list of all current DeviceInsightSession objects stored in the manager.
     - Provides a snapshot (values.toList()) of sessions map values.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - Returns a List of DeviceInsightSession instances currently held in memory.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | List<DeviceInsightSession> | List<DeviceInsightSession> | List of session objects representing all tracked devices. |

12. **detectAudioCapabilities**
   - Category: utility, audio detection
   - Lines: 84-92
   - **Description**
     - Determine whether a connected Bluetooth device is an audio device and populate relevant audio fields on the session.
     - Safely accesses bluetoothClass (handling potential SecurityException), checks major device class for AUDIO_VIDEO, sets an A2DP profile state, and queries the system AudioManager to detect if audio is currently playing.
   - **Parameters description**
     - Takes a BluetoothDevice, the DeviceInsightSession to populate, and Android Context for AudioManager access.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | device | BluetoothDevice | Used to read the device.bluetoothClass (permission-protected) to infer major device class. |
     | session | DeviceInsightSession | Session object to update with isAudioDevice, audioProfiles, and isAudioPlaying. |
     | context | Context | Android Context used to obtain AudioManager for checking isMusicActive. |
   - **Returns description**
     - No return; mutates the provided session with audio-related metadata.


**Code Walkthroughs**
1. **Lines:** 26-30
   - **What it does**
     - Attempt to read BluetoothDevice.name but catch SecurityException to handle cases where runtime permission prevents access.
   - **Why it matters**
     - Reading device.name can throw SecurityException on newer Android versions if location/bluetooth permissions are missing; the code substitutes a safe string to avoid crashing and to indicate the permission issue.

2. **Lines:** 18-19
   - **What it does**
     - Append a new app-level event with a timestamp and enforce a maximum buffer size of 200 by removing the oldest event when the cap is exceeded.
   - **Why it matters**
     - Simple in-process circular buffering to prevent unbounded memory growth of appEvents.

3. **Lines:** 42-56
   - **What it does**
     - Iterate discovered GATT services and their characteristics, resolving human-readable names via BluetoothUuidRegistry and parsing characteristic properties via CharacteristicPropertyParser.
   - **Why it matters**
     - Mapping raw UUIDs and bitmask properties to descriptive data structures is central to producing useful insights for UI/debugging; these lines bridge raw Bluetooth APIs to the app's insight model.

4. **Lines:** 85-91
   - **What it does**
     - Safe access of device.bluetoothClass with fallback, determination of audio device by majorDeviceClass, assignment of A2DP state, and checking whether music is currently playing via AudioManager.
   - **Why it matters**
     - Combines permission-safe BluetoothClass access and runtime AudioManager query to build an immediate picture of the device's audio capabilities and current playback state.


**Style Conventions**
1. **Lines:** 12-93
   - **Guideline**
     - Kotlin object singleton used for global manager state; functions are defined as top-level members of that object.
     - Concise expression bodies are used for simple getters (single-line functions).
   - **Rationale**
     - The singleton pattern centralizes state and access; expression-bodied functions improve brevity and readability for trivial accessors.

2. **Lines:** 26-30
   - **Guideline**
     - try/catch with ignored exception variable (catch (_: SecurityException)) to explicitly handle permission-related exceptions without logging the exception details.
   - **Rationale**
     - This keeps the code concise and signals intentional suppression of the exception info (permission denial handled by storing 'Permission Denied').
