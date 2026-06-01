**FileName:** ProceduralInsightsSheet.kt   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/java/com/example/myapplication/ui/ProceduralInsightsSheet.kt   
**Tags:** ui, insights, android, recyclerview, viewpager   

**File Summary**
Defines a BottomSheetDialog subclass (ProceduralInsightsSheet) that inflates a layout containing a TabLayout and ViewPager2 to show two pages: an "App Status" list of events and a "Device Metrics" list of device sessions. The file also contains an inner RecyclerView.Adapter (AppEventsAdapter) used to render timestamped device events. It composes Android UI components (RecyclerView, ViewPager2, TabLayout) and internal insight manager/adapter classes to populate content.

**Function Summaries**
1. **ProceduralInsightsSheet**
   - Category: class, UI component
   - Lines: 23-71
   - **Description**
     - Implements a BottomSheetDialog that inflates fragment_device_insights layout and wires TabLayout and ViewPager2 to provide two pages: app events and device metrics.
     - Creates and assigns a ViewPager2 adapter (anonymous RecyclerView.Adapter) which provides two pages: the first page shows app events via AppEventsAdapter; the second page shows device sessions via DeviceInsightAdapter populated from DeviceInsightManager.
     - Attaches TabLayoutMediator to synchronize tabs with pages and registers a page change callback to refresh the device sessions list when the user navigates to the second page.
   - **Parameters description**
     - Constructor takes a Context (inherited from BottomSheetDialog). onCreate takes savedInstanceState: Bundle? used per Android lifecycle but not referenced inside onCreate.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | context | Context | Android Context passed to the BottomSheetDialog constructor for view inflation and resource access. |
     | savedInstanceState | Bundle? | Lifecycle bundle provided by Android; not used in the onCreate implementation but included in the method signature. |
   - **Returns description**
     - onCreate returns Unit (no explicit return). The class exposes a nullable deviceInsightAdapter property for external access.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | deviceInsightAdapter | DeviceInsightAdapter? | Nullable property that may hold the DeviceInsightAdapter instance used for the device metrics page. It is declared at class level and intended for access outside onCreate, although a local shadowing variable exists inside onCreate. |

2. **ViewPager Adapter (anonymous RecyclerView.Adapter)**
   - Category: anonymous class, adapter
   - Lines: 33-56
   - **Description**
     - Provides two pages to ViewPager2 by implementing a RecyclerView.Adapter for ViewPager2's internal usage.
     - onCreateViewHolder creates a full-size RecyclerView for each page; onBindViewHolder attaches the appropriate adapter based on page position: AppEventsAdapter for position 0, DeviceInsightAdapter for position 1.
     - getItemCount returns 2 to define two pages.
   - **Parameters description**
     - Standard RecyclerView.Adapter methods with holder and position parameters; the adapter holds no external parameters.
   - **Returns description**
     - Adapter methods conform to RecyclerView.Adapter expectations; overall adapter provides two pages to ViewPager2.

3. **TabLayoutMediator attachment**
   - Category: UI wiring, mediator
   - Lines: 61-63
   - **Description**
     - Creates and attaches a TabLayoutMediator to synchronize the TabLayout and the ViewPager2, setting tab titles 'App Status' for position 0 and 'Device Metrics' for position 1.
     - Ensures tabs reflect the two pages provided by the ViewPager2 adapter.
   - **Parameters description**
     - Lambda receives tab and position; uses position to set tab text.
   - **Returns description**
     - attach() is called on the mediator to start synchronization; no return value used.

4. **ViewPager2 OnPageChangeCallback**
   - Category: event handler, callback
   - Lines: 64-70
   - **Description**
     - Registers an OnPageChangeCallback to the ViewPager2 to detect page selection changes.
     - When the second page (position == 1) becomes visible, requests DeviceInsightManager to provide all sessions and calls updateData on deviceInsightAdapter to refresh the device metrics list.
   - **Parameters description**
     - onPageSelected receives position: Int and reacts when position == 1.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | position | Int | Index of the selected page in the ViewPager2; used to decide whether to refresh device metrics data. |
   - **Returns description**
     - Callback methods return Unit; the important action is calling deviceInsightAdapter?.updateData(...) for refresh.

5. **AppEventsAdapter**
   - Category: class, RecyclerView.Adapter
   - Lines: 73-99
   - **Description**
     - Adapter that renders a list of DeviceEvent items in a RecyclerView, formatting each item's timestamp and message into a single TextView.
     - Holds a SimpleDateFormat instance (timeFormat) to format event timestamps with millisecond precision.
   - **Parameters description**
     - Constructor takes a list of DeviceEvent objects which are displayed by the adapter.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | events | List<DeviceEvent> | List of DeviceEvent objects to be rendered; adapter size and binding are driven by this list. |
   - **Returns description**
     - Adapter provides ViewHolder instances for binding and exposes item count equal to events.size.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | timeFormat | SimpleDateFormat | Formatter used internally to format event timestamps into HH:mm:ss.SSS strings according to the device locale. |


**Code Walkthroughs**
1. **Lines:** 24-32
   - **What it does**
     - A class-level nullable property deviceInsightAdapter is declared (line 24) and then a local variable with the same name is declared inside onCreate (line 32).
   - **Why it matters**
     - This shadowing means the local variable inside onCreate hides the class property; assignments to the local variable will not update the class-level property unless explicitly referenced.

2. **Lines:** 35-39
   - **What it does**
     - onCreateViewHolder of the anonymous adapter constructs a RecyclerView programmatically, sets layout parameters to match parent (-1), and assigns a LinearLayoutManager.
   - **Why it matters**
     - Creating a RecyclerView as the page view for ViewPager2 is an explicit decision: each page hosts its own RecyclerView instance. The layoutParams use -1 (MATCH_PARENT) directly which maps to ViewGroup.LayoutParams constants.

3. **Lines:** 45-51
   - **What it does**
     - onBindViewHolder sets the RecyclerView's adapter depending on page position: for position 0 uses AppEventsAdapter with DeviceInsightManager.getAppEvents(); for position 1 constructs a DeviceInsightAdapter with DeviceInsightManager.getAllSessions().toMutableList() and assigns it.
   - **Why it matters**
     - DeviceInsightAdapter is instantiated within onBindViewHolder for position 1 rather than reused; for position 0 a separate AppEventsAdapter is created using the static DeviceInsightManager data.

4. **Lines:** 66-67
   - **What it does**
     - When the ViewPager2 page changes to position 1, the code calls deviceInsightAdapter?.updateData(DeviceInsightManager.getAllSessions()).
   - **Why it matters**
     - This triggers a refresh of device session data when the user navigates to the Device Metrics tab. The nullable-safe call means no action occurs if deviceInsightAdapter is null.

5. **Lines:** 74-94
   - **What it does**
     - AppEventsAdapter defines a timeFormat SimpleDateFormat and uses it in onBindViewHolder to format the event.timestamp into HH:mm:ss.SSS combined with event.message into a string resource R.string.format_of_message.
     - It gets context from holder.text.context and uses context.getString(...) to produce the displayed text.
   - **Why it matters**
     - Formatting of timestamps and usage of string resource with parameters ensures localization of surrounding text while formatting timestamp and message values explicitly.


**Style Conventions**
1. **Lines:** 24-32
   - **Guideline**
     - A class-level variable deviceInsightAdapter is declared and later a local variable with the same name is declared inside onCreate, resulting in shadowing.
     - Anonymous inner classes are used inline for the ViewPager2 adapter and the OnPageChangeCallback, following a compact style of defining UI behavior within onCreate.
   - **Rationale**
     - Shadowing can lead to confusion about which variable is being referenced; inline anonymous classes are used consistently for concise local behavior.

2. **Lines:** 35-36
   - **Guideline**
     - LayoutParams and sizing use literal -1 values to represent MATCH_PARENT via ViewGroup.LayoutParams(-1, -1).
   - **Rationale**
     - Using explicit numeric constants for layout parameters is an observable style choice in this file.


**Event Handling**
1. **ViewPager Page Selection**
   - Lines: 64-70
   - **Trigger Type:** ViewPager2
   - **Behavior**
     - Registers a ViewPager2.OnPageChangeCallback to observe page selection events. When page 1 (Device Metrics) is selected, it requests fresh session data from DeviceInsightManager and calls updateData on the deviceInsightAdapter.
     - This ensures the Device Metrics list is refreshed when the user navigates to that tab; the callback is registered in onCreate and scoped to the dialog instance.
   - **Impact**
     - Triggers a data refresh on the DeviceInsightAdapter which updates the UI for the Device Metrics page when selected.
