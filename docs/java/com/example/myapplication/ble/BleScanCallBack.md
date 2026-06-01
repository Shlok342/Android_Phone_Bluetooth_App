**FileName:** BleScanCallBack.kt   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/java/com/example/myapplication/ble/BleScanCallBack.kt   
**Tags:** ble, android, callback, logging, permission   

**File Summary**
Kotlin class that implements an Android BLE ScanCallback. It processes individual and batched BLE scan results, extracts device metadata (name, address, RSSI), handles permission-related name access, maps scan failure error codes to human-readable strings, logs events, and forwards discovered devices via a provided callback lambda. The file integrates with an internal BleDeviceItem model and relies on an injected permission-checker lambda to determine whether Bluetooth connect permission is available.

**Function Summaries**
1. **BleScanCallback constructor & class declaration**
   - Category: Class, Constructor
   - Lines: 9-12
   - **Description**
     - Defines the BleScanCallback class which extends Android's ScanCallback to receive BLE scanning events.
     - Accepts two lambdas: one to notify the caller when a device is found and another to check whether required Bluetooth permissions are granted.
   - **Parameters description**
     - Two injected lambda parameters: onDeviceFound is invoked when a device is discovered; permissionChecker is used to determine whether BLUETOOTH_CONNECT (or equivalent) permission is granted before accessing certain device properties.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | onDeviceFound | (BleDeviceItem, BluetoothDevice) -> Unit | Callback invoked with a BleDeviceItem and the raw BluetoothDevice when a device is discovered. |
     | permissionChecker | () -> Boolean | No-arg lambda returning whether the app currently has the permission required to access device.name (true if permitted). |
   - **Returns description**
     - None (class constructor).

2. **onBatchScanResults**
   - Category: Override, Event handler
   - Lines: 14-21
   - **Description**
     - Handles a batch of ScanResult objects delivered by the BLE scanner.
     - Queries permissionChecker once for the whole batch, then iterates over results and processes each via processSingleResult.
   - **Parameters description**
     - Receives a mutable list of ScanResult objects representing multiple BLE scan events gathered together.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | results | MutableList<ScanResult> | A collection of ScanResult objects that were discovered during a scanning interval; each will be processed individually. |
   - **Returns description**
     - Unit (no direct return).

3. **onScanResult**
   - Category: Override, Event handler
   - Lines: 23-29
   - **Description**
     - Handles a single ScanResult callback from the BLE scanner.
     - Logs receipt of a raw result, checks permissions, and processes the single result via processSingleResult.
   - **Parameters description**
     - Takes the callback type and a single ScanResult and uses the permissionChecker to determine device name access.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | callbackType | Int | Integer identifying how/why the callback was triggered (provided by the scanning subsystem). |
     | result | ScanResult | The single scan result to be processed. |
   - **Returns description**
     - Unit (no direct return).

4. **onScanFailed**
   - Category: Override, Error handler, Event handler
   - Lines: 30-55
   - **Description**
     - Handles scan failure events reported by the BLE scanner by mapping integer error codes to human-readable reasons.
     - Logs an error-level message with the derived failure reason string.
   - **Parameters description**
     - Accepts an integer errorCode defined by the Android BLE scanning API and converts it into a descriptive string for logging.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | errorCode | Int | One of the ScanCallback error constants indicating why scanning failed. |
   - **Returns description**
     - Unit (no direct return).

5. **processSingleResult**
   - Category: Private helper
   - Lines: 56-84
   - **Description**
     - Private helper that extracts device details from a single ScanResult and builds a BleDeviceItem representation.
     - Resolves the device name conditionally based on permission availability and handles SecurityException if name access is attempted without permission; logs the discovered device and invokes the supplied onDeviceFound callback with the constructed item and raw BluetoothDevice.
   - **Parameters description**
     - Takes a ScanResult and a boolean indicating whether the code has permission to access the BluetoothDevice name.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | result | ScanResult | The scan result containing rssi and the BluetoothDevice reference. |
     | hasPermission | Boolean | Indicates whether BLUETOOTH_CONNECT (or equivalent) permission is currently granted; used to determine how to safely access the device name. |
   - **Returns description**
     - Unit (no direct return); side effect is calling onDeviceFound lambda.


**Code Walkthroughs**
1. **Lines:** 31-52
   - **What it does**
     - Maps ScanCallback integer error codes to human-readable strings using a kotlin when expression.
     - Provides a final 'UNKNOWN_ERROR' fallback for codes not explicitly handled.
   - **Why it matters**
     - This mapping converts low-level numeric error codes into readable log output, aiding diagnostics when scanning fails.

2. **Lines:** 62-71
   - **What it does**
     - Conditionally obtains the BluetoothDevice.name only if permissionChecker indicates permission is present; wraps the access in a try/catch to handle SecurityException if the system refuses name access.
     - Provides clear fallback strings indicating why the name is unknown.
   - **Why it matters**
     - Accessing device.name can throw SecurityException on newer Android versions without BLUETOOTH_CONNECT; the code both checks a permission lambda and defensively catches SecurityException to avoid crashes.

3. **Lines:** 73-77
   - **What it does**
     - Creates a BleDeviceItem model from the resolved name, device address, and RSSI contained in ScanResult.
     - This is the data structure passed to the external onDeviceFound callback for UI or persistence use.
   - **Why it matters**
     - Central data transformation from platform ScanResult to app model; important for understanding what information is forwarded to other parts of the app.

4. **Lines:** 79-83
   - **What it does**
     - Logs the discovered device (name and address) and invokes the onDeviceFound callback with both the created BleDeviceItem and the raw BluetoothDevice object.
     - Enables callers to act on both the high-level model and the low-level device instance (e.g., to connect).
   - **Why it matters**
     - Shows how discovered device information is both recorded via logs and communicated outward; the dual argument gives consumers flexibility to use either the lightweight model or the actual BluetoothDevice.


**Style Conventions**
1. **Lines:** 9-12
   - **Guideline**
     - Uses concise Kotlin primary constructor syntax to inject dependencies (lambdas) directly into the class.
     - Parameter names are descriptive (onDeviceFound, permissionChecker) following typical Kotlin naming conventions.
   - **Rationale**
     - Improves readability and makes dependencies explicit; consistent with idiomatic Kotlin.

2. **Lines:** 56-84
   - **Guideline**
     - Private helper method processSingleResult encapsulates transformation and side effects, keeping the public callback handlers minimal.
     - Logging tags and message strings are inline (e.g., "BLE_SCAN"); consistent and simple logging style is used.
   - **Rationale**
     - Encapsulation and consistent logging help maintainability and traceability during debugging.


**Event Handling**
1. **Batch scan results handler**
   - Lines: 14-21
   - **Trigger Type:** Android BLE Scanner (ScanCallback.onBatchScanResults)
   - **Behavior**
     - Handles multiple scan results emitted together by the platform scan API, iterating over them and delegating to processSingleResult.
     - No side effects beyond invoking the external onDeviceFound callback for each device; does not aggregate or deduplicate devices.
   - **Impact**
     - Triggers device-found callbacks for every result in the batch; can lead to many outbound events if scanning returns many results.

2. **Single scan result handler**
   - Lines: 23-29
   - **Trigger Type:** Android BLE Scanner (ScanCallback.onScanResult)
   - **Behavior**
     - Handles single ScanResult events by logging and delegating to processSingleResult after permission check.
     - Used for immediate processing of individual discoveries rather than batch processing.
   - **Impact**
     - Immediately emits device-found callbacks for each discovered device, causing downstream processing (UI updates, connection attempts) to run.

3. **Scan failure handler**
   - Lines: 30-55
   - **Trigger Type:** Android BLE Scanner (ScanCallback.onScanFailed)
   - **Behavior**
     - Handles scanning errors reported by the Android BLE stack by mapping error codes to strings and logging them at error level.
     - Does not perform retries or state transitions; purely logs the failure reason.
   - **Impact**
     - Provides visibility into scanning failures via logs but does not inform other components directly.
