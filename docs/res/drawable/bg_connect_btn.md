**FileName:** bg_connect_btn.xml   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/res/drawable/bg_connect_btn.xml   
**Tags:** android, drawable, ui, selector   

**File Summary**
An Android drawable selector XML that defines the visual background for a "Connect" button. It provides two visual states — pressed and default — using layered rectangle shapes to compose a filled rounded rectangle and an overlaid stroke/border. The drawable references project color resources and uses dp units for corner radius and stroke width.

**Function Summaries**
1. **Pressed state drawable**
   - Category: UI Drawable, Selector Item, Layer-list
   - Lines: 3-19
   - **Description**
     - Defines the visual appearance of the button when android:state_pressed is true.
     - Composes two layered rectangle shapes: a filled rounded rectangle using color_btn_connect_pressed and an overlaid transparent rectangle that provides a 1dp stroke using color_state_idle, creating a border effect while keeping the interior filled.
   - **Parameters description**
     - No function parameters; this block is an XML drawable item describing visual state.
   - **Returns description**
     - No return values; it defines an XML resource used by the Android framework to draw the pressed state.

2. **Default state drawable**
   - Category: UI Drawable, Selector Item, Layer-list
   - Lines: 20-36
   - **Description**
     - Defines the default (non-pressed) visual appearance of the button.
     - Composes two layered rectangle shapes: a filled rounded rectangle using color_btn_connect and an overlaid transparent rectangle that provides a 1dp stroke using color_btn_border, giving a bordered rounded button look.
   - **Parameters description**
     - No function parameters; this block is an XML drawable item describing visual state.
   - **Returns description**
     - No return values; it defines an XML resource used by the Android framework to draw the default state.


**Configuration References**
1. **@color/color_btn_connect_pressed**
   - Line: 8
   - **What it does:**
     - Color used as the fill for the pressed state background; controls pressed-state visual feedback.
   - **Default value**
     - N/A

2. **@color/color_state_idle**
   - Line: 14
   - **What it does:**
     - Color used for the stroke/border overlay in the pressed state; differentiates the border color when pressed.
   - **Default value**
     - N/A

3. **@color/color_btn_connect**
   - Line: 25
   - **What it does:**
     - Color used as the fill for the default state background; controls normal appearance of the button.
   - **Default value**
     - N/A

4. **@color/color_btn_border**
   - Line: 31
   - **What it does:**
     - Color used for the stroke/border overlay in the default state; defines the unpressed border color.
   - **Default value**
     - N/A

5. **android:color/transparent**
   - Line: 15,32
   - **What it does:**
     - Built-in Android transparent color used to make the overlay shape's fill transparent so the bottom fill shows through, while still drawing the stroke.
   - **Default value**
     - N/A


**Code Walkthroughs**
1. **Lines:** 4-18
   - **What it does**
     - Uses a layer-list to stack two shape drawables: a filled shape (solid) under a shape that defines the stroke with a transparent fill.
     - This achieves a combined filled interior with a visible border without overlapping the fill color with the stroke's fill.
   - **Why it matters**
     - Layering a filled shape beneath a transparent-stroked shape is a common non-obvious pattern to get a distinct border while preserving the fill color; it's important to understand this to adjust border/fill independently.

2. **Lines:** 21-35
   - **What it does**
     - Repeats the same layering approach for the default state with different resource colors.
     - Ensures consistent corner radius and stroke width between states so the UI doesn't visually shift between pressed and default.
   - **Why it matters**
     - Maintaining identical corner radius and stroke width across states prevents layout/appearance shifts; recognizing this makes safe changes easier.

3. **Lines:** 6-7
   - **What it does**
     - Defines a rounded rectangle shape with a corner radius of 12dp.
     - This value controls the button's roundedness for both states.
   - **Why it matters**
     - Corner radius is a key visual parameter; altering it requires changing both state blocks to maintain consistency.

4. **Lines:** 14-15
   - **What it does**
     - Defines a 1dp stroke using a project color and a transparent solid fill for the stroked overlay.
     - The transparent solid ensures the underlying filled shape shows through while the stroke remains visible.
   - **Why it matters**
     - Combining stroke and transparent solid in the overlay is the mechanism that creates the visible border without covering the filled color underneath.


**Style Conventions**
1. **Lines:** 1-2
   - **Guideline**
     - XML declares the Android namespace via the standard xmlns attribute on the selector root node.
     - This is required for the android: attributes used throughout the file.
   - **Rationale**
     - Namespace declaration is required for proper parsing of android-specific attributes.

2. **Lines:** 6-33
   - **Guideline**
     - Consistent use of dp units for corner radius and stroke width (12dp and 1dp) and resource references for colors.
     - Shapes and layers are repeated for each state with identical geometry to keep visual consistency.
   - **Rationale**
     - Consistent units and duplicated geometry ensure predictable cross-density rendering and state transitions.

3. **Lines:** 3-36
   - **Guideline**
     - File follows a straightforward, indented XML structure with repeated blocks for pressed and default states.
     - Resource names follow a clear naming convention (color_btn_*, color_state_*, color_btn_border) indicating purpose.
   - **Rationale**
     - Naming conventions and clear structure help maintainability and make resource mapping easier for UI designers and developers.
