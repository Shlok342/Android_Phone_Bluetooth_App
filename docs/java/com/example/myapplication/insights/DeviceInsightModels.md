**FileName:** DeviceInsightModels.kt   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/java/com/example/myapplication/insights/DeviceInsightModels.kt   
**Tags:** model, bluetooth, kotlin, insights, mutable-state   

**File Summary**
Kotlin data model definitions for device insight telemetry used by the app. This file declares immutable container types (data classes) representing a device session, discovered services, characteristics, and timestamped events, including several mutable collections for runtime accumulation of insights and an imported AudioProfileState type.

**Function Summaries**
1. **DeviceInsightSession**
   - Category: data class, model
   - Lines: 7-27
   - **Description**
     - Represents a single connection/session for a device, capturing identifying information (name, MAC, transport), timing (connectedAt, optional disconnectedAt), connection metrics (mtu, rssi), audio-related flags and profiles, and runtime-collected lists of services and events.
     - Holds mutable collections (audioProfiles map, services list, events list) that are expected to be updated over the lifetime of the session instance.
   - **Parameters description**
     - Constructor parameters provide identifying fields, optional connection metadata, audio state flags, and default-initialized mutable collections for profiles, services, and events.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | deviceName | String | Human-readable name of the device under this session. |
     | macAddress | String | MAC address identifying the device. |
     | transportType | String | Transport type used (e.g., BLE/classic). |
     | connectedAt | Long | Epoch timestamp (ms) when the session/connection started. |
     | disconnectedAt | Long? | Optional epoch timestamp (ms) when the session ended; null if still connected. |
     | disconnectReason | String? | Optional textual reason for disconnection. |
     | mtu | Int? | Optional negotiated MTU for the connection. |
     | rssi | Int? | Optional RSSI measurement for the device. |
     | isAudioDevice | Boolean | Flag indicating whether the device is considered an audio device. |
     | isAudioPlaying | Boolean | Flag indicating whether audio playback is active during the session. |
     | audioProfiles | MutableMap<String, AudioProfileState> | Mutable map keyed by profile name/identifier to AudioProfileState instances; initialized empty by default and intended to be updated during session. |
     | services | MutableList<ServiceInsight> | Mutable list of discovered ServiceInsight objects for the device session; starts empty. |
     | events | MutableList<DeviceEvent> | Mutable list of DeviceEvent objects capturing timestamped messages for the session; starts empty. |
   - **Returns description**
     - This is a data container type (no function return). Instances hold session state and are typically created and later mutated via their mutable collections.

2. **ServiceInsight**
   - Category: data class, model
   - Lines: 29-33
   - **Description**
     - Models a discovered Bluetooth service for a device, capturing a service name, UUID, and a mutable list of characteristics.
     - Intended to be constructed and then have its characteristics list populated as discovery proceeds.
   - **Parameters description**
     - Constructor parameters include service identification and an initially empty mutable list of CharacteristicInsight instances.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | serviceName | String | Human-readable name of the service. |
     | serviceUuid | String | Service UUID string identifier. |
     | characteristics | MutableList<CharacteristicInsight> | Mutable list to accumulate characteristics discovered under this service. |
   - **Returns description**
     - Data container representing a Bluetooth service; no runtime return values.

3. **CharacteristicInsight**
   - Category: data class, model
   - Lines: 35-39
   - **Description**
     - Represents a single characteristic within a service, storing a name, UUID, and a list of properties (as strings).
     - Used within ServiceInsight.characteristics to describe each characteristic's metadata.
   - **Parameters description**
     - Constructor takes identifying and descriptive fields for a characteristic: name, uuid, and properties list.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | characteristicName | String | Human-readable name of the characteristic. |
     | uuid | String | UUID string of the characteristic. |
     | properties | List<String> | List of property names supported by the characteristic (e.g., read, write, notify). |
   - **Returns description**
     - Data container representing a characteristic; no runtime return values.

4. **DeviceEvent**
   - Category: data class, model, event
   - Lines: 41-44
   - **Description**
     - Simple event holder containing a timestamp and a message string, used to record events or log entries related to a DeviceInsightSession.
     - Designed to be appended to DeviceInsightSession.events to provide a chronological trace of notable occurrences.
   - **Parameters description**
     - Constructor includes a timestamp and message string describing the event.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | timestamp | Long | Epoch timestamp (ms) when the event occurred. |
     | message | String | Human-readable message describing the event. |
   - **Returns description**
     - Data container for a single event entry; no runtime return values.


**Code Walkthroughs**
1. **Lines:** 22-24
   - **What it does**
     - Declares audioProfiles as a mutable map from String to AudioProfileState and initializes it to an empty mutable map.
     - Relies on an external/internal type AudioProfileState (imported at line 5) to represent per-profile audio states.
   - **Why it matters**
     - Mutable map is part of the session's runtime state and will be updated during the session; highlights the dependency on AudioProfileState for understanding stored values.

2. **Lines:** 25-26
   - **What it does**
     - Initializes services and events as empty mutable lists to be populated at runtime.
     - These defaults imply instances are intended to be mutated and accumulated rather than recreated.
   - **Why it matters**
     - Mutable collections inside a data class affect how instances are shared and modified; important for reviewers/new developers to recognize mutability semantics.

3. **Lines:** 1-1
   - **What it does**
     - File header comment includes a file path that differs from the declared package and repository path.
     - This mismatch may be historical or a copy/paste artifact and does not affect runtime but may confuse contributors.
   - **Why it matters**
     - Clarifies that the comment path differs from actual package declaration and file location, relevant to onboarding and locating source.


**Style Conventions**
1. **Lines:** 1-3
   - **Guideline**
     - Top-of-file comment (line 1) shows a different file path than the package declaration and provided file path, which may be a leftover from refactoring.
     - Package declaration on line 3 follows standard Kotlin package naming; data classes use concise primary-constructor style.
   - **Rationale**
     - The header mismatched path could create confusion when searching for the file or its history; consistent metadata helps onboarding.

2. **Lines:** 7-44
   - **Guideline**
     - All types are implemented as Kotlin data classes with constructor properties and default values; mutable fields use Kotlin mutable collection types and nullable primitives where appropriate.
     - Naming is consistent (PascalCase for types, camelCase for properties) and uses explicit types for clarity.
   - **Rationale**
     - Consistency with Kotlin idioms eases readability and maintenance for new contributors.
