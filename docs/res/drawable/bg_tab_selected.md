**FileName:** bg_tab_selected.xml   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/res/drawable/bg_tab_selected.xml   
**Tags:** android, drawable, ui, resource, xml   

**File Summary**
An Android drawable XML that defines a layered rounded-rectangle background used for a selected tab state. It composes two stacked shapes: a filled rounded rectangle with an active background color and a rounded rectangle overlay providing a 1dp border with a transparent interior. The drawable references app color resources and Android's transparent color and is intended for use in UI components (tabs) to indicate selection.

**Function Summaries**
1. **bg_tab_selected (layer-list container)**
   - Category: Drawable XML, Layer-list
   - Lines: 1-16
   - **Description**
     - Defines a layer-list drawable which stacks multiple shape items to form the final background used for a selected tab UI state.
     - Provides the container/context for two shape items: the base filled rounded rectangle and an overlay rounded rectangle that supplies a border.

2. **Base filled rounded rectangle**
   - Category: Drawable, Shape
   - Lines: 3-8
   - **Description**
     - Renders the underlying rounded rectangle with a corner radius of 10dp and fills it with the active tab background color resource.
     - Forms the visible colored area of the selected tab.
   - **Parameters description**
     - No function parameters; uses XML attributes and a color resource to configure shape appearance.

3. **Border overlay rounded rectangle**
   - Category: Drawable, Shape, Stroke
   - Lines: 9-15
   - **Description**
     - Provides an overlay rounded rectangle with the same corner radius to create a 1dp border using a color resource while keeping the interior transparent.
     - When stacked on top of the base shape, this creates a bordered rounded rectangle appearance around the active tab background.
   - **Parameters description**
     - No function parameters; uses XML attributes, a stroke width and color, and a transparent solid to make interior see-through.


**Configuration References**
1. **@color/color_tab_active_bg**
   - Line: 6
   - **What it does:**
     - Provides the fill color for the base rounded rectangle (the active tab background).
     - Changing this resource will alter the visible tab active background color across any UI components using this drawable.
   - **Default value**
     - N/A

2. **@color/color_glass_border**
   - Line: 12
   - **What it does:**
     - Provides the color for the 1dp border stroke on the overlay rounded rectangle.
     - Alters the border appearance of the selected tab when modified.
   - **Default value**
     - N/A

3. **@android:color/transparent**
   - Line: 13
   - **What it does:**
     - Used to make the overlay shape's interior transparent so the underlying fill shows through while retaining the overlay's stroke.
     - This is an Android framework color constant and ensures consistent transparency behavior.
   - **Default value**
     - N/A

4. **Android XML Namespace (http://schemas.android.com/apk/res/android)**
   - Line: 2
   - **What it does:**
     - Necessary to use android: prefixed attributes in the drawable (e.g., android:shape, android:color, android:radius).
     - Allows the XML parser to resolve Android framework attributes used throughout the file.
   - **Default value**
     - N/A


**Code Walkthroughs**
1. **Lines:** 2-2
   - **What it does**
     - Declares the layer-list element and imports the Android XML namespace used for drawable attributes.
   - **Why it matters**
     - The namespace declaration enables use of android: attributes; it's required for all subsequent drawable attribute references.

2. **Lines:** 5-5
   - **What it does**
     - Specifies the corner radius of 10dp for shapes to create rounded corners on both the base and overlay shapes.
   - **Why it matters**
     - A consistent corner radius across stacked shapes ensures the border and fill align visually.

3. **Lines:** 6-6
   - **What it does**
     - References an app color resource (@color/color_tab_active_bg) to fill the base shape with the active tab background color.
   - **Why it matters**
     - Using a color resource centralizes theming and makes the drawable adaptable to different color configurations.

4. **Lines:** 12-13
   - **What it does**
     - Configures a 1dp stroke with a color resource for the border and sets the overlay's solid fill to transparent so the base fill remains visible.
   - **Why it matters**
     - The overlay uses stroke plus transparent fill to produce only a border; this pattern separates border rendering from fill rendering for clearer theming.


**Style Conventions**
1. **Lines:** 1-16
   - **Guideline**
     - Uses standard Android drawable XML structure with indentation for readability and grouped shape items within a layer-list.
     - Resource references use @color and @android:color notation consistently, and dp units are used for physical dimensions.
   - **Rationale**
     - Consistent XML formatting and resource usage align with Android resource conventions and make it easier for maintainers to update styles.
