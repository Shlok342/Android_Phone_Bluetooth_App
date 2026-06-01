**FileName:** MainUiFactory.kt   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/java/com/example/myapplication/ui/MainUiFactory.kt   
**Tags:** ui, android, bluetooth, adapters, event-handling   

**File Summary**
This Kotlin file builds the main UI for an Android Bluetooth app entirely in code (no XML layouts). It defines a UiComponents data class that collects references to created views and an object MainUiFactory with a build(...) function that programmatically constructs views, adapters, event listeners, layout structure, and returns a UiComponents instance. The file wires callbacks for scanning, connecting, filtering, tab switching, and manages window insets and list animations.

**Function Summaries**
1. **UiComponents**
   - Category: data class
   - Lines: 18-35
   - **Description**
     - Holds references to all major UI elements created by MainUiFactory.build so the caller can manipulate the UI after construction.
     - Serves as a single aggregated return value providing access to views (rootFrame, list views, texts, buttons), adapters, and other UI controls.
   - **Parameters description**
     - Each field is a view or adapter instance created during UI construction. Consumers of UiComponents use these references to update UI state, visibility, or attach additional logic.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | rootFrame | FrameLayout | Top-level container holding background and the main layout. |
     | backgroundView | GlassmorphicBackgroundView | Custom background view used behind the main UI. |
     | listView | ListView | ListView showing BLE devices. |
     | classicListView | ListView | ListView showing Classic (non-BLE) devices. |
     | statusText | TextView | Status text for BLE devices (connected/not connected). |
     | classicStatusText | TextView | Status text for Classic devices. |
     | bleHeaderText | TextView | Header text label for BLE devices section. |
     | classicTextHeader | TextView | Header text label for Classic devices section. |
     | bleTabBtn | MaterialButton | Tab button to switch to the BLE view. |
     | classicTabBtn | MaterialButton | Tab button to switch to the Classic view. |
     | classicActionsRow | LinearLayout | Container for Classic-specific action buttons (e.g., features). |
     | transferStatusText | TextView | Text view used to show data transfer status/messages. |
     | deviceAdapter | DeviceAdapter | Adapter backing the BLE device ListView. |
     | classicAdapter | ClassicDeviceAdapter | Adapter backing the Classic device ListView. |
     | bleClearFilterBtn | MaterialButton | Button to clear BLE search/filter results. |
     | classicClearFilterBtn | MaterialButton | Button to clear Classic search/filter results. |
   - **Returns description**
     - Not applicable — this is a data holder type used as the return type of the factory build function.

2. **MainUiFactory**
   - Category: object
   - Lines: 37-381
   - **Description**
     - Namespace object housing a single public API (build) to construct the main UI programmatically.
     - Organizes the UI construction logic and returns a UiComponents instance for external use.
   - **Parameters description**
     - Not applicable — this object wraps the build function.
   - **Returns description**
     - Not applicable — this is a container for the build function.

3. **build**
   - Category: function
   - Lines: 38-380
   - **Description**
     - Constructs and arranges all views for the main screen, sets up adapters, configures click listeners and lambda callbacks, manages visibility and tab switching, applies window inset padding, and assigns list animations.
     - Returns a UiComponents instance containing the created views and adapters so the caller can further manipulate UI or state.
   - **Parameters description**
     - Accepts the hosting activity, collections for BLE and Classic devices, maps of BluetoothDevice objects, and a set of callback lambdas (refresh, stop scan, disconnect, tab switches, features, and connect callbacks).
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | activity | AppCompatActivity | Activity context used to construct Views and resolve resources. |
     | bleDeviceList | MutableList<BleDeviceItem> | Mutable list of BLE device models supplied to the BLE adapter as its data source. |
     | bleDeviceMap | MutableMap<String, BluetoothDevice> | Map from device identifier to BluetoothDevice for BLE devices, used by the adapter for connecting. |
     | classicDeviceList | List<ClassicDeviceItem> | List of Classic device models supplied to the Classic adapter. |
     | classicDeviceMap | Map<String, BluetoothDevice> | Map from identifier to BluetoothDevice for Classic devices. |
     | onRefresh | () -> Unit | Callback invoked when the user presses the Refresh button. |
     | onStopScan | () -> Unit | Callback invoked when the user presses the Stop Scan button. |
     | onDisconnect | () -> Unit | Callback invoked when the user presses the Disconnect button. |
     | onTabBle | () -> Unit | Callback invoked when user switches to the BLE tab. |
     | onTabClassic | () -> Unit | Callback invoked when user switches to the Classic tab. |
     | onFeatures | () -> Unit | Callback invoked when the Classic "Features" button is pressed. |
     | connectBleCallback | (BluetoothDevice) -> Unit | Callback invoked by the BLE adapter when user requests connect to a BLE device. |
     | connectClassicCallback | (BluetoothDevice) -> Unit | Callback invoked by the Classic adapter when user requests connect to a Classic device. |
   - **Returns description**
     - Returns a UiComponents object aggregating all created views and adapters so calling code can retain references to update UI state dynamically.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | UiComponents | UiComponents | An instance containing references to rootFrame, background view, lists, texts, buttons, adapters, and filter buttons created in this function. |

4. **Adapter initialization**
   - Category: initialization, adapters
   - Lines: 54-65
   - **Description**
     - Creates DeviceAdapter and ClassicDeviceAdapter instances that back the BLE and Classic ListViews respectively.
     - Wires connect callbacks so adapters can trigger connection flow outside of the factory.
   - **Parameters description**
     - Uses activity as context, device lists and device maps as data sources, and passes connect callbacks which accept a BluetoothDevice.
   - **Returns description**
     - Creates adapter objects assigned to local variables deviceAdapter and classicAdapter.

5. **BLE header and controls**
   - Category: views, header
   - Lines: 75-113
   - **Description**
     - Creates BLE section header text, a search ImageButton, a MaterialButton to clear filters, and arranges them into a horizontal header row.
     - Configures styling (text size, color, boldness) and initial visibility for the clear-filter button (hidden).
   - **Parameters description**
     - No external parameters; uses activity context and resources to populate strings, drawables, colors and layout values.
   - **Returns description**
     - Instantiated views are added later into the main layout and returned inside UiComponents.

6. **Classic header and controls**
   - Category: views, header
   - Lines: 115-154
   - **Description**
     - Creates Classic devices section header text, a search ImageButton, a MaterialButton to clear filters, arranges them into a header row that is initially hidden.
     - Mirrors BLE header behavior but initialized as invisible until user switches to Classic tab.
   - **Parameters description**
     - No external parameters; uses activity context and resources similarly to the BLE header.
   - **Returns description**
     - Instantiated classic header views are added to the layout and returned via UiComponents.

7. **Search sheet listeners**
   - Category: event handling, listeners
   - Lines: 156-179
   - **Description**
     - Opens a DeviceSearchSheet when BLE or Classic search buttons are clicked; passes callbacks to apply filters and to adjust visibility of the corresponding Clear Filter button on dismiss.
     - Calls adapter.applyFilter(query, byMac) in response to search input and toggles clear-filter visibility based on whether a query exists when the search sheet dismisses.
   - **Parameters description**
     - Uses activity context and adapter references; onFilter receives query and byMac boolean; onDismissed receives hasQuery boolean.
   - **Returns description**
     - No return; interacts with adapters and button visibility.

8. **Main layout assembly**
   - Category: layout, container
   - Lines: 181-187
   - **Description**
     - Creates a vertical LinearLayout and adds BLE header row, Classic header row, and a status text view as top elements of the screen.
     - Serves as the primary content container that later receives buttons, tabs, lists and other components.
   - **Parameters description**
     - No parameters; layout constructed using activity context.
   - **Returns description**
     - The layout is later inserted into the rootFrame and returned in UiComponents.

9. **Action buttons row and control buttons**
   - Category: views, actions
   - Lines: 188-237
   - **Description**
     - Creates a horizontal button row with Refresh, Stop Scan, and Disconnect MaterialButtons, configures styling and click callbacks that call onRefresh, onStopScan and onDisconnect respectively, then adds them to the main layout.
     - Sets layout margins and uses equal weight to distribute the three buttons across the row.
   - **Parameters description**
     - Uses activity resources for strings and colors; click handlers are provided by build's parameters.
   - **Returns description**
     - Buttons are added to the layout and returned through UiComponents via references (disconnect, refresh, stop are local but UI effects are exposed via callbacks and returned components).

10. **Tabs and tab row**
   - Category: views, tabs
   - Lines: 239-275
   - **Description**
     - Creates BLE and Classic tab MaterialButtons with selected/unselected styling and places them in a horizontal tab row.
     - Tab buttons are later wired to switch visible sections when clicked.
   - **Parameters description**
     - No external parameters; uses activity resources to determine labels and colors.
   - **Returns description**
     - Tab buttons are returned in UiComponents so calling code can read or change their state if needed.

11. **Tab click handlers (switch views)**
   - Category: event handling, state
   - Lines: 324-353
   - **Description**
     - Defines click behavior for BLE and Classic tab buttons: they invoke onTabBle/onTabClassic callbacks and toggle view visibility, header visibility, tab styling, and which list/status/action areas are visible.
     - Manages the UI state transitions between BLE and Classic sections entirely in code.
   - **Parameters description**
     - No parameters; uses captured references to views and activity resources for colors and drawables.
   - **Returns description**
     - No return; directly mutates view properties (visibility, background, text color).

12. **Classic list, actions and transfer status**
   - Category: views, list, actions
   - Lines: 276-316
   - **Description**
     - Instantiates classicStatusText, a classic ListView (initially hidden), a classicActionsRow with a Features button, and a transferStatusText used to show transfer-related state (hidden by default).
     - Sets classicListView.adapter to the previously created classicAdapter; classicActionsRow is populated with the featuresBtn wired to onFeatures.
   - **Parameters description**
     - Uses activity resources for strings and styling; classic adapter and click handlers are captured from earlier initialization.
   - **Returns description**
     - These views are added to the main layout and returned inside UiComponents for external manipulation.

13. **Root frame, background, window insets handling**
   - Category: views, system insets
   - Lines: 355-367
   - **Description**
     - Creates a FrameLayout as the top-level container, adds a GlassmorphicBackgroundView and the constructed layout into it, sets the activity content view to this root frame, and registers an OnApplyWindowInsetsListener to apply system bar insets as top/bottom padding to the main layout.
     - Ensures the layout avoids overlapping system bars by using WindowInsetsCompat.getInsets for systemBars.
   - **Parameters description**
     - No parameters; uses ViewCompat.setOnApplyWindowInsetsListener and WindowInsetsCompat to read system insets.
   - **Returns description**
     - Root frame and backgroundView are returned via UiComponents for external use.

14. **Adapters assignment, animations, and return**
   - Category: finalization, animations
   - Lines: 369-379
   - **Description**
     - Assigns the created deviceAdapter to the BLE ListView, sets layout animations for both lists using an XML animation resource, and returns a UiComponents object bundling created views and adapters.
     - Finalizes UI setup so calling code receives all references needed to operate the UI (e.g., updating lists, toggling visibility).
   - **Parameters description**
     - No parameters; uses Activity to load animation resource R.anim.layout_item_slide_in.
   - **Returns description**
     - Returns the fully-populated UiComponents instance.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | UiComponents | UiComponents | Final packed return value with references to all created UI components. |


**Code Walkthroughs**
1. **Lines:** 95-95
   - **What it does**
     - Sets a background tint color for the clear filter MaterialButton using a ColorStateList constructed from a single color value.
   - **Why it matters**
     - Explicitly creating a ColorStateList.valueOf ensures consistent tinting across API levels where backgroundTint handling may differ.

2. **Lines:** 96-97
   - **What it does**
     - Resets MaterialButton intrinsic size constraints and disables state list animator for a flat look and to avoid default elevation animations.
   - **Why it matters**
     - Min/max size adjustments along with stateListAnimator = null customize the MaterialButton appearance and behavior.

3. **Lines:** 101-104
   - **What it does**
     - Clear filter button click clears the adapter filter and hides the clear-filter button itself.
   - **Why it matters**
     - This is a UI action that directly mutates adapter filter state and button visibility; important to know for understanding filter lifecycle.

4. **Lines:** 159-162
   - **What it does**
     - When the DeviceSearchSheet's onFilter is invoked, it applies the filter to the deviceAdapter and immediately hides the BLE clear-filter button (the dismissed callback restores visibility appropriately).
   - **Why it matters**
     - Separation of immediate filter application and visibility state is handled between onFilter and onDismissed callbacks.

5. **Lines:** 234-236
   - **What it does**
     - Adds three buttons to a horizontal LinearLayout using weight-based layout parameters so buttons equally share horizontal space.
   - **Why it matters**
     - Using weight = 1f with width 0 is a common Android pattern to make children share available space.

6. **Lines:** 363-366
   - **What it does**
     - Reads system bar insets and applies them as top and bottom padding to the main layout so UI content is positioned below/above system status/navigation bars.
     - Returns the insets object unchanged as required by the insets listener contract.
   - **Why it matters**
     - Ensures the layout respects system UI insets dynamically (important for devices with display cutouts or gesture/navigation bars).


**Style Conventions**
1. **Lines:** 66-380
   - **Guideline**
     - UI is built entirely programmatically (imperative creation and layout of Views) rather than using XML layout resources.
     - Consistent usage of apply { ... } blocks to configure Views, and dp(activity) extension for converting dp values, creates a compact initialization style.
   - **Rationale**
     - Using programmatic UI provides more dynamic control but diverges from typical Android XML layouts; apply blocks improve readability and grouping of property settings.

2. **Lines:** 90-105
   - **Guideline**
     - MaterialButton instances frequently reset min/max dimensions and disable stateListAnimator to achieve flat, custom-styled buttons without default Material elevation animations.
   - **Rationale**
     - Uniform styling approach applied across multiple buttons for consistent look without platform-default animations or constraints.

3. **Lines:** 234-236
   - **Guideline**
     - LayoutParams usage often uses width = 0 and weight = 1f to distribute space evenly among child views.
   - **Rationale**
     - Weight-based layout distribution is consistently used for rows of equally spaced controls.


**Event Handling**
1. **Control buttons**
   - Lines: 198-232
   - **Trigger Type:** MaterialButton onClick
   - **Behavior**
     - Refresh, Stop Scan, and Disconnect buttons are created and each wired to call external callbacks onRefresh, onStopScan, and onDisconnect respectively when clicked.
     - These callbacks are provided by the caller of build and allow the UI to trigger scanning/connectivity actions without owning that logic.
   - **Impact**
     - Triggers scanning and connection lifecycle operations in hosting code via supplied callbacks.

2. **Tab switching**
   - Lines: 324-353
   - **Trigger Type:** MaterialButton onClick
   - **Behavior**
     - BLE and Classic tab buttons have onClick listeners that call onTabBle/onTabClassic and toggle which header, lists, status texts and actions are visible, as well as update tab button styles.
     - This controls the main UI state: which device type (BLE or Classic) is presented to the user.
   - **Impact**
     - Switches visible UI sections and informs hosting logic via callbacks about the tab change.

3. **Search sheet interactions**
   - Lines: 156-179
   - **Trigger Type:** ImageButton onClick -> DeviceSearchSheet callbacks
   - **Behavior**
     - Clicking BLE or Classic search buttons opens DeviceSearchSheet and supplies onFilter and onDismissed handlers: onFilter applies a filter to the adapter; onDismissed toggles visibility of the corresponding clear-filter button based on whether there was a query.
     - DeviceSearchSheet is responsible for collecting user input (query and byMac flag) and invoking provided handlers.
   - **Impact**
     - Applies search filtering to adapters and updates Clear Filter button visibility.

4. **Adapter connect actions**
   - Lines: 54-65
   - **Trigger Type:** Adapter item actions -> connectCallback
   - **Behavior**
     - Adapters are constructed with connectCallback lambdas. When user initiates a connect action from a list item, these callbacks will be invoked with a BluetoothDevice to let host code perform connection.
     - This decouples UI from connection logic: adapters raise events which build's caller handles.
   - **Impact**
     - Delegates device connection initiation to external logic provided via build parameters.
