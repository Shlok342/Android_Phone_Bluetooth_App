**FileName:** MainActivity.kt   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/java/com/example/myapplication/ui/MainActivity.kt   
**Tags:** bluetooth, ble, classic, service, ui   

**File Summary**
MainActivity is the primary Android Activity that drives the app's UI for both BLE and Classic Bluetooth flows. It initializes and binds to two background services (BluetoothService for BLE and ClassicBluetoothService for classic RFCOMM/HFP/A2DP flows), manages device lists and UI controllers, handles permission flow and scanning lifecycle, and coordinates event streams from services to update UI and show live data in a bottom sheet. The file contains multiple lifecycle hooks, service connection handlers, broadcast receiver registration for classic discovery, and multiple places with SDK-version gated permission checks and TODO placeholders for older Android versions.

**Function Summaries**
1. **UI State & Fields**
   - Category: Fields, State
   - Lines: 51-76
   - **Description**
     - Declare and initialize UI state and related fields used across the activity: UI component holder, handlers, refresh scheduling flags, device lists and maps, tab tracking, service start flags, and bottom sheet variables.
     - Provides shared mutable state for scan results (BLE and Classic), adapters, and controllers used to present device lists and live data.
   - **Parameters description**
     - No parameters — these are class-level fields used across methods.

2. **filePickerLauncher**
   - Category: ActivityResultLauncher, File selector callback
   - Lines: 77-90
   - **Description**
     - Registers an Activity Result callback to allow the user to pick a file (GetContent).
     - When a URI is returned, checks that the ClassicBluetoothService is bound and connected and then triggers a file send via classicService.fileTransferManager; otherwise shows a Toast indicating the service or connection is not ready.
   - **Parameters description**
     - No explicit function parameters; callback receives a Uri? from the GetContent contract.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | uri | Uri? | Selected file URI provided by the system picker; null means selection was canceled. |
   - **Returns description**
     - No return value; side effects: shows Toasts or initiates file transfer.

3. **Service & Manager fields**
   - Category: Fields, Service references
   - Lines: 92-101
   - **Description**
     - Declare references and state for BLE and Classic services (BluetoothService, ClassicBluetoothService), binding flags, a coroutine Job for collecting classic service flows, and BleScanManager instance placeholder.
     - These fields are populated when services are bound and are central to service interactions across the activity.
   - **Parameters description**
     - No parameters — class-level declarations.

4. **classicScanReceiver**
   - Category: BroadcastReceiver (Classic scan handler)
   - Lines: 103-115
   - **Description**
     - Constructs a ClassicScanReceiver instance that updates classicDeviceList and classicDeviceMap on discovery events, checks runtime BLUETOOTH_CONNECT permission for behavior, and calls UI update callbacks for device list and status updates.
     - This receiver is later registered to listen for ACTION_FOUND, ACTION_DISCOVERY_FINISHED, ACTION_PAIRING_REQUEST and ACTION_BOND_STATE_CHANGED to support classic discovery flow.
   - **Parameters description**
     - Configured via constructor properties rather than explicit function parameters.

5. **serviceConnection (BLE)**
   - Category: ServiceConnection, Callback
   - Lines: 117-152
   - **Description**
     - Handles binding/unbinding to the BLE BluetoothService. On connection it obtains the service instance, starts BLE scanning (with a small delay), wires up callbacks to update UI on BLE state changes, logs state transitions to SystemTimeline, and sets a handler for onDataReceived to show incoming data in the bottom sheet.
     - On disconnection it clears the service reference and binding flag.
   - **Parameters description**
     - ServiceConnection callbacks receive ComponentName and IBinder from the binding framework.

6. **classicConnection (Classic)**
   - Category: ServiceConnection, Coroutine collectors
   - Lines: 154-242
   - **Description**
     - Handles binding to ClassicBluetoothService. On connection it launches a lifecycleScope coroutine using repeatOnLifecycle to collect multiple flows from the service's connection manager, messages, events, file transfer manager and audio profile manager.
     - Collected events are used to update Classic UI state, log lifecycle messages to SystemTimeline, surface messages in the BLE bottom sheet, and update transfer and audio UI states. On service disconnect it cancels the collector job and clears references.
   - **Parameters description**
     - Callbacks get ComponentName and IBinder from bindService; inside it uses the service.connectionManager and other managers to collect Flows.

7. **onCreate**
   - Category: Lifecycle method, Initialization
   - Lines: 245-396
   - **Description**
     - Activity onCreate: builds UI via MainUiFactory, configures callbacks for UI actions (refresh, stop scan, disconnect, tab switches, show features, connect callbacks), triggers permission checks and service startup, registers the classic broadcast receiver, initializes the BleScanManager with its callbacks, and creates BleUiController and ClassicUiController instances wired to UI elements and service getters.
     - Also forces an initial UI adapter sync and ensures scanning is started or stopped according to active tab state.
   - **Parameters description**
     - savedInstanceState: Bundle? — typical Android lifecycle input to restore state; not heavily used in this method.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | savedInstanceState | Bundle? | System-provided bundle for saved activity state; not used beyond signature. |
   - **Returns description**
     - No return value; this is a lifecycle initializer with side effects: UI, receivers, services and controllers are initialized.

8. **BleScanManager init & onDeviceFound handler**
   - Category: Manager initialization, Callback
   - Lines: 327-368
   - **Description**
     - Instantiate BleScanManager with a permission checker and three callbacks: onDeviceFound (updates bleDeviceList and bleDeviceMap, diffs by address and RSSI/name and batches adapter notifications), onClearDevices (clears lists and cancels pending notification), and onScanStopped (cancels pending notification).
     - This block is responsible for receiving discovery events from BLE scanning and ensuring UI updates are coalesced to avoid excessive adapter.refresh calls.
   - **Parameters description**
     - BleScanManager constructed with lambdas for permission checking and device lifecycle callbacks.

9. **bleUiController creation**
   - Category: Controller initialization
   - Lines: 369-384
   - **Description**
     - Create a BleUiController instance that mediates BLE status display, starting scans, retrieving connected device name/state, pending refresh flags, and data-sheet dismissal.
     - The controller receives callbacks to interact with the activity and service getters for UI reflections.
   - **Parameters description**
     - Supplies UI element references and lambdas to read/modify activity state.

10. **classicUiController creation**
   - Category: Controller initialization
   - Lines: 385-395
   - **Description**
     - Create a ClassicUiController to manage classic-tab UI elements (status, transfer status) and actions like sending a file and dismissing data sheet; it also queries classic connection state and connected device name.
     - Ties classic UI behaviors to the Activity's file picker launcher and classicService getters.
   - **Parameters description**
     - Supplies UI element references and lambdas for classic-specific operations.

11. **checkPermissionsAndStartService**
   - Category: Permissions, Startup
   - Lines: 398-417
   - **Description**
     - Determine the set of runtime permissions required based on Android SDK version (BLE runtime perms for SDK >= S and POST_NOTIFICATIONS for TIRAMISU and above, otherwise ACCESS_FINE_LOCATION), check which are missing, and either start BLE and Classic services if all permissions are present or launch a request for the missing permissions.
     - Uses ContextCompat.checkSelfPermission to evaluate permission grants; if none missing it proceeds to start services, else triggers the permissions result launcher.
   - **Parameters description**
     - No parameters. Operates on Build.VERSION.SDK_INT and ContextCompat permission checks.
   - **Returns description**
     - No return value; side effects: starts services or requests permissions.

12. **startBluetoothService**
   - Category: Service start & bind
   - Lines: 419-426
   - **Description**
     - Start and bind to the foreground BluetoothService (BLE) if not already started. It guards with a flag (serviceStarted) to prevent multiple starts and binds using startForegroundService and bindService with BIND_AUTO_CREATE.
     - Applies package-scoped Intent to ensure service resolves to the app's component.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - No return value; side effects: starts and binds BLE service.

13. **startClassicBluetoothService**
   - Category: Service start & bind
   - Lines: 427-433
   - **Description**
     - Start and bind to the foreground ClassicBluetoothService if not already started. Uses classicServiceStarted flag to avoid duplicate starts and binds similarly with a package-scoped Intent.
     - Responsible for ensuring the classic service runs in foreground and is available for connection management and file transfer flows.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - No return value; side effects: starts and binds Classic service.

14. **requestPermissionsLauncher**
   - Category: ActivityResultLauncher<RequestMultiplePermissions>
   - Lines: 435-453
   - **Description**
     - Callback handling result of permission requests. Inspects granted permission map and determines if necessary Bluetooth permissions are granted depending on SDK level, then starts BLE and Classic services on success, otherwise displays a Toast indicating permissions are required. Notification permission denial is intentionally ignored.
     - It expects multiple permission keys and uses conditional checks for SDK >= S versus earlier versions.
   - **Parameters description**
     - Receives a Map<String, Boolean> mapping permissions to granted boolean.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | permissions | Map<String, Boolean> | Result map from the permission request containing permission names and whether they were granted. |
   - **Returns description**
     - No return; starts services or shows Toast messages as side effects.

15. **startClassicScan**
   - Category: Discovery start, Coroutine, Permissions
   - Lines: 455-510
   - **Description**
     - Initiates a Classic Bluetooth discovery: verifies Bluetooth adapter is enabled, clears device lists/maps and refreshes adapter, then uses lifecycleScope to check BLUETOOTH_SCAN permission (or no check for older SDKs) and starts discovery on the adapter, handling cancel/previous discovery and logging and user feedback via Toast.
     - Also builds a cached list of bonded/classic devices when permissions allow, populating classicDeviceList and classicDeviceMap synchronously before starting active discovery.
   - **Parameters description**
     - No parameters; uses system BluetoothManager and runtime permission checks.
   - **Returns description**
     - No return; side effects: starts/cancels discovery and updates UI lists.

16. **stopClassicScan**
   - Category: Discovery stop
   - Lines: 512-514
   - **Description**
     - Attempts to cancel Bluetooth classic discovery via the system Bluetooth adapter. Swallows SecurityException if permission is missing.
     - Used to stop scanning when switching tabs or on-demand.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - No return; side effects: halts device discovery if possible.

17. **connectToDevice**
   - Category: BLE connect helper
   - Lines: 516-526
   - **Description**
     - Helper to initiate a BLE connection: ensures the activity is bound to the BLE service, stops the bleScanManager, logs the connection attempt, updates status text and triggers haptic feedback on supported devices, then calls bluetoothService.connect(device).
     - Handles SecurityException when reading device.name and fails silently if the service is not bound.
   - **Parameters description**
     - Takes a BluetoothDevice to connect to and uses bluetoothService to initiate the connection.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | device | BluetoothDevice | Target BLE device to connect to. Device name/address are used for logging and UI. |
   - **Returns description**
     - No explicit return value; side effects: starts connection process on BluetoothService.

18. **onDestroy**
   - Category: Lifecycle teardown
   - Lines: 528-537
   - **Description**
     - Cleans up resources on activity destroy: removes delayed runnables, unregisters the classic broadcast receiver, cancels coroutine collector job, stops BLE scanning, and unbinds services if bound.
     - Swallows exceptions where needed (e.g., unregisterReceiver) to ensure a safe teardown path.
   - **Parameters description**
     - No parameters; this is an override of AppCompatActivity.onDestroy.
   - **Returns description**
     - No return; performs resource cleanup.

19. **onResume**
   - Category: Lifecycle method
   - Lines: 538-546
   - **Description**
     - Lifecycle onResume: resets BLE and Classic services' connection states to Idle if they were Disconnected or Failed, ensuring UI and internal state are ready for new interactions.
     - Uses direct calls to service.resetToIdle() and connectionManager.resetToIdle().
   - **Parameters description**
     - Override with no parameters other than the implicit lifecycle call.
   - **Returns description**
     - No return; side effects: resets internal connection states.

20. **onStart**
   - Category: Lifecycle method
   - Lines: 547-551
   - **Description**
     - Lifecycle onStart: ensures BLE and Classic services are started and bound if not already bound by calling startBluetoothService and startClassicBluetoothService.
     - Provides a safety net to re-bind to services if the activity is restarted while services remain running.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - No return; side effects: may start/bind services.


**Configuration References**
1. **Android runtime permissions (Manifest.permission.*)**
   - Line: 3,77,107,330,399,400,401,406,435,438,439,440,442
   - **What it does:**
     - The activity reads Android permission constants and checks request/response status to decide whether to start services and to permit scanning/discovery operations.
     - Permission checks are gated by SDK version: for newer Android versions (S and TIRAMISU) it expects BLUETOOTH_SCAN and BLUETOOTH_CONNECT (and POST_NOTIFICATIONS for TIRAMISU+), while older code paths rely on ACCESS_FINE_LOCATION.
   - **Default value**
     - N/A

2. **Build.VERSION.SDK_INT checks**
   - Line: 107,330,399,400,467,486,522
   - **What it does:**
     - The code uses SDK version gating to select appropriate runtime permissions and behavior (e.g., whether to check BLUETOOTH_CONNECT permission, whether to request POST_NOTIFICATIONS).
     - These checks affect which code paths are executed and include TODO placeholders for older SDK branches not implemented in this file.
   - **Default value**
     - N/A


**Code Walkthroughs**
1. **Lines:** 59-62
   - **What it does**
     - Defines a Runnable (notifyRunnable) that clears the notifyScheduled flag and calls ui.deviceAdapter.notifyDataSetChanged() on the UI thread.
     - Used to coalesce and defer UI adapter updates to avoid frequent UI refreshes when many scan results arrive quickly.
   - **Why it matters**
     - This batching mechanism (notifyScheduled + postDelayed) reduces UI thrashing from rapid incoming scan events and is used elsewhere to throttle adapter updates.

2. **Lines:** 103-115
   - **What it does**
     - classicScanReceiver is constructed with lambdas for permission checking, device-list change callback, and status update callback. Permission checker uses SDK gating to request BLUETOOTH_CONNECT permission on SDK >= S and TODO for older versions.
     - The receiver centralizes updates from classic discovery broadcasts into the shared device lists and UI updates.
   - **Why it matters**
     - It contains a runtime permission check branch and a TODO placeholder for older SDKs, which is non-obvious and affects behavior on different Android versions.

3. **Lines:** 336-355
   - **What it does**
     - onDeviceFound callback for BleScanManager checks if the found device already exists in bleDeviceList; if not it adds it, otherwise it updates the item if RSSI or name changed; updates bleDeviceMap; and schedules a coalesced UI update via notifyRunnable.
     - Uses indexOfFirst to locate existing device and a notifyScheduled flag to throttle UI updates via a 250ms delay.
   - **Why it matters**
     - This diffing and batching logic is the key mechanism to keep the BLE device list accurate while avoiding too-frequent adapter updates — important for performance and UX.

4. **Lines:** 160-229
   - **What it does**
     - The classicConnection onServiceConnected launches multiple collectors within repeatOnLifecycle to process connectionInfo, messages, events, file transfer state, and audio profile state. Each collected flow updates UI and/or shows messages in the BLE bottom sheet and logs timeline events.
     - The fileTransfer collector maps FileTransferState to user-facing strings (progress, completion or failure) and updates both the bottom-sheet log and the transfer UI accordingly.
   - **Why it matters**
     - Multiple concurrent collectors handling different Flows/streams from the Classic service are colocated here; understanding the mapping of states-to-UI and the concurrency model is important for reasoning about thread-safety, UI updates and lifecycle-bound cancellation.

5. **Lines:** 398-407
   - **What it does**
     - Constructs the permissions array required for the app based on SDK level: for modern SDKs this includes BLUETOOTH_SCAN, BLUETOOTH_CONNECT, and POST_NOTIFICATIONS (TIRAMISU+); otherwise it requests ACCESS_FINE_LOCATION.
     - This mapping is used to determine which runtime permissions to request before starting services.
   - **Why it matters**
     - The SDK-gated permission set determines service startup behavior and is a central integration point for Android permission model changes. There are TODO placeholders for SDK branches not handled in the code.

6. **Lines:** 400-404
   - **What it does**
     - The inner branch for Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU returns an array with POST_NOTIFICATIONS included; the else branch contains TODO("VERSION.SDK_INT < TIRAMISU").
     - This indicates the code has explicit gaps for older SDKs and will throw if executed on those older versions.
   - **Why it matters**
     - The presence of TODO in permission branches is significant: it will cause a runtime exception if the code path is reached on older SDKs and thus impacts compatibility.

7. **Lines:** 467-472
   - **What it does**
     - When populating bondedDevices for classic devices, the code checks device.type to ensure only CLASSIC or DUAL devices are included, then adds to classicDeviceMap and classicDeviceList with a ClassicDeviceItem.
     - This ensures bonded devices of other transport types are filtered out from the Classic device list.
   - **Why it matters**
     - This filter logic is important to avoid showing non-classic devices in the Classic tab and demonstrates care handling device types returned by Bluetooth API.


**Style Conventions**
1. **Lines:** 51-76
   - **Guideline**
     - Class-level fields use Kotlin idioms: lateinit for non-null properties initialized after construction, mutable lists and maps for device storage, and concise property naming.
     - Multiple flags and handlers are used to coordinate UI updates and deferred actions (e.g., notifyScheduled).
   - **Rationale**
     - Consistent Kotlin pattern for Android Activities: separating UI components and controllers from lifecycle logic for clarity.

2. **Lines:** 107-111
   - **Guideline**
     - Several branches use TODO("VERSION.SDK_INT < S") style placeholders where behavior for older SDK versions is not implemented; these will throw if executed.
     - Empty catch blocks (catch (_: SecurityException) {}) are used to swallow permission-related exceptions when permissions are absent.
   - **Rationale**
     - TODO placeholders and swallowed exceptions are noteworthy because they affect runtime compatibility and error observability.

3. **Lines:** 131-136
   - **Guideline**
     - SystemTimeline.log calls include emoji-prefixed messages to indicate state transitions (e.g., 🔄, 🟢, ❌) which is a repository-level logging style for readability.
     - runOnUiThread is used in service callbacks to ensure UI updates happen on the main thread.
   - **Rationale**
     - These stylistic choices aim for human-friendly logs and safe UI threading but are important to follow when extending logging or UI update code.


**Event Handling**
1. **Classic discovery BroadcastReceiver**
   - Lines: 103-115
   - **Trigger Type:** Android system Bluetooth broadcasts (BluetoothDevice.ACTION_FOUND, ACTION_DISCOVERY_FINISHED, ACTION_PAIRING_REQUEST, ACTION_BOND_STATE_CHANGED)
   - **Behavior**
     - classicScanReceiver listens to system Bluetooth classic discovery broadcasts and updates the classic device lists and status text via provided callbacks.
     - It is registered in onCreate with intent filters for ACTION_FOUND, ACTION_DISCOVERY_FINISHED, ACTION_PAIRING_REQUEST and ACTION_BOND_STATE_CHANGED, so it reacts to discovery lifecycle and pairing events.
   - **Impact**
     - Updates UI device lists and status text, enabling the Classic tab to display discovered and bonded devices; may update adapter frequently during discovery.

2. **BLE ServiceConnection events**
   - Lines: 117-152
   - **Trigger Type:** Bound BluetoothService (local service binding)
   - **Behavior**
     - serviceConnection handles onServiceConnected and onServiceDisconnected for the BLE service. When connected, it starts scanning (after a short delay), and attaches callbacks to the BluetoothService for state changes and incoming data.
     - State changes are logged and used to update the BLE status UI; incoming data is shown via the BleUiController bottom sheet.
   - **Impact**
     - Initiates BLE scanning and updates UI upon service state changes; provides the app's primary BLE event conduit.

3. **Classic ServiceConnection collectors**
   - Lines: 154-242
   - **Trigger Type:** ClassicBluetoothService internal Flows (connectionInfo, messages, events, fileTransferManager.state, audioProfileManager.connectionInfo)
   - **Behavior**
     - On binding to ClassicBluetoothService, multiple coroutines collect flows from the service's connection manager, messages, events, file transfer manager and audio profile manager. These collected events update Classic UI, show logs in the bottom sheet and log SystemTimeline entries.
     - These collections are lifecycle-aware (repeatOnLifecycle) and cancel automatically when the activity moves past STARTED state.
   - **Impact**
     - Drives continuous UI updates (status, transfer progress, a2dp audio status) and logs which are essential for showing live device and transfer state to the user.
