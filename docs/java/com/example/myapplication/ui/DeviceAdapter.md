**FileName:** DeviceAdapter.kt   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/java/com/example/myapplication/ui/DeviceAdapter.kt   
**Tags:** ui, bluetooth, adapter, favorites, device-names   

**File Summary**
DeviceAdapter is a Kotlin Android BaseAdapter that renders a list of BLE devices in a ListView/GridView. It manages device display (name, address, RSSI), filtering by name or MAC address, editing and saving custom names, marking favorites, and initiating connect callbacks via an externally supplied function. The adapter reads/writes small local stores (DeviceNameStore, FavoriteStore) and shows device insights via DeviceInsightManager on long-press.

**Function Summaries**
1. **DeviceAdapter (class + constructor)**
   - Category: Class, UI, Adapter
   - Lines: 21-180
   - **Description**
     - Constructs an adapter instance that binds BLE device data to view items and handles user interactions for each device row.
     - Accepts context, a modifiable list of BleDeviceItem, a map to actual BluetoothDevice instances, and a connect callback function provided by the consumer Activity/Fragment.
   - **Parameters description**
     - Constructor parameters configure the adapter context, the dataset to display, a mapping from device address to BluetoothDevice, and a callback to start connecting when a user requests connection.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | adapterContext | Context | Android Context used by the adapter for operations that require context (e.g., DeviceNameStore access, Toasts). |
     | devices | MutableList<BleDeviceItem> | Mutable list of BLE device items that the adapter will display and filter. |
     | deviceMap | Map<String, BluetoothDevice> | Map keyed by device address linking to BluetoothDevice instances used to perform actual connections. |
     | connectCallback | (BluetoothDevice) -> Unit | Callback supplied by the host that is invoked with a BluetoothDevice when the user requests a connection. |
   - **Returns description**
     - An adapter instance which can be set on AdapterView components (ListView/GridView) to show BLE devices and handle interactions.

2. **getCount**
   - Category: Adapter override
   - Lines: 28-28
   - **Description**
     - Returns number of items that should be displayed after applying the current filter.
     - Relies on displayList() to compute the filtered list size.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - Integer count of visible items.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | count | Int | Size of the filtered display list. |

3. **getItem**
   - Category: Adapter override
   - Lines: 30-30
   - **Description**
     - Returns the BleDeviceItem at the given position from the filtered display list.
   - **Parameters description**
     - Index of the item in the current visible list.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | p | Int | Position index in the visible list. |
   - **Returns description**
     - BleDeviceItem for the requested position.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | item | BleDeviceItem | The device item at the given filtered position. |

4. **displayList**
   - Category: Helper, Filtering
   - Lines: 36-42
   - **Description**
     - Produces the list of BleDeviceItem objects to display based on current filter state (filterQuery and filterByMac).
     - If no query is set, it returns the original devices list; otherwise it filters by MAC address or by device name (using DeviceNameStore fallback to original name).
   - **Parameters description**
     - No parameters; reads internal filterQuery and filterByMac state and the devices list.
   - **Returns description**
     - A list of BleDeviceItem that match the active filter criteria (or the complete list if no filter).
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | filteredList | List<BleDeviceItem> | Filtered list according to current query and filter mode. |

5. **applyFilter**
   - Category: Helper, Filtering
   - Lines: 44-46
   - **Description**
     - Sets the filter query and whether to filter by MAC address, then triggers a UI refresh by calling notifyDataSetChanged().
   - **Parameters description**
     - Accepts the new query string and a boolean to select MAC vs name filtering.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | query | String | The filter string to match against device name or MAC address. |
     | byMac | Boolean | If true apply filter against device address; otherwise apply against device name (or custom name). |
   - **Returns description**
     - No return value; side effect is to update filter state and refresh the adapter view.

6. **clearFilter**
   - Category: Helper, Filtering
   - Lines: 48-50
   - **Description**
     - Clears any active filter and resets filter mode, then notifies the adapter to refresh the displayed list.
   - **Parameters description**
     - No parameters.
   - **Returns description**
     - No return value; resets internal filter variables and refreshes UI.

7. **getItemId**
   - Category: Adapter override
   - Lines: 52-52
   - **Description**
     - Returns a stable ID for an item; here it simply returns the position cast to Long.
   - **Parameters description**
     - Position index of the item.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | p | Int | Index of the item whose ID is requested. |
   - **Returns description**
     - Long id for the item.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | id | Long | Position converted to Long used as a unique id in this adapter. |

8. **updateStarButton**
   - Category: Helper, UI
   - Lines: 53-61
   - **Description**
     - Updates the star ImageButton appearance (icon and background) based on whether the device is a favorite.
     - Centralizes the UI state changes for favorite toggle to keep getView cleaner.
   - **Parameters description**
     - Receives the ImageButton to update and a boolean indicating favorite state.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | btn | ImageButton | UI element representing the favorite/star control to update visuals on. |
     | isFavorite | Boolean | If true use filled star and active background; otherwise use outline and default background. |
   - **Returns description**
     - No return value; directly modifies the button visuals.

9. **getView**
   - Category: Adapter override, UI, Event handling
   - Lines: 62-179
   - **Description**
     - Creates or reuses a row view for a device and binds BleDeviceItem data to UI elements (name, address, RSSI).
     - Sets up event handlers for connect button, edit name dialog, long-press insight, and favorite toggling. It also uses DeviceNameStore to resolve custom names.
   - **Parameters description**
     - Receives position, optional recycled view, and parent ViewGroup to inflate layout and bind data.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | p | Int | Position index in the displayed (filtered) list. |
     | v | View? | Optional recycled view provided by the AdapterView for reuse. |
     | parent | ViewGroup | Parent view group, used to obtain LayoutInflater and context as needed. |
   - **Returns description**
     - A View instance representing the device row, populated and wired with event handlers.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | view | View | Inflated or recycled view populated with device data and event listeners. |


**Code Walkthroughs**
1. **Lines:** 36-41
   - **What it does**
     - Filtering logic alternates between MAC-address matching and name matching. For name matching it consults DeviceNameStore for a custom name and falls back to the device's advertised name.
   - **Why it matters**
     - This determines how the displayed list is computed and explains why custom names affect filtering behavior.

2. **Lines:** 77-92
   - **What it does**
     - Connect button initialization sets it disabled with reduced alpha, then defines click behavior that looks up a BluetoothDevice by address and invokes the connect callback if present; if not found it shows a Toast.
   - **Why it matters**
     - The click handler assumes the host Activity will handle scanning/connection flow and relies on deviceMap to map address -> BluetoothDevice; understanding this is crucial for connection flow integration.

3. **Lines:** 108-161
   - **What it does**
     - Constructs an AlertDialog that allows editing the device display name. It uses DeviceNameStore to get/save/remove custom names, provides a 'Clear all custom names' button which clears the store, refreshes the list, and shows a Toast confirmation.
   - **Why it matters**
     - This block performs UI inflation, local storage mutations, and modifies global state (DeviceNameStore), so it's important to know side effects and user-visible behavior.

4. **Lines:** 141-147
   - **What it does**
     - Changes AlertDialog positive/negative button label capitalization and tints the text using fixed hex colors converted via toColorInt().
   - **Why it matters**
     - Manually setting text color via hard-coded hex values is a notable visual customization and uses an extension function to convert the string to a color integer.

5. **Lines:** 163-171
   - **What it does**
     - Long-press listener queries DeviceInsightManager for session data about the device and shows details (device name and number of discovered services) in a Toast or indicates no data is available.
   - **Why it matters**
     - This provides quick access to discovery insights and depends on DeviceInsightManager's session cache existence.

6. **Lines:** 172-177
   - **What it does**
     - Star button initial state is derived from FavoriteStore, and clicking toggles favorite state and updates the UI using updateStarButton().
   - **Why it matters**
     - Favorite persistence and UI update happen through FavoriteStore; toggling affects stored state used across app.


**Style Conventions**
1. **Lines:** 77-79
   - **Guideline**
     - The connect button is explicitly disabled and has an inline comment 'ADD THIS'. This deviates from usual approach where enabled state might be handled externally or via binding.
     - Single-line style used for simple overrides (getCount, getItem) is compact idiomatic Kotlin.
   - **Rationale**
     - The inline comment likely indicates an intentional change; it's notable when scanning code for behavior differences.

2. **Lines:** 36-46
   - **Guideline**
     - Filtering state is maintained in two properties (filterQuery and filterByMac) and helper methods applyFilter/clearFilter mutate them and call notifyDataSetChanged().
     - Use of expression-bodied function for simple overrides and concise Kotlin idioms makes the adapter compact and readable.
   - **Rationale**
     - Consistency with Kotlin concise function style helps readability and maintenance.

3. **Lines:** 121-147
   - **Guideline**
     - AlertDialog is configured fluently with chained setView/setPositiveButton/setNegativeButton/show, and then buttons are customized after show().
     - Colors for dialog buttons are hard-coded hex strings converted with toColorInt(), instead of referencing theme attributes or color resources.
   - **Rationale**
     - Customizing dialog buttons after show() is a standard Android pattern because buttons are not available before show(); however hard-coded hex colors are a style choice worth noting.


**Event Handling**
1. **Connect button click**
   - Lines: 77-102
   - **Trigger Type:** UI Button (MaterialButton)
   - **Behavior**
     - Handles user request to connect to a device. The button is initially disabled/low-alpha, and on click the adapter looks up the BluetoothDevice in deviceMap by address and calls the provided connectCallback with it.
     - If the BluetoothDevice cannot be found in deviceMap a Toast indicates a data mismatch. The actual scan stopping and connection sequence is expected to be implemented by the Activity that provided connectCallback.
   - **Impact**
     - Triggers external connection flow via connectCallback; does not itself start Bluetooth operations.

2. **Edit name button click**
   - Lines: 104-161
   - **Trigger Type:** UI Button (ImageButton -> dialog actions)
   - **Behavior**
     - Opens an AlertDialog with an EditText to set a custom display name for the device. Saving writes to DeviceNameStore (save or remove) and triggers a full adapter refresh; 'Clear all custom names' clears the entire store and shows a Toast.
     - This event updates persisted custom names which influence list display and filtering behavior.
   - **Impact**
     - Mutates DeviceNameStore and refreshes UI; potentially clears names for all devices.

3. **Item long press (insight)**
   - Lines: 163-171
   - **Trigger Type:** View long-click
   - **Behavior**
     - On long-press, fetches a device session from DeviceInsightManager and shows brief device insight information (custom device name and number of discovered services) in a Toast, or indicates missing session data.
   - **Impact**
     - Read-only; shows information to user.

4. **Favorite/star toggle**
   - Lines: 172-177
   - **Trigger Type:** UI Button (ImageButton)
   - **Behavior**
     - Toggles and persists favorite state using FavoriteStore.toggle and updates the star button UI accordingly.
     - This event updates app-wide favorite state for the device.
   - **Impact**
     - Persists favorite flag and updates row visuals.
