**FileName:** bg_toggle_active.xml   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/res/drawable/bg_toggle_active.xml   
**Tags:** drawable, ui, resources, android-xml   

**File Summary**
An Android drawable XML that defines a layered rounded-rectangle background used for an active toggle state. It composes two stacked shapes: a colored rounded rectangle as the fill and a second rounded rectangle providing a 1dp border with a transparent interior. The file references color resources defined elsewhere and uses Android drawable primitives (layer-list, shape, corners, stroke, solid).

**Function Summaries**
1. **Base fill shape**
   - Category: Drawable, Shape
   - Lines: 3-8
   - **Description**
     - Defines the bottom layer of the drawable: a rounded rectangle used as the main fill of the active toggle background.
     - Sets corner radius to 12dp and fills the shape with a color resource (@color/color_btn_connect), providing the primary visible background.
   - **Parameters description**
     - No function parameters; this is a static XML drawable element.
   - **Returns description**
     - No return values; this element produces a drawable layer that contributes to the final composed drawable.

2. **Border overlay shape**
   - Category: Drawable, Shape, Stroke
   - Lines: 9-15
   - **Description**
     - Defines the top layer which visually provides a border around the base fill by drawing a 1dp stroke with a specified color resource (@color/color_state_idle).
     - Maintains the same 12dp corner radius and sets the interior to transparent, allowing the base fill color to show through while adding an outline.
   - **Parameters description**
     - No function parameters; static XML drawable element.
   - **Returns description**
     - No return values; this element produces a drawable layer that contributes to the final composed drawable.


**Configuration References**
1. **@color/color_btn_connect**
   - Line: 6
   - **What it does:**
     - Specifies the fill color for the base rounded rectangle. Changing this resource will change the primary visible background color of the drawable.
   - **Default value**
     - N/A

2. **@color/color_state_idle**
   - Line: 12
   - **What it does:**
     - Specifies the stroke (border) color for the overlay shape. Modifying this resource will change the outline color drawn around the base fill.
   - **Default value**
     - N/A

3. **@android:color/transparent**
   - Line: 13
   - **What it does:**
     - Used to make the interior of the top layer transparent so the bottom fill shows through while the stroke remains visible.
   - **Default value**
     - N/A


**Code Walkthroughs**
1. **Lines:** 2-2
   - **What it does**
     - Declares the root <layer-list> and the Android XML namespace used to reference framework attributes.
     - Indicates that multiple drawable items will be stacked to produce the final drawable.
   - **Why it matters**
     - Understanding the root element is necessary because it determines layering order: items declared later are drawn on top of earlier ones.

2. **Lines:** 11-13
   - **What it does**
     - Combines a stroke and a transparent solid in the same shape to create an outline without obscuring the base layer.
     - This is how the file achieves a border effect while preserving the fill color of the bottom layer.
   - **Why it matters**
     - The interplay between a transparent solid and a colored stroke is non-obvious to some readers; it explains how the border is drawn without covering the underlying color.


**Style Conventions**
1. **Lines:** 4-14
   - **Guideline**
     - Consistent indentation and compact structure of drawable XML elements make the file easy to read.
     - Dimensions and radii use dp units, which is the correct practice for density-independent sizing in Android resources.
   - **Rationale**
     - Follows Android resource conventions (use of dp units and resource references) which aids maintainability and theming.
