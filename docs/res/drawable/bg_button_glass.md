**FileName:** bg_button_glass.xml   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/res/drawable/bg_button_glass.xml   
**Tags:** android, drawable, ui, selector, resource   

**File Summary**
An Android drawable XML selector that defines a reusable button background with a glass-like appearance. It declares two visual states (pressed and default) using layer-list elements composed of shape drawables with rounded corners, fills, and a 1dp border. The file references color resources for theming and uses transparent fill for border layering.

**Function Summaries**
1. **File-level selector**
   - Category: Drawable Selector, XML Resource
   - Lines: 1-52
   - **Description**
     - Defines a state-list drawable (selector) that switches between different layered drawables based on view state.
     - Wraps two item blocks (pressed and default) so this single resource can be used as a background for buttons to handle state changes automatically.
   - **Parameters description**
     - No runtime parameters; this is a static XML resource consumed by Android UI components.
   - **Returns description**
     - This file produces a drawable resource (state-list) used by the Android framework when inflating UI components.

2. **Pressed state selector**
   - Category: Layer-list, Shape, Pressed state
   - Lines: 4-26
   - **Description**
     - Specifies the drawable used when the view is in the pressed state (android:state_pressed="true").
     - Composes two shape layers: a filled rounded rectangle using elevated glass fill color, and an overlaid rounded rectangle stroke (border) with transparent interior to render the outline.
   - **Parameters description**
     - No parameters; the element is chosen by the Android view state mechanism.
   - **Returns description**
     - Produces the pressed-state layered drawable applied when the control is pressed.

3. **Default state selector**
   - Category: Layer-list, Shape, Default state
   - Lines: 28-50
   - **Description**
     - Specifies the drawable used for the default (non-pressed) state.
     - Composes two shape layers similar to the pressed state but uses the neutral glass fill color; the border is drawn with a 1dp stroke and transparent interior to preserve the fill beneath.
   - **Parameters description**
     - No parameters; selected by absence of pressed state.
   - **Returns description**
     - Produces the default layered drawable applied when the control is not pressed.


**Configuration References**
1. **@color/color_glass_fill_elevated**
   - Line: 11
   - **What it does:**
     - Used as the solid fill color for the pressed-state base layer, controlling the elevated glass appearance when a button is pressed.
   - **Default value**
     - N/A

2. **@color/color_glass_border**
   - Line: 20,44
   - **What it does:**
     - Used as the stroke color for the border in both pressed and default states; central to the button outline appearance and theming.
   - **Default value**
     - N/A

3. **@color/color_glass_fill**
   - Line: 35
   - **What it does:**
     - Used as the solid fill color for the default (non-pressed) base layer, providing the base glass look.
   - **Default value**
     - N/A

4. **@android:color/transparent**
   - Line: 21,45
   - **What it does:**
     - Used as the solid color for the stroked layer to keep the interior transparent while the stroke is rendered; ensures the base fill remains visible.
   - **Default value**
     - N/A

5. **XML namespace (xmlns:android)**
   - Line: 2
   - **What it does:**
     - Declares Android XML attributes used throughout the drawable. Required for all android: attributes in the file.
   - **Default value**
     - N/A


**Code Walkthroughs**
1. **Lines:** 6-25
   - **What it does**
     - The pressed state uses a layer-list containing two shape items: one solid-filled rounded rectangle and one rounded rectangle that provides only a stroke (border) with transparent fill.
   - **Why it matters**
     - Layering a solid fill and a transparent-stroked shape creates a visible border without obscuring the underlying fill color and allows reusing the same corner radius for consistent rounding.

2. **Lines:** 15-22
   - **What it does**
     - The second item in the pressed state defines a stroke with width 1dp and color referenced from @color/color_glass_border and pairs it with a transparent solid.
   - **Why it matters**
     - Using a transparent solid for the stroked shape ensures the border is visible while keeping the interior transparent so the filled shape beneath shows through.

3. **Lines:** 30-49
   - **What it does**
     - The default state mirrors the pressed state structure but references a different fill color resource (@color/color_glass_fill) to convey the non-pressed appearance.
   - **Why it matters**
     - Maintaining identical structure between states ensures consistent geometry and border rendering; only fill color differs to indicate state.

4. **Lines:** 10-11
   - **What it does**
     - Defines a corner radius of 12dp for the filled shape in the pressed state and uses a color resource for the fill.
   - **Why it matters**
     - Central visual parameter (12dp) determines roundedness of the button and is repeated to ensure matching corners across layers.


**Style Conventions**
1. **Lines:** 1-52
   - **Guideline**
     - XML is well-structured and indented consistently. Comments are used to separate the pressed and default sections.
     - Resource references use @color/... and android system colors; dimension units use dp for corner radius and stroke width.
   - **Rationale**
     - Consistency with Android resource conventions aids readability and maintainability; use of dp ensures device-density independent sizing.

2. **Lines:** 10-41
   - **Guideline**
     - Corner radius (12dp) is duplicated across layers and states to ensure identical rounded corners across stacked shapes.
   - **Rationale**
     - Explicit repetition ensures visual consistency but means changes must be duplicated in multiple places if modified.
