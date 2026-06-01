**FileName:** DeviceSearchSheet.kt   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/java/com/example/myapplication/ui/DeviceSearchSheet.kt   
**Tags:** ui, bottom-sheet, search, events, android   

**File Summary**
DeviceSearchSheet.kt defines a BottomSheetDialog subclass that builds a device search UI programmatically. The sheet supports toggling between name-based live filtering and MAC-address explicit search, wires UI elements (buttons, input) to callbacks provided by the caller, and reports whether a query was present when dismissed. All views are created in onCreate and no XML layout is used; styling and resources are referenced from the app's resources.

**Function Summaries**
1. **DeviceSearchSheet (class & constructor)**
   - Category: Class, UI component
   - Lines: 20-24
   - **Description**
     - Declares a BottomSheetDialog subclass that provides a device search UI as a modal sheet.
     - Accepts two callbacks: onFilter(query, byMac) to perform filtering/search and onDismissed(hasQuery) to notify the host when the sheet is closed with/without a query.
   - **Parameters description**
     - Primary constructor receives Android Context and two lambda callbacks for filtering and dismissal.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | context | Context | Android Context used to create views and passed to BottomSheetDialog superclass. |
     | onFilter | (query: String, byMac: Boolean) -> Unit | Callback invoked to perform filtering/search. The boolean indicates whether the query is to be treated as a MAC search. |
     | onDismissed | (hasQuery: Boolean) -> Unit | Callback invoked when the dialog is dismissed; indicates whether a non-empty query was present at dismissal. |
   - **Returns description**
     - No return value; this is a class declaration.

2. **onCreate**
   - Category: Override, Lifecycle, UI construction
   - Lines: 28-180
   - **Description**
     - Lifecycle method that builds the entire bottom sheet UI programmatically when the dialog is created.
     - Creates root layout, header (title + close), a divider, toggle buttons to switch search mode, search input, optional search button for MAC mode, and hooks up listeners to drive filtering and dismissal behavior.
   - **Parameters description**
     - Receives savedInstanceState: Bundle? as required by Activity/Dialog lifecycle overrides.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | savedInstanceState | Bundle? | Standard Android bundle used for restoring state; not used in this implementation. |
   - **Returns description**
     - No return (Unit). The method's effect is to configure the dialog UI and behavior.

3. **activateName**
   - Category: Helper function, UI state switch
   - Lines: 128-137
   - **Description**
     - Switches the sheet into 'Name' search mode: updates internal flag, swaps toggle button styles, hides the explicit MAC search button, updates the search input hint, and triggers a live filter with the current input.
     - Used to ensure UI state and behavior correspond to a name-based (live) search mode.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - No return value; updates UI and calls onFilter as a side effect.

4. **activateMac**
   - Category: Helper function, UI state switch
   - Lines: 139-148
   - **Description**
     - Switches the sheet into 'MAC' search mode: updates internal flag, swaps toggle styles, shows the explicit MAC search button, updates the search input hint, and clears the current filter by invoking onFilter with an empty query.
     - This mode requires an explicit search trigger (button click or IME action) rather than live filtering.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - No return value; updates UI and calls onFilter with an empty query to clear live filters.

5. **Header UI construction**
   - Category: UI construction
   - Lines: 32-61
   - **Description**
     - Creates a horizontal header row containing the title text and a close button, styles both, and adds them to the root layout.
     - Title string and colors are taken from resources; close button is a MaterialButton styled to look like an icon/button.
   - **Parameters description**
     - No parameters (local UI build).
   - **Returns description**
     - No return value; side-effect is adding views to root.

6. **Divider**
   - Category: UI construction
   - Lines: 63-69
   - **Description**
     - Adds a thin horizontal divider view under the header using a background color from resources and margins defined via dp extensions.
     - Serves visual separation between header and content.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - No return value; adds a view to root.

7. **Toggle Row UI construction**
   - Category: UI construction
   - Lines: 71-95
   - **Description**
     - Creates two toggle MaterialButtons (Name and MAC) placed horizontally so the user can select search mode.
     - Name button is styled active by default; MAC button is inactive initially. Buttons are added to the root layout.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - No return value; adds views to root and later wired to listeners.

8. **Search input construction**
   - Category: UI construction
   - Lines: 96-112
   - **Description**
     - Creates an EditText used for both name and MAC input. Sets hint, styles, padding, IME action, input type, and disables autofill.
     - Configured to be single-line and vertically centered for consistent appearance.
   - **Parameters description**
     - No parameters (local UI build).
   - **Returns description**
     - No return value; view added to root.

9. **Search button UI construction**
   - Category: UI construction
   - Lines: 114-125
   - **Description**
     - Creates a MaterialButton used to explicitly trigger MAC searches. Initially hidden (visibility = GONE) because name mode is the default.
     - When shown, it appears under the input and triggers onFilter with byMac = true.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - No return value; view added to root.

10. **TextWatcher (live-name filtering)**
   - Category: Event listener, TextWatcher
   - Lines: 154-160
   - **Description**
     - Anonymous TextWatcher attached to the search input that executes afterTextChanged and invokes onFilter for live filtering only when not in MAC mode.
     - No-ops beforeTextChanged and onTextChanged are provided.
   - **Parameters description**
     - Implements TextWatcher callbacks; primary data is the Editable passed to afterTextChanged.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | s | Editable? | The current text in the EditText after the change; trimmed and passed to onFilter when applicable. |
   - **Returns description**
     - No return value; acts via side-effect of invoking onFilter.

11. **searchBtn OnClickListener**
   - Category: Event listener, Click
   - Lines: 163-165
   - **Description**
     - On click, triggers onFilter with the current input and byMac = true. Used for explicit MAC searches when in MAC mode.
   - **Parameters description**
     - Standard click listener with no parameters in this lambda.
   - **Returns description**
     - No return value; invokes onFilter as side-effect.

12. **searchInput Editor Action Listener**
   - Category: Event listener, IME action
   - Lines: 166-170
   - **Description**
     - Handles the IME action from the keyboard (search action). When the IME_ACTION_SEARCH is received and the sheet is in MAC mode, invokes onFilter with byMac = true and returns true to indicate the event was handled.
     - When not in MAC mode or action differs, it returns false allowing default behavior.
   - **Parameters description**
     - Standard EditorActionListener parameters: view, actionId, event (not used).
   - **Returns description**
     - Returns a Boolean indicating whether the IME action was handled (true when handling MAC search).
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | handled | Boolean | True if the IME action resulted in a MAC search invocation, false otherwise. |

13. **closeBtn OnClickListener**
   - Category: Event listener, Click
   - Lines: 173-177
   - **Description**
     - When the close button is clicked, determines whether the input contains a non-empty query and calls onDismissed(hasQuery) to notify the caller. Then dismisses the dialog.
     - This allows the host to, for example, show/clear an external clear icon if a query existed.
   - **Parameters description**
     - No parameters; uses current text in the search input to compute hasQuery.
   - **Returns description**
     - No return value; side-effects include invoking onDismissed and dismissing the dialog.


**Configuration References**
1. **R.string.search_devices**
   - Line: 44
   - **What it does:**
     - Title text for the sheet header; affects displayed label.
   - **Default value**
     - N/A

2. **R.string.search_toggle_name**
   - Line: 77
   - **What it does:**
     - Label for the 'Name' toggle button; controls displayed text for naming mode.
   - **Default value**
     - N/A

3. **R.string.search_toggle_mac**
   - Line: 85
   - **What it does:**
     - Label for the 'MAC' toggle button; controls displayed text for MAC mode.
   - **Default value**
     - N/A

4. **R.string.search_hint_name**
   - Line: 101,135
   - **What it does:**
     - Hint text shown in the EditText for name mode; guides user input.
   - **Default value**
     - N/A

5. **R.string.search_hint_mac**
   - Line: 146
   - **What it does:**
     - Hint text shown in the EditText for MAC mode; guides user input formatting.
   - **Default value**
     - N/A

6. **R.string.search_button_label**
   - Line: 116
   - **What it does:**
     - Label for the explicit MAC search button.
   - **Default value**
     - N/A

7. **R.color.color_text_primary**
   - Line: 47,79,118,142
   - **What it does:**
     - Primary text color used for prominent labels and active toggle states.
   - **Default value**
     - N/A

8. **R.color.color_text_secondary**
   - Line: 53,87,143
   - **What it does:**
     - Secondary text color used for inactive/less-prominent text and toggle states.
   - **Default value**
     - N/A

9. **R.color.color_text_tertiary**
   - Line: 103
   - **What it does:**
     - Hint text color for EditText hints.
   - **Default value**
     - N/A

10. **R.color.color_glass_border**
   - Line: 68
   - **What it does:**
     - Color used for the divider background.
   - **Default value**
     - N/A

11. **R.drawable.bg_button_glass**
   - Line: 54,88,119
   - **What it does:**
     - Background drawable used for several buttons in their non-active state.
   - **Default value**
     - N/A

12. **R.drawable.bg_toggle_active**
   - Line: 80,141
   - **What it does:**
     - Background drawable used for active toggle button state.
   - **Default value**
     - N/A

13. **R.drawable.bg_edit_text_luxury**
   - Line: 99
   - **What it does:**
     - Background drawable used for the EditText input field.
   - **Default value**
     - N/A

14. **dp (extension function)**
   - Line: 34,57,66,74,92,98,106,122
   - **What it does:**
     - Converts integer dp values into pixel units using the provided context. Used throughout to create consistent spacing and sizes independent of screen density.
   - **Default value**
     - N/A


**Code Walkthroughs**
1. **Lines:** 34-34
   - **What it does**
     - Applies padding to the root layout using a dp extension function to convert integer dp to pixels based on context.
   - **Why it matters**
     - dp(...) is an internal convenience extension; using it ensures device-density agnostic spacing but requires understanding of the extension implementation.

2. **Lines:** 57-57
   - **What it does**
     - Sets multiple min/max size properties on the MaterialButton in a single line separated by semicolons.
   - **Why it matters**
     - Compound statements on one line using semicolons are legal Kotlin but slightly non-idiomatic; they compact initialization code.

3. **Lines:** 147-147
   - **What it does**
     - Calls onFilter with an empty query when switching to MAC mode to clear any existing name-based filter.
   - **Why it matters**
     - Explicitly clearing the filter on mode switch is an intentional behavioral choice to avoid cross-mode residual filtering.

4. **Lines:** 166-169
   - **What it does**
     - Handles the IME search action: if action matches IME_ACTION_SEARCH and current mode is MAC, it triggers a MAC search and returns true to indicate the action was handled by the app.
   - **Why it matters**
     - The lambda uses a semicolon followed by 'true' (onFilter(...); true) to ensure the listener returns true after invoking the callback. This idiom ensures the onFilter side-effect occurs and the handler signals event consumption.

5. **Lines:** 174-175
   - **What it does**
     - Computes hasQuery by trimming text and checking non-emptiness and then passes that boolean to onDismissed before dismissing the sheet.
   - **Why it matters**
     - The host is informed whether the sheet was dismissed while a query was present, enabling UI decisions outside the sheet.


**Style Conventions**
1. **Lines:** 32-125
   - **Guideline**
     - Views are constructed programmatically using Kotlin scope function apply for concise initialization. LayoutParams are created inline with magic constants (-1, -2) for MATCH_PARENT/WRAP_CONTENT.
     - Resource resolution uses ctx.getColor/getString calls directly within view initializers for clarity.
   - **Rationale**
     - apply-based construction is consistent and reduces boilerplate; the use of -1/-2 aligns with common Android patterns but could be replaced with LayoutParams constants for readability.

2. **Lines:** 55-55
   - **Guideline**
     - Multiple property assignments are placed on one line separated by semicolons (e.g., setting minHeight/minWidth).
   - **Rationale**
     - This is legal Kotlin but less idiomatic and may slightly reduce readability.

3. **Lines:** 166-169
   - **Guideline**
     - Single-line lambda body uses a semicolon followed by 'true' (onFilter(...); true) to ensure a Boolean is returned after a side-effecting call.
     - This idiom is compact but slightly terse and therefore highlighted for clarity.
   - **Rationale**
     - N/A


**Event Handling**
1. **Name toggle click**
   - Lines: 150-150
   - **Trigger Type:** MaterialButton (Name)
   - **Behavior**
     - When the Name toggle button is clicked, activateName() is invoked to set UI to name mode and trigger a live filter with the current text.
     - Impact: switches to live filtering mode and hides the explicit MAC search button.
   - **Impact**
     - Changes UI state to name-based live filtering and calls onFilter(false) with current input.

2. **MAC toggle click**
   - Lines: 151-151
   - **Trigger Type:** MaterialButton (MAC)
   - **Behavior**
     - When the MAC toggle button is clicked, activateMac() is invoked to set UI to MAC mode and clear any existing filter.
     - Impact: switches to explicit-trigger MAC search mode and reveals the search button.
   - **Impact**
     - Changes UI state to MAC search mode and calls onFilter with an empty query to clear live filters.

3. **Search input text changed**
   - Lines: 154-160
   - **Trigger Type:** EditText (search input)
   - **Behavior**
     - TextWatcher.afterTextChanged invokes onFilter for live filtering only when in name mode (searchByMac == false).
     - Impact: triggers repeated onFilter calls as user types, enabling real-time filtering behavior in name mode.
   - **Impact**
     - Frequent calls to onFilter may drive list updates in the host component.

4. **Search button click**
   - Lines: 163-165
   - **Trigger Type:** MaterialButton (search button)
   - **Behavior**
     - Clicking the explicit search button triggers onFilter with byMac = true to perform a MAC search.
     - Impact: initiates search action for MAC mode; typically results in a single search request/run.
   - **Impact**
     - Single explicit search trigger.

5. **IME search action**
   - Lines: 166-170
   - **Trigger Type:** EditText IME action
   - **Behavior**
     - When the keyboard 'Search' action is pressed, if the sheet is in MAC mode the listener invokes onFilter with byMac = true and returns true to consume the IME action.
     - Impact: allows users to use keyboard action to submit MAC searches without touching the on-screen button.
   - **Impact**
     - Triggers MAC search and consumes the IME event when applicable.

6. **Close button click**
   - Lines: 173-177
   - **Trigger Type:** MaterialButton (close)
   - **Behavior**
     - When the close button is clicked, the code computes whether a non-empty query is present, calls onDismissed(hasQuery) to notify the host, and dismisses the dialog.
     - Impact: informs the host of dismissal context (whether a query existed) and closes the sheet.
   - **Impact**
     - Triggers external UI changes in the host via onDismissed and closes the dialog.
