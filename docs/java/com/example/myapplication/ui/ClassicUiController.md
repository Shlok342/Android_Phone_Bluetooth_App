**FileName:** ClassicUiController.kt   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/java/com/example/myapplication/ui/ClassicUiController.kt   
**Tags:** ui, bluetooth, file-transfer, bottom-sheet, android   

**File Summary**
ClassicUiController is a UI controller class in the Android app that manages and updates the user interface for the "Classic" (classic Bluetooth) feature. It drives status text animations, transfer status UI, and presents two bottom-sheet dialogs: a small feature sheet and a larger procedural insights modal that lists system timeline events. The file builds UI programmatically, references app resources, and depends on internal helpers for device names and system timeline data.

**Function Summaries**
1. **ClassicUiController**
   - Category: Class, UI Controller
   - Lines: 25-223
   - **Description**
     - Encapsulates UI logic for the "Classic" Bluetooth flow (status, transfers, and informational dialogs).
     - Holds constructor-injected dependencies (activity, text views, background view, callbacks) and exposes methods to update UI and show dialogs.
     - Coordinates between internal state providers (callbacks) and UI components, and constructs dialogs programmatically.
   - **Parameters description**
     - Constructor parameters are injected dependencies and callbacks required to update UI and perform actions (e.g., sending files, checking connection, getting active tab and connected device name).
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | activity | AppCompatActivity | The hosting activity used for context to create views, access resources, and show dialogs. |
     | classicStatusText | TextView | TextView to display the overall Classic connection status. |
     | transferStatusText | TextView | TextView to display current file transfer status and progress. |
     | backgroundView | GlassmorphicBackgroundView | Custom background view used to animate/transition the UI to different Classic states. |
     | onSendFile | () -> Unit | Callback invoked when the user selects 'Send file' from the features sheet. |
     | isClassicConnected | () -> Boolean | Callback used to determine whether Classic is currently connected before allowing certain actions. |
     | getActiveTab | () -> ActiveTab | Callback to read the currently active tab; used to dismiss modal/data sheet when disconnected. |
     | onDismissDataSheet | () -> Unit | Callback invoked to dismiss any data sheet when Classic disconnects or fails. |
     | getConnectedDeviceName | () -> String? | Callback that returns the name of the currently connected device if available. |
   - **Returns description**
     - The constructor initializes the controller; methods produce side effects (update UI and show dialogs) and return Unit.

2. **animateStatusText**
   - Category: Private method, UI animation helper
   - Lines: 35-42
   - **Description**
     - Performs a small fade-and-translate animation when changing the text of a TextView to visually indicate status changes.
     - Skips animation if the new text is identical to the current text.
   - **Parameters description**
     - Takes the target TextView and the new text string to display; animates alpha and translationY when text changes.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | tv | TextView | The TextView whose text should be updated and animated. |
     | newText | String | The new status text to set on the TextView. |
   - **Returns description**
     - Performs UI animation and updates the TextView text; returns Unit.

3. **updateClassicStatusUi**
   - Category: Public method, UI update
   - Lines: 43-70
   - **Description**
     - Updates UI to reflect the current Classic connection state (idle, connecting, connected, reconnecting, failed, disconnected).
     - Transforms states into human-readable status messages (including device name and/or address) and invokes background transitions on the GlassmorphicBackgroundView.
     - Dismisses the data sheet if state is DISCONNECTED or FAILED and the Classic tab is active.
   - **Parameters description**
     - Accepts a ClassicState and an address string; builds a status message based on the state and updates the UI via animateStatusText.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | state | ClassicState | The current connection state used to determine the status message and UI transitions. |
     | address | String | Bluetooth address used to look up a stored device name (fallbacks applied). |
   - **Returns description**
     - Updates the status UI and may call onDismissDataSheet; returns Unit.

4. **updateTransferUi**
   - Category: Public method, UI update
   - Lines: 71-91
   - **Description**
     - Updates the transferStatusText based on the given FileTransferState (Idle, Sending, Receiving, Done, Failed, Cancelled).
     - Shows or hides the transferStatusText, formats percentages for progress, and uses string resources for localized messages.
   - **Parameters description**
     - Accepts a FileTransferState which contains progress, filename, direction, and failure reason as applicable.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | state | FileTransferState | Represents the file transfer lifecycle and metadata (progress, filename, success/failure reason, direction). |
   - **Returns description**
     - Updates transfer-related UI elements (text and visibility); returns Unit.

5. **showClassicFeaturesSheet**
   - Category: Public method, UI dialog
   - Lines: 92-115
   - **Description**
     - Creates and shows a BottomSheetDialog listing quick actions for Classic mode (Send file, Procedural insights).
     - Builds the sheet programmatically (title + two MaterialButtons) and wires click listeners that dismiss the sheet and trigger actions/callbacks.
   - **Parameters description**
     - No parameters. Uses activity and injected callbacks to perform actions when buttons are clicked.
   - **Returns description**
     - Shows a bottom sheet with actions; returns Unit.

6. **showInsightsModal**
   - Category: Public method, UI dialog, event list
   - Lines: 117-222
   - **Description**
     - Creates and shows a larger BottomSheetDialog that displays procedural insights — a scrollable event list sourced from SystemTimeline.
     - Constructs the entire UI programmatically including header (title, clear and close buttons), divider, scrollable event list, and configures BottomSheetBehavior to show expanded content that occupies most of the screen.
   - **Parameters description**
     - No parameters. Pulls event data from SystemTimeline and uses activity context and callbacks to manage interactions like clearing events and closing the sheet.
   - **Returns description**
     - Shows a full-height modal with a list of timeline events and buttons to clear or close; returns Unit.

7. **rebuildEvents**
   - Category: Local function, UI list rebuild
   - Lines: 181-207
   - **Description**
     - Local helper inside showInsightsModal that clears and repopulates the eventList LinearLayout with formatted TextViews for each event from SystemTimeline.
     - If there are no events, shows a placeholder message. Adds subtle separators between entries.
   - **Parameters description**
     - No parameters. Reads events via SystemTimeline.getEvents().
   - **Returns description**
     - Directly manipulates eventList view children; returns Unit.


**Code Walkthroughs**
1. **Lines:** 35-41
   - **What it does**
     - Implements a two-stage animation: fade out + upward translation, change text, reset translation, then fade in + translate to original position.
   - **Why it matters**
     - Non-trivial sequence of chained animations that ensure text replacement is visually smooth and avoids jumpy changes; skipping on identical text avoids redundant animation.

2. **Lines:** 45-47
   - **What it does**
     - Resolves a device name by first looking up a stored name by address, then falling back to getConnectedDeviceName() callback, then defaulting to 'Device'.
   - **Why it matters**
     - Shows how the code prioritizes multiple sources for the connected device's display name before using a generic fallback.

3. **Lines:** 53-53
   - **What it does**
     - Formats a reconnecting status message that includes the current attempt and a constant maximum attempts value.
   - **Why it matters**
     - This string concatenation includes parentheses and references an external constant; its formatting should be checked for completeness (see potential unmatched parenthesis).

4. **Lines:** 75-76
   - **What it does**
     - Converts progress fraction to a percentage integer and uses a localized string to show sending progress including filename and percent.
   - **Why it matters**
     - The conversion and resource formatting are important to ensure correct percent display and localization usage.

5. **Lines:** 80-82
   - **What it does**
     - Same as sending case but for receiving; calculates percent and sets text/visibility appropriately.
   - **Why it matters**
     - Consistent logic for progress display used across sending and receiving flows.

6. **Lines:** 94-100
   - **What it does**
     - Programmatically constructs the features bottom sheet container, title and 'Send file' MaterialButton with styling and click handler in a single chained apply block.
   - **Why it matters**
     - Chained configuration with many inline properties reduces readability; this is where click behavior triggers onSendFile after dismissing the sheet.

7. **Lines:** 118-126
   - **What it does**
     - Computes a contentHeight for the insights modal as 92% of the device screen height and applies it to the root LinearLayout.
     - This sets the modal size so the bottom sheet behavior can expand to a near-fullscreen view.
   - **Why it matters**
     - Explicit sizing influences BottomSheetBehavior configuration and is central to how the modal is presented on different screen sizes.

8. **Lines:** 181-207
   - **What it does**
     - Iterates SystemTimeline.getEvents() to create a vertical list of TextViews and separators for each event; shows placeholder when empty.
   - **Why it matters**
     - This immediate in-memory construction of views for each event can be costly if the timeline grows large; it also controls the displayed text format and separators.

9. **Lines:** 216-221
   - **What it does**
     - Configures the BottomSheetBehavior to expand the sheet fully (peekHeight set to contentHeight, skipCollapsed true, not fitToContents, and set to STATE_EXPANDED).
   - **Why it matters**
     - Explicitly manipulates bottom sheet behavior to present insights as an expanded modal rather than a collapsible sheet, affecting UX and interaction model.


**Style Conventions**
1. **Lines:** 94-100
   - **Guideline**
     - UI elements are constructed programmatically using apply { } blocks with many property assignments on single lines.
     - Some apply blocks chain many statements in one long line which can reduce readability.
   - **Rationale**
     - Consistent programmatic construction is used across the file, but dense inline configuration lines may make review and diffs harder to read.

2. **Lines:** 98-109
   - **Guideline**
     - Use of activity.getString() vs context.getString() is mixed in different places (both are valid but inconsistent).
     - dp extension function is used for dimensional conversions which is a conventional Kotlin pattern for Android.
   - **Rationale**
     - Inconsistency in using 'activity' vs 'context' for string/resource access is noteworthy for maintainability and consistency.

3. **Lines:** 35-42
   - **Guideline**
     - Animations are built using ViewPropertyAnimator with chained calls and withEndAction callbacks; this is a common and lightweight approach for simple UI transitions.
   - **Rationale**
     - This is appropriate for small transitions and keeps the code succinct.


**Event Handling**
1. **SendFile button click**
   - Lines: 98-99
   - **Trigger Type:** MaterialButton (UI)
   - **Behavior**
     - When the 'Send file' MaterialButton is clicked, the sheet is dismissed and onSendFile() is invoked if isClassicConnected() returns true.
     - This triggers file-send flows externally via the provided callback; the dismissal ensures the sheet doesn't remain visible during file selection/transfers.
   - **Impact**
     - Initiates file-send action; conditional on current connection status.

2. **Procedural Insights button click**
   - Lines: 108-109
   - **Trigger Type:** MaterialButton (UI)
   - **Behavior**
     - When the 'Procedural insights' button is clicked, the current sheet is dismissed and a ProceduralInsightsSheet is displayed.
     - This transitions from the features sheet to an insights view provided by a different component (ProceduralInsightsSheet).
   - **Impact**
     - Shows a separate insights UI; changes the visible modal.

3. **Insights modal clear button click**
   - Lines: 209-210
   - **Trigger Type:** MaterialButton (UI)
   - **Behavior**
     - Clears the SystemTimeline via SystemTimeline.clear() and rebuilds the displayed event list by calling rebuildEvents().
     - This modifies application state (system timeline) and immediately updates the modal content.
   - **Impact**
     - Mutates timeline state and refreshes view to show cleared state.

4. **Insights modal close button click**
   - Lines: 160-160
   - **Trigger Type:** MaterialButton (UI)
   - **Behavior**
     - Dismisses the insights sheet and opens the classic features sheet by calling showClassicFeaturesSheet().
     - Provides navigation between the insights modal and the smaller features sheet.
   - **Impact**
     - Switches visible modal; no direct state mutation besides dismissing dialogs.
