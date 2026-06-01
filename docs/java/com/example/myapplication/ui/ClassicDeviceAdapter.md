**FileName:** ClassicDeviceAdapter.kt   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/java/com/example/myapplication/ui/ClassicDeviceAdapter.kt   
**Tags:** ui, adapter, bluetooth, favorites, device-names   

**File Summary**
Kotlin Android ListView adapter that renders a list of classic Bluetooth devices, providing UI controls to connect, edit device display names, and toggle favorites. It wraps device model items (ClassicDeviceItem), looks up actual BluetoothDevice instances via a provided map for connection callback, and uses local stores (DeviceNameStore, FavoriteStore) to persist names and favorites. The adapter supports client-side filtering (by name or MAC) and customizes button appearance and dialog styling with resource drawables and colors.

**Function Summaries**
1. **ClassicDeviceAdapter (class)**
   - Category: Class, Adapter, UI
   - Lines: 21-145
   - **Description**
     - Primary adapter class for rendering Bluetooth Classic device rows in a ListView/GridView by extending BaseAdapter.
     - Holds constructor-injected data (context, device list, map of BluetoothDevice objects) and a connection callback; exposes filtering utilities and view binding logic for each row.
     - Coordinates with DeviceNameStore and FavoriteStore to display and persist user edits and favorite states.
   - **Parameters description**
     - Constructor parameters: context, list of ClassicDeviceItem model objects, a map from MAC address to BluetoothDevice, and a callback invoked to initiate a connection.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | adapterContext | Context | Android Context used by the adapter; typically the Activity or Application context for resource and preference access. |
     | devices | List<ClassicDeviceItem> | List of model items representing discovered classic Bluetooth devices to be displayed. |
     | deviceMap | Map<String, BluetoothDevice> | Map keyed by device MAC address providing the actual BluetoothDevice objects (used when a connect action is triggered). |
     | connectCallback | (BluetoothDevice) -> Unit | Lambda invoked with the BluetoothDevice to start a connection procedure when the Connect button is tapped. |
   - **Returns description**
     - An instance of ClassicDeviceAdapter used by the hosting UI component (ListView/GridView).

2. **getCount / getItem / getItemId**
   - Category: Override, Adapter interface
   - Lines: 27-29
   - **Description**
     - Adapter overrides providing count, indexed item, and stable id to the list view.
     - Each relies on displayList() (the filtered view) rather than the raw devices list to reflect current filter state.
   - **Parameters description**
     - getItem takes an index. getItemId takes an index. getCount takes no parameters.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | p | Int | Zero-based index of the requested item. |
   - **Returns description**
     - getCount returns the number of items in the current (possibly filtered) list; getItem returns the item at index p; getItemId returns the index as a Long.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | getCount | Int | Number of items currently displayed (after filtering). |
     | getItem | ClassicDeviceItem | The ClassicDeviceItem at the requested display index. |
     | getItemId | Long | Stable id for item p (here implemented as the index converted to Long). |

3. **Filter state fields**
   - Category: Fields, State
   - Lines: 31-34
   - **Description**
     - Internal state used to support client-side filtering: the current query string and whether the filter uses MAC address matching.
     - These fields are mutated by applyFilter and clearFilter and influence displayList output.
   - **Parameters description**
     - Not applicable; these are private mutable fields.
   - **Returns description**
     - Not applicable.

4. **displayList**
   - Category: Helper function, Filtering
   - Lines: 35-41
   - **Description**
     - Produces the list of ClassicDeviceItem to display based on the current filter state.
     - If no filter is active, returns the full devices list; otherwise filters by either MAC address or device name (using stored custom names when present).
   - **Parameters description**
     - No parameters; uses adapter's filterQuery and filterByMac fields.
   - **Returns description**
     - Returns a List<ClassicDeviceItem> representing the current visible items after applying filter logic.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | result | List<ClassicDeviceItem> | Filtered list of device items based on filterQuery and filterByMac. |

5. **applyFilter**
   - Category: Mutation, Filtering
   - Lines: 43-45
   - **Description**
     - Updates the adapter's filter query and mode (by MAC or by name) and triggers UI refresh via notifyDataSetChanged().
   - **Parameters description**
     - Takes a query string and a boolean flag indicating whether to interpret the query as a MAC-address filter.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | query | String | Filter text to apply; empty string disables filtering. |
     | byMac | Boolean | When true, filter compares the query against the device address; otherwise compares against the device name (using custom names if stored). |
   - **Returns description**
     - Unit. Causes the adapter to refresh visible items.

6. **clearFilter**
   - Category: Mutation, Filtering
   - Lines: 47-49
   - **Description**
     - Resets filter state to show all devices and triggers a UI refresh via notifyDataSetChanged().
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - Unit. Clears query and resets filter mode.

7. **updateStarButton**
   - Category: Helper, UI
   - Lines: 50-58
   - **Description**
     - Visually updates the star ImageButton to reflect favorite state by swapping icon and background resource.
     - Used by getView to initialize and after toggling favorite state.
   - **Parameters description**
     - Takes the ImageButton control and a boolean indicating favorite state to apply appropriate resources.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | btn | ImageButton | Reference to the ImageButton view to update. |
     | isFavorite | Boolean | When true, set filled star icon and active background; otherwise set outline icon and default background. |
   - **Returns description**
     - Unit. Mutates visual state of the provided button.

8. **getView**
   - Category: Override, View Binding, Event wiring
   - Lines: 59-144
   - **Description**
     - Binds a ClassicDeviceItem to a row view (inflates device_item layout if needed), populates text fields (name/address/signal), wires button click handlers (Connect, Edit Name, Favorite).
     - Uses DeviceNameStore for persisted custom names, FavoriteStore for favorite state, and deviceMap + connectCallback to trigger connection attempts.
   - **Parameters description**
     - Standard BaseAdapter getView parameters: position index, optional recycled view, and parent view group.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | p | Int | Index of item to bind (based on filtered display list). |
     | v | View? | Optional recycled view provided by the ListView for reuse. |
     | parent | ViewGroup | Parent container hosting the row view; used to obtain context and inflate when needed. |
   - **Returns description**
     - Returns a View representing the bound row that will be displayed in the ListView.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | view | View | Inflated or recycled row view with fields populated and event handlers attached. |


**Configuration References**
1. **Android resources (layouts, drawables, strings)**
   - Line: 60,80,96,116,137,50,51,52
   - **What it does:**
     - The adapter inflates layouts (R.layout.device_item, R.layout.dialog_edit_device_name), references drawable resources for star icons and backgrounds, and uses resource IDs to find child views. These resources determine UI structure and visuals and must exist with the expected IDs and names.
   - **Default value**
     - N/A

2. **toColorInt color literals**
   - Line: 117,120
   - **What it does:**
     - Hardcoded hex color strings are converted to integer color values for dialog button text. These literals drive the exact colors used in the dialog action buttons.
   - **Default value**
     - N/A


**Code Walkthroughs**
1. **Lines:** 62-62
   - **What it does**
     - Resolve display name for the device by first checking DeviceNameStore for a custom name; fall back to the model's name if none stored.
   - **Why it matters**
     - This lookup ensures user-defined custom device names take precedence in UI display; understanding this is important when modifying the naming behavior.

2. **Lines:** 71-72
   - **What it does**
     - Attempt to find the BluetoothDevice object from deviceMap by MAC address and invoke the provided connectCallback; otherwise show a short Toast asking user to rescan.
   - **Why it matters**
     - The connection action relies on the deviceMap; if the map does not contain the device, the UI gracefully informs the user instead of crashing.

3. **Lines:** 82-90
   - **What it does**
     - Inflate a custom dialog layout for editing device name, populate the input with current custom or default name, and select all text so user can type immediately.
   - **Why it matters**
     - Using a custom dialog and selecting all text improves UX; developers must preserve view IDs and layout resource names when changing the dialog UI.

4. **Lines:** 96-107
   - **What it does**
     - Handle Save button click: read trimmed input; if empty remove stored custom name, otherwise save it. Then refresh adapter view via notifyDataSetChanged().
   - **Why it matters**
     - This logic persists user edits to DeviceNameStore and ensures the change is immediately reflected in the list view.

5. **Lines:** 116-120
   - **What it does**
     - Change the AlertDialog's positive and negative button text styles: disable all-caps and set explicit text colors using toColorInt conversion.
   - **Why it matters**
     - Direct styling of dialog buttons affects visual consistency; toColorInt usage requires androidx.core import and literals must be valid hex strings.

6. **Lines:** 122-135
   - **What it does**
     - ClearAll button handler: clears all custom names via DeviceNameStore.clearAll, notifies the adapter, shows a Toast confirming the action, and dismisses the dialog.
   - **Why it matters**
     - Batch-clearing persisted names is a destructive operation; the code provides user feedback and updates UI accordingly; ensure DeviceNameStore.clearAll semantics are known before altering behavior.

7. **Lines:** 137-142
   - **What it does**
     - Initialize the favorite star button state using FavoriteStore.isFavorite, and toggle favorite state on click using FavoriteStore.toggle, updating visual state with updateStarButton.
   - **Why it matters**
     - FavoriteStore is the single source of truth for favorite state; the adapter reads and writes to it and reflects changes in the button UI.


**Style Conventions**
1. **Lines:** 67-73
   - **Guideline**
     - Button text is explicitly set to not use all-caps (isAllCaps = false) for better visual consistency with design.
     - Uses Kotlin's safe-call and let with elvis operator to handle optional device lookup concisely.
   - **Rationale**
     - Consistency with dialog button styling and modern Material design practices; concise Kotlin idioms improve readability.

2. **Lines:** 59-144
   - **Guideline**
     - Adapter uses direct findViewById calls for each view bind rather than a ViewHolder pattern; Kotlin synthetic/ViewBinding is not used here.
     - notifyDataSetChanged() is used to refresh the entire list after changes rather than more specific notifyItem... APIs (which are not available on BaseAdapter).
   - **Rationale**
     - These choices are standard for simple BaseAdapter implementations but should be noted when integrating with RecyclerView or optimizing rendering.


**Event Handling**
1. **Connect button click**
   - Lines: 67-74
   - **Trigger Type:** Button tap (UI)
   - **Behavior**
     - Handles taps on the Connect button: looks up BluetoothDevice by MAC address from deviceMap and invokes connectCallback if found; otherwise displays a Toast recommending rescanning.
     - This triggers connection logic outside the adapter via the provided callback; adapter only mediates the user action and user feedback on missing device.
   - **Impact**
     - Invokes an external connect flow (through connectCallback) or shows immediate user feedback via Toast.

2. **Edit Name dialog**
   - Lines: 75-136
   - **Trigger Type:** Button tap -> AlertDialog actions
   - **Behavior**
     - On edit button tap, opens an AlertDialog with a custom view to edit or clear a device's custom name. The dialog Save action persists the new name (or removes it) via DeviceNameStore and refreshes the adapter. The Clear All button clears all custom names and dismisses the dialog.
     - Multiple UI side-effects: updating persistent store, refreshing list view via notifyDataSetChanged(), and presenting Toast confirmations.
   - **Impact**
     - Changes persisted device display names, which affects how devices appear throughout the UI.

3. **Favorite star toggle**
   - Lines: 137-142
   - **Trigger Type:** ImageButton tap
   - **Behavior**
     - Toggles favorite state for a device using FavoriteStore.toggle and updates the star button's appearance.
     - FavoriteStore is changed synchronously and the UI reflects the new state immediately.
   - **Impact**
     - Updates persistent favorite state; may influence other UI or sorting logic elsewhere that reads FavoriteStore.
