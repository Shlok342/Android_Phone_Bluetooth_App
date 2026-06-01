**FileName:** DeviceInsightFormatter.kt   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/java/com/example/myapplication/insights/DeviceInsightFormatter.kt   
**Tags:** insights, formatting, bluetooth, utilities   

**File Summary**
DeviceInsightFormatter is a Kotlin singleton (object) responsible for producing a human-readable, multi-section textual report of a Bluetooth device session. It formats timestamps, audio profile states, signal quality from RSSI, GATT services and characteristics, event timelines, and disconnection/duration information into a single String for display or logging. The file depends on a sealed AudioProfileState type and several domain model fields on DeviceInsightSession, and uses JDK date formatting utilities.

**Function Summaries**
1. **timeFormat**
   - Category: property,lazy
   - Lines: 10-10
   - **Description**
     - Holds a lazily-initialized SimpleDateFormat configured for HH:mm:ss using the device default Locale.
     - Delays creation until first use to avoid unnecessary initialization cost at class load time.
   - **Returns description**
     - A SimpleDateFormat instance used by formatTime to format timestamps.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | SimpleDateFormat | java.text.SimpleDateFormat | Formatter configured with pattern "HH:mm:ss" and Locale.getDefault(). |

2. **format**
   - Category: function
   - Lines: 12-153
   - **Description**
     - Builds and returns a multiline textual report describing a DeviceInsightSession.
     - Organizes output into sections: connection header/time, device information, optional audio details, GATT services and characteristics, optional event timeline, and optional disconnection summary with duration.
   - **Parameters description**
     - Accepts a single DeviceInsightSession domain object containing device metadata, lists of services/characteristics, audio profile states, events, and timestamps.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | session | DeviceInsightSession | Container for the session data to be described; fields accessed include connectedAt, deviceName, macAddress, transportType, rssi, mtu, isAudioDevice, isAudioPlaying, audioProfiles, services, events, disconnectedAt, and disconnectReason. |
   - **Returns description**
     - A single String containing the formatted report for the provided session.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | report | String | Complete multi-line textual representation of the session suitable for display or logging. |

3. **formatAudioState**
   - Category: function
   - Lines: 155-182
   - **Description**
     - Converts an AudioProfileState value into a human-readable description.
     - Handles sealed variants including simple enum-like states and data-carrying variants (RECONNECTING and FAILED) to include attempt count or failure reason where present.
   - **Parameters description**
     - Takes an AudioProfileState sealed type value and returns a descriptive string.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | state | AudioProfileState | Sealed-state instance representing the audio profile status to format. |
   - **Returns description**
     - A String describing the audio state, including details for data-bearing variants.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | description | String | Human-readable description such as "Idle", "Connecting", "Playing Audio", or e.g. "Reconnecting (Attempt X)" or "Failed (reason)". |

4. **formatTime**
   - Category: function
   - Lines: 184-189
   - **Description**
     - Formats a timestamp (milliseconds since epoch) into an HH:mm:ss string using the shared timeFormat formatter.
     - Is used throughout the report to display connection, event and disconnection times consistently.
   - **Parameters description**
     - Accepts a millisecond epoch timestamp and returns a time string formatted per the object's timeFormat.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | timestamp | Long | Epoch milliseconds to be converted to a short time string. |
   - **Returns description**
     - A formatted time string in HH:mm:ss representing the provided timestamp.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | timeString | String | Formatted time representation derived from timestamp. |

5. **getSignalQuality**
   - Category: function
   - Lines: 191-209
   - **Description**
     - Maps a numeric RSSI value (dBm) to a qualitative signal description.
     - Implements thresholds to categorize signal as Excellent, Good, Weak, or Very Weak.
   - **Parameters description**
     - Takes an integer RSSI value and returns a human-readable quality label.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | rssi | Int | RSSI reading in dBm used to determine connection quality. |
   - **Returns description**
     - A qualitative signal quality string based on RSSI thresholds.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | quality | String | One of "Excellent", "Good", "Weak", or "Very Weak" corresponding to the provided RSSI ranges. |

6. **formatDuration**
   - Category: function
   - Lines: 211-231
   - **Description**
     - Converts a duration given in seconds into a compact hours/minutes/seconds string (e.g., "1h 2m 3s").
     - Omits hours or minutes segments when their values are zero for concise output.
   - **Parameters description**
     - Accepts duration in seconds and composes a human-readable duration string.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | seconds | Long | Duration value in seconds to be formatted into hours, minutes, and seconds. |
   - **Returns description**
     - A String with hours, minutes, and seconds parts as applicable.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | durationString | String | Formatted duration such as "2h 3m 4s" or "5m 6s" or "7s" depending on magnitude. |


**Configuration References**
1. **Locale.getDefault()**
   - Line: 10
   - **What it does:**
     - The formatting of times uses the device default Locale, which influences localized time formatting (e.g., decimal separators or localized strings if pattern changed).
   - **Default value**
     - N/A

2. **SimpleDateFormat pattern "HH:mm:ss"**
   - Line: 10,188
   - **What it does:**
     - Controls the exact time output format used throughout the report (hour:minute:second).
   - **Default value**
     - "HH:mm:ss"


**Code Walkthroughs**
1. **Lines:** 16-18
   - **What it does**
     - Prepends the report with a connection header showing the formatted connect time and device name.
   - **Why it matters**
     - Uses formatTime(session.connectedAt) to present a localized time string; important as the first visible line summarizing session start.

2. **Lines:** 30-37
   - **What it does**
     - Conditionally appends RSSI information and a derived connection quality only when the session.rssi value is present (non-null).
   - **Why it matters**
     - Shows a defensive use of Kotlin nullable handling via let to omit RSSI and quality lines when RSSI is not available.

3. **Lines:** 60-65
   - **What it does**
     - Iterates over audioProfiles, destructuring each entry into profile and state, then prints a formatted line per profile.
   - **Why it matters**
     - Destructuring in the lambda implicitly depends on audioProfiles being a collection of pairs or map entries (profile -> state); understanding the data shape is necessary to modify audio reporting.

4. **Lines:** 86-107
   - **What it does**
     - For each GATT service, prints service metadata and iterates its characteristics, showing each characteristic's name, UUID and joined properties.
   - **Why it matters**
     - Characteristic properties are joined with " | "; modifications to property presentation or indentation are localized here.

5. **Lines:** 144-149
   - **What it does**
     - Computes connection duration in seconds by subtracting connectedAt from disconnectedAt and formatting it into a human-friendly string.
   - **Why it matters**
     - Performs arithmetic on epoch millisecond timestamps and divides by 1000; correct units and null-safety are important to preserve accurate durations.

6. **Lines:** 176-181
   - **What it does**
     - Handles data-bearing sealed-state variants of AudioProfileState to include attempt count or failure reason when available.
   - **Why it matters**
     - Shows extraction of runtime data from sealed subclasses via pattern matching (is Type) — important when extending AudioProfileState with new data variants.

7. **Lines:** 219-230
   - **What it does**
     - Builds the duration string by appending hours, minutes, and remaining seconds selectively to avoid zero-valued segments.
   - **Why it matters**
     - Uses buildString and conditional appends for compact output; central location for any changes to duration formatting rules.


**Style Conventions**
1. **Lines:** 12-153
   - **Guideline**
     - Uses Kotlin idioms: object singleton, lazy property, StringBuilder with appendLine for readable multi-line assembly, safe-call let for nullable fields, forEach with lambda destructuring.
     - Separation into small private helper functions (formatAudioState, formatTime, getSignalQuality, formatDuration) improves readability and single responsibility.
   - **Rationale**
     - Consistent use of idiomatic Kotlin constructs makes the file easy to read and maintain for Kotlin developers.

2. **Lines:** 155-182
   - **Guideline**
     - Pattern matching on a sealed class via when with both direct enum-like entries and 'is' checks for data-bearing variants.
     - String templates are used consistently for inline interpolation.
   - **Rationale**
     - Enables clear mapping from domain states to user-presentable text and makes adding new state variants straightforward.
