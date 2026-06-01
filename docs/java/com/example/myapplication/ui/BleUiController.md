**FileName:** BleUiController.kt   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/java/com/example/myapplication/ui/BleUiController.kt   
**Tags:** ui, bluetooth, bottom-sheet, animation, handler   

**File Summary**
BleUiController is a Kotlin UI helper class that centralizes UI updates related to Bluetooth Low Energy (BLE) state and displays a live-data bottom sheet. It manages animations for status text, schedules delayed status messages for long-running connect/pair stages, and builds a Material bottom sheet showing incoming BLE data entries with color-coded message types.

**Function Summaries**
1. **BleUiController (class + fields)**
   - Category: Class, Controller, UI
   - Lines: 21-36
   - **Description**
     - Constructs an instance that binds BLE state, activity UI elements and callbacks needed to update the app UI in response to BLE events.
     - Holds private state (bottom sheet dialog/list references, main-thread handler, delayed runnable) used across methods to manage asynchronous UI interactions.
   - **Parameters description**
     - Constructor parameters provide Android activity/context, UI views to update, and a set of lambda callbacks used to start scans, get device info/state, manage refresh flags, determine the active app tab, and handle dismissal behavior.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | activity | AppCompatActivity | Android activity context used for view creation, resources and dialog presentation. |
     | statusText | TextView | TextView instance whose text/animation is controlled to reflect BLE connection status. |
     | backgroundView | GlassmorphicBackgroundView | Custom view instance used to animate background transitions based on BLE state. |
     | onStartBleScan | () -> Unit | Callback invoked to initiate a BLE scan (used after certain disconnect logic). |
     | getConnectedDeviceName | () -> String? | Callback that returns the currently connected device name, if available. |
     | getCurrentBleState | () -> BleState? | Callback that returns the current BLE state synchronously. |
     | isPendingRefresh | () -> Boolean | Callback that indicates whether a refresh/rescan is pending after a disconnect. |
     | clearPendingRefresh | () -> Unit | Callback to clear the pending refresh flag. |
     | getActiveTab | () -> ActiveTab | Callback to determine which UI tab is currently active in the host activity. |
     | onDismissDataSheet | () -> Unit | Callback invoked to dismiss any data sheet-related UI when appropriate. |
   - **Returns description**
     - No return; this defines stored properties and initial state for the controller.

2. **updateStatusUi**
   - Category: Function, UI Update, State Handler
   - Lines: 37-79
   - **Description**
     - Updates UI to reflect the provided BLE state and device address: transitions background, determines device display name, sets an appropriate status message and animates it.
     - Schedules delayed messages for the CONNECTING and BONDING states to show longer-warning messages if those states persist; triggers an automatic scan if a disconnect occurs while a pending refresh flag is set; dismisses data sheet when disconnected or failed and the BLE tab is active.
   - **Parameters description**
     - Accepts the resolved BLE state and device address; uses constructor callbacks to look up names, query current state, handle pending refresh, and trigger scans or sheet dismissals.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | state | BleState | The new BLE state to present in the UI (enum cases like IDLE, CONNECTING, READY, etc.). |
     | address | String | Device MAC/identifier used to attempt a stored name lookup; may be empty which causes fallback to other name sources. |
   - **Returns description**
     - No return value; performs immediate UI side effects (animations, scheduled runnables, callbacks).

3. **animateStatusText**
   - Category: Function, Animation Helper, UI
   - Lines: 80-87
   - **Description**
     - Performs a short fade/translate animation to update the status TextView's text in a visually smooth way.
     - Skips any work if the text is already the requested value.
   - **Parameters description**
     - Takes a TextView to animate and the new text string to display.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | tv | TextView | Target TextView whose text and visual properties will be animated. |
     | newText | String | The new status message string to display in the TextView. |
   - **Returns description**
     - No return; triggers view animation and updates the TextView text on animation end.

4. **showDataBottomSheet**
   - Category: Function, UI, BottomSheet
   - Lines: 88-115
   - **Description**
     - Creates (if necessary) and shows a bottom sheet dialog that displays live BLE data entries. Each entry is a TextView appended to a vertical list. The sheet includes a header with title and a clear button that removes all entries.
     - Color-codes rows based on message prefixes (e.g., [Notify], [Read], [Subscribed], [Log]); ensures the dialog is reused while it exists.
   - **Parameters description**
     - Receives a single string data line which is rendered into the top of the live-data list in the bottom sheet and shown to the user.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | data | String | A single-line message describing a BLE event/data which will be added to the top of the list in the bottom sheet. |
   - **Returns description**
     - No return; side-effects include creating views, mutating the bottom sheet content list, and showing the dialog.

5. **dismissDataSheet**
   - Category: Function, UI, BottomSheet
   - Lines: 116-120
   - **Description**
     - Programmatically dismisses the live-data bottom sheet and clears the local references so the sheet will be rebuilt on next show.
     - Ensures any UI state held in this controller related to the bottom sheet is released.
   - **Parameters description**
     - No parameters; acts on controller-held bottom sheet references.
   - **Returns description**
     - No return; triggers dismissal of the dialog if present and nulls local references.


**Code Walkthroughs**
1. **Lines:** 40-42
   - **What it does**
     - Determine display name for the device shown in status: first try a stored name keyed by address, then fall back to the connected device callback, finally use a generic 'Device' string.
   - **Why it matters**
     - This order of fallbacks influences what name users see in status messages and ties to DeviceNameStore and runtime callbacks.

2. **Lines:** 46-51
   - **What it does**
     - Schedule a delayed runnable (5s) when entering CONNECTING so that if the device remains connecting the status text will animate to a 'taking longer than expected' warning.
   - **Why it matters**
     - This delayed behavior prevents immediate warnings on brief connects and only shows the user a note after a threshold.

3. **Lines:** 55-61
   - **What it does**
     - Similar to CONNECTING, schedule a longer delayed runnable (10s) when in BONDING state to warn the user if pairing takes too long; the scheduled runnable checks the live BLE state before animating.
   - **Why it matters**
     - Bonding may legitimately take longer than connecting; the longer delay avoids false warnings while still notifying long operations.

4. **Lines:** 66-69
   - **What it does**
     - If a disconnect happens and a pending refresh flag is set, clear that flag and schedule a new BLE scan to start after a short delay (700ms).
   - **Why it matters**
     - This provides automated retry logic after disconnects, tied to external control via the isPendingRefresh/clearPendingRefresh callbacks.

5. **Lines:** 80-86
   - **What it does**
     - Perform chained view property animations to fade out the status text, update its content while offscreen, and fade/translate it back in for a fluid update effect.
   - **Why it matters**
     - The animation sequence is non-trivial (chained withEndAction) and is crucial for the perceived responsiveness of status updates.

6. **Lines:** 91-100
   - **What it does**
     - Lazily constructs a BottomSheetDialog and its nested ScrollView/LinearLayouts/TextView header components, wires the clear button to remove child views from the list, sets the content view, and clears local references on dismiss.
   - **Why it matters**
     - The dynamic view creation uses multiple nested apply blocks and local references that must be properly managed to avoid leaks and to ensure correct re-creation on subsequent calls.

7. **Lines:** 103-111
   - **What it does**
     - Create a TextView row for the incoming data and map certain message prefixes to specific hex color values; default color is white for unknown prefixes.
   - **Why it matters**
     - The prefix-based color mapping determines how different kinds of messages are visually distinguished in the live-data UI.


**Style Conventions**
1. **Lines:** 91-99
   - **Guideline**
     - Uses Kotlin's apply blocks to configure Views inline for concise, fluent initialization.
     - Makes heavy use of literal numeric values (padding, textSize, delay durations) directly in code rather than constants.
   - **Rationale**
     - The apply pattern reduces boilerplate for view construction; magic numbers are noteworthy because they centralize UI timing/spacing decisions in this file rather than constants.

2. **Lines:** 80-86
   - **Guideline**
     - Animations are implemented with chained calls and withEndAction to update text on animation pivot; consistent use of property animation on alpha and translationY.
   - **Rationale**
     - Consistent animation approach keeps a predictable visual update pattern for status changes.


**Event Handling**
1. **Clear button click handler**
   - Lines: 95-95
   - **Trigger Type:** MaterialButton (UI)
   - **Behavior**
     - The MaterialButton in the bottom sheet header is wired to remove all child views of bottomSheetList when clicked, clearing displayed live-data rows.
   - **Impact**
     - Clears visible live data entries; does not report or persist cleared data anywhere in this controller.

2. **Bottom sheet dismiss listener**
   - Lines: 100-100
   - **Trigger Type:** BottomSheetDialog
   - **Behavior**
     - When the BottomSheetDialog is dismissed by the user or programmatically, the dialog and list references stored in the controller are nulled so the sheet will be rebuilt on the next show request.
   - **Impact**
     - Releases local references so memory can be reclaimed and ensures new UI is built the next time showDataBottomSheet is called.

3. **Scheduled delayed status runnables**
   - Lines: 46-61
   - **Trigger Type:** Handler.postDelayed
   - **Behavior**
     - Runnables posted to uiHandler run on the main thread after a delay to check the current BLE state and update animation text if the state persists (for CONNECTING and BONDING).
   - **Impact**
     - Triggers additional UI animations after a time threshold to inform users of long-running operations.
