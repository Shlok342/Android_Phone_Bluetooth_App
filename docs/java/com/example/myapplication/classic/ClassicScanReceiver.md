**FileName:** ClassicScanReceiver.kt   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/java/com/example/myapplication/classic/ClassicScanReceiver.kt   
**Tags:** bluetooth, broadcast-receiver, permissions, pairing, kotlin   

**File Summary**
ClassicScanReceiver is a Kotlin BroadcastReceiver that listens for Bluetooth classic scan and pairing-related broadcasts. It maintains and updates an in-memory list and map of discovered Classic-capable Bluetooth devices, handles discovery lifecycle events (found, discovery finished), pairing requests, and bond state changes, and communicates updates via callback lambdas. The class accounts for Android API differences and runtime permission constraints when reading Bluetooth device properties.

**Function Summaries**
1. **ClassicScanReceiver constructor**
   - Category: Class constructor, Initialization
   - Lines: 16-21
   - **Description**
     - Constructs an instance of ClassicScanReceiver and captures references to a mutable device list and device map used to store discovered devices.
     - Accepts lambdas to check permissions, notify when the device list changes, and to surface status updates. The receiver relies entirely on these supplied mutable collections and callbacks rather than persisting state itself.
   - **Parameters description**
     - Initializes the receiver with collections to store devices and three callbacks: permission checking, device-list-change notification, and status updates.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | classicDeviceList | MutableList<ClassicDeviceItem> | A mutable list that holds ClassicDeviceItem entries representing discovered devices. This is modified by the receiver when devices are added or updated. |
     | classicDeviceMap | MutableMap<String, BluetoothDevice> | A map keyed by device MAC address to the BluetoothDevice object for direct access to system BluetoothDevice instances. |
     | permissionChecker | () -> Boolean | A lambda that returns true when the required Bluetooth runtime permission(s) (e.g., BLUETOOTH_CONNECT on newer Android) have been granted. Used to decide whether to access certain properties safely. |
     | onDeviceListChanged | () -> Unit | Callback lambda invoked whenever the displayed device list is modified to let the UI or other layers refresh. |
     | onStatusUpdate | (String) -> Unit | Callback lambda used to surface short textual status updates (e.g., pairing state) to the caller/UI. |
   - **Returns description**
     - No return value; constructs the BroadcastReceiver instance.

2. **isProbablyClassicCapable**
   - Category: Helper function, Predicate
   - Lines: 22-61
   - **Description**
     - Determines whether a given BluetoothDevice is likely to support Classic (BR/EDR) operations based on device.type and bluetoothClass heuristics.
     - Handles API-level and permission differences: for Android S+ it first consults the provided permissionChecker to decide if type can be read; also catches SecurityException to fail-safe return false.
   - **Parameters description**
     - Receives a BluetoothDevice and inspects its type and bluetoothClass to infer Classic capability while being cautious about permissions and API level differences.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | device | BluetoothDevice | The BluetoothDevice instance being evaluated for Classic capability. |
   - **Returns description**
     - Returns a Boolean indicating whether the device is probably Classic-capable. On permission errors or ambiguous types without bluetoothClass, returns false.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | Boolean | Boolean | True when the device is inferred to support Classic (DEVICE_TYPE_CLASSIC or DEVICE_TYPE_DUAL, or when LE/UNKNOWN but bluetoothClass is present); false otherwise. |

3. **onReceive**
   - Category: BroadcastReceiver override, Event handler
   - Lines: 63-201
   - **Description**
     - Main broadcast handler that reacts to Bluetooth intents: device found during discovery, discovery finished, pairing requests, and bond state changes.
     - Extracts BluetoothDevice extras in an API-safe way, uses permissionChecker to avoid unauthorized property access, updates in-memory collections and emits callbacks for UI/status updates.
   - **Parameters description**
     - Standard BroadcastReceiver onReceive parameters: a Context and the Intent containing Bluetooth broadcast action and extras.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | context | Context | Android Context used for resources and creating Toast messages. |
     | intent | Intent | The broadcast Intent containing the action and extras describing the Bluetooth event. |
   - **Returns description**
     - No return value; side-effects include mutating device collections and invoking callbacks.

4. **ACTION_FOUND handling**
   - Category: Event branch, Discovery handling
   - Lines: 66-138
   - **Description**
     - Handles BluetoothDevice.ACTION_FOUND: extracts the BluetoothDevice from the intent, checks if it's probably Classic-capable, and then either updates an existing entry or adds a new ClassicDeviceItem.
     - Takes care to respect runtime permission constraints when reading device properties (type, name) and logs discovery details. On improvements to name/type, it replaces list entries and updates the map and invokes the onDeviceListChanged callback.
   - **Parameters description**
     - Reads the BluetoothDevice extra and some extras like device type when allowed by permissions; otherwise uses safe fallbacks.
   - **Returns description**
     - No return value; modifies classicDeviceList/classicDeviceMap and calls onDeviceListChanged as needed.

5. **ACTION_DISCOVERY_FINISHED handling**
   - Category: Event branch, Discovery lifecycle
   - Lines: 140-149
   - **Description**
     - Handles BluetoothAdapter.ACTION_DISCOVERY_FINISHED: shows a short Toast indicating scan completion and logs the final device count.
     - No mutation of lists here; purely UI/logging feedback to indicate discovery has ended.
   - **Parameters description**
     - No explicit parameters read other than action; uses context for UI.
   - **Returns description**
     - No return; emits a Toast and a debug log.

6. **ACTION_PAIRING_REQUEST handling**
   - Category: Event branch, Pairing request
   - Lines: 150-170
   - **Description**
     - Handles BluetoothDevice.ACTION_PAIRING_REQUEST: extracts the device and pairing variant value and maps the variant to a human-readable pairingType string.
     - Uses onStatusUpdate to surface a localized status message (combining pairingType and device address) using a string resource.
   - **Parameters description**
     - Reads device extra and EXTRA_PAIRING_VARIANT from the intent to decide how to present the pairing prompt type.
   - **Returns description**
     - No return; invokes onStatusUpdate with a composed status string.

7. **ACTION_BOND_STATE_CHANGED handling**
   - Category: Event branch, Bond state handling
   - Lines: 172-199
   - **Description**
     - Handles BluetoothDevice.ACTION_BOND_STATE_CHANGED: reads bond state and device extras; on successful bonding, refreshes the device name in the list if available and sends a paired/connecting status; on bond removed, sends pairing_failed status.
     - Uses permission-aware access to device.name and updates the device list and triggers onDeviceListChanged when appropriate.
   - **Parameters description**
     - Reads extras EXTRA_BOND_STATE and EXTRA_DEVICE from the intent; inspects the bond state constants.
   - **Returns description**
     - No return; may update device list and invoke onDeviceListChanged and onStatusUpdate.


**Configuration References**
1. **Manifest.permission.BLUETOOTH_CONNECT**
   - Line: 63,73,83,90,189
   - **What it does:**
     - Used implicitly via @RequiresPermission annotation on onReceive and also guarded at runtime through permissionChecker calls. Controls whether device properties like name and type can be accessed on Android S+ and later.
     - When not granted, the code falls back to safe defaults (DEVICE_TYPE_UNKNOWN, "Unknown") to avoid SecurityException and to respect privacy constraints.
   - **Default value**
     - N/A

2. **Build.VERSION.SDK_INT checks**
   - Line: 27,67,83,151,178
   - **What it does:**
     - Branch behavior based on Android SDK level: choose modern getParcelableExtra overloads on TIRAMISU+, and restrict access to device properties on S+ when permissions are missing.
     - These conditionals ensure API compatibility and correct permission handling across Android releases.
   - **Default value**
     - N/A

3. **String resources (R.string.with, R.string.paired_connecting, R.string.pairing_failed)**
   - Line: 167,193,196
   - **What it does:**
     - Used to generate localized status messages for pairing UI: shows pairing type with an address, indicates paired/connecting and pairing failure states.
     - They externalize user-facing text for localization and consistency.
   - **Default value**
     - N/A


**Code Walkthroughs**
1. **Lines:** 24-31
   - **What it does**
     - Guard that returns false when Build.VERSION >= S and permissionChecker indicates the BLUETOOTH_CONNECT permission is not granted.
     - Prevents reading sensitive device properties that require runtime permission on Android S and above.
   - **Why it matters**
     - This conditional centralizes the runtime-permission-aware early-exit and avoids throwing SecurityException later when accessing device.type or device.name.

2. **Lines:** 34-55
   - **What it does**
     - Switch/case on device.type to classify Classic capability: CLASSIC/DUAL => true; LE or UNKNOWN => true only if bluetoothClass exists; else false.
     - Provides heuristic to handle temporary or incomplete device.type values reported during discovery.
   - **Why it matters**
     - Determining Classic capability is non-trivial because Android reports LE/UNKNOWN frequently during discovery; bluetoothClass presence is used as a heuristic to avoid false negatives.

3. **Lines:** 57-60
   - **What it does**
     - Catches SecurityException and returns false to fail-safe when permission-restricted fields are accessed.
     - Prevents receiver from throwing and crashing when permissions are not present.
   - **Why it matters**
     - Access to BluetoothDevice properties can raise SecurityException on restricted Android versions; this catch ensures robust behavior.

4. **Lines:** 67-69
   - **What it does**
     - Retrieves BluetoothDevice extra in a backwards-compatible manner: uses the typed getParcelableExtra overload on TIRAMISU+ and falls back to the deprecated form otherwise.
     - Suppresses deprecation warnings explicitly for the older API path.
   - **Why it matters**
     - Android changed the getParcelableExtra API; conditional usage prevents class cast issues and keeps code compatible across SDK versions.

5. **Lines:** 71-75
   - **What it does**
     - Computes the device type in a permission-aware way: if no permission, uses DEVICE_TYPE_UNKNOWN fallback; catches SecurityException as a final safeguard.
     - This protects against permission failures when reading device.type on newer Android releases.
   - **Why it matters**
     - Reading device.type without BLUETOOTH_CONNECT may throw; this pattern ensures safe fallback and continued discovery processing.

6. **Lines:** 80-92
   - **What it does**
     - Obtains a device name in a permission-aware manner and falls back to "Unknown" on permission restrictions or exceptions.
     - Account for Build.VERSION and permissionChecker before trying to access device.name.
   - **Why it matters**
     - device.name may be restricted by runtime permissions on Android S+, so the code uses safe fallbacks to avoid exceptions and to provide consistent display text.

7. **Lines:** 100-125
   - **What it does**
     - Checks whether the discovered device already exists in classicDeviceList by address, then conditionally updates the stored name and/or type only when the newly observed data is an improvement.
     - Updates both the list entry (immutably via copy) and the device map and invokes onDeviceListChanged on change.
   - **Why it matters**
     - Avoids unnecessary list updates and UI refreshes by only mutating when new info (real name or non-unknown type) is available; ensures map remains in sync.

8. **Lines:** 129-136
   - **What it does**
     - Adds a new ClassicDeviceItem to the list and stores the corresponding BluetoothDevice in the map, then notifies consumers via onDeviceListChanged.
     - Handles the standard path when a newly discovered device is not already tracked.
   - **Why it matters**
     - This is the canonical insertion flow for new devices discovered during classic scanning.

9. **Lines:** 156-165
   - **What it does**
     - Maps pairing variant integer codes to human-readable strings for status reporting (Enter PIN, Confirm passkey, Confirm pairing, or generic Pairing).
     - Includes a hard-coded numeric constant (6) for PAIRING_VARIANT_CONSENT due to it not being available as a named constant on older SDKs.
   - **Why it matters**
     - Pairing variants are enumerated integers; mapping them to user-facing strings makes status messages meaningful. The numeric literal indicates compatibility handling across SDK definitions.

10. **Lines:** 184-193
   - **What it does**
     - Handles BOND_BONDED by refreshing the corresponding device entry name in the list (permission-aware) and invokes both onDeviceListChanged and an onStatusUpdate indicating paired/connecting.
     - Ensures display names can be updated post-bonding when more privileged information becomes available.
   - **Why it matters**
     - Bonding can reveal a real device name; the receiver updates UI state accordingly to reflect the new information.


**Style Conventions**
1. **Lines:** 24-60
   - **Guideline**
     - Kotlin idioms are used: try/catch returning expressions and use of expression-style when blocks. Catch clauses use a named underscore to intentionally ignore exceptions (catch (_: SecurityException)).
   - **Rationale**
     - This style simplifies permission/error handling and keeps the function concise while explicitly documenting the exception type being ignored.

2. **Lines:** 67-69
   - **Guideline**
     - Uses API-level conditional and @Suppress("DEPRECATION") for the older getParcelableExtra overload for backward compatibility.
     - Maintains clarity by explicitly selecting the modern overload on newer SDKs.
   - **Rationale**
     - Ensures cross-SDK compatibility while keeping the code free of deprecation warnings on older branches.

3. **Lines:** 100-125
   - **Guideline**
     - When updating an existing ClassicDeviceItem, the code uses existing.copy(...) to create an updated immutable copy rather than mutating in place, consistent with data-class immutability practices.
   - **Rationale**
     - This preserves functional-style updates and avoids unexpected side effects on objects assumed to be value-type.


**Event Handling**
1. **Bluetooth broadcasts**
   - Lines: 63-201
   - **Trigger Type:** Android system Broadcast Intents (Bluetooth subsystem)
   - **Behavior**
     - Handles four Bluetooth-related broadcast actions: ACTION_FOUND (device discovered), ACTION_DISCOVERY_FINISHED (discovery ended), ACTION_PAIRING_REQUEST (pairing initiation), and ACTION_BOND_STATE_CHANGED (bond state updates).
     - For ACTION_FOUND, the handler processes discovery results, filters for Classic-capable devices, updates internal collections, and notifies listeners. For pairing-related events, it uses onStatusUpdate to surface pairing flows and updates list entries on successful bonding.
   - **Impact**
     - Triggers mutations to in-memory device collections (adds/updates), emits UI-facing status updates via callbacks, and produces a Toast/log when discovery ends.
