**FileName:** bg_tab_unselected.xml   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/res/drawable/bg_tab_unselected.xml   
**Tags:** android, drawable, ui, resource, xml   

**File Summary**
An Android drawable XML resource that defines a rectangular shape used as a background for an unselected tab. The drawable sets rounded corners with a fixed radius and fills the shape with a color referenced from the app's color resources. This file is a static UI asset used by layouts or components that require a consistent inactive-tab background.

**Function Summaries**
1. **Rectangle shape drawable (bg_tab_unselected)**
   - Category: UI Drawable, XML Resource
   - Lines: 2-6
   - **Description**
     - Defines a rectangle-shaped drawable resource used as the background for an unselected tab state.
     - Specifies rounded corners and a solid fill color, delegating the color value to a named color resource for theming consistency.
   - **Parameters description**
     - This XML resource does not accept runtime parameters; it is referenced by resource name from layout or style files. Its appearance is driven by attributes and referenced resources.
   - **Returns description**
     - This file does not return values. When referenced, it provides a Drawable object to the Android UI system representing the configured shape.


**Configuration References**
1. **@color/color_tab_inactive_bg**
   - Line: 5
   - **What it does:**
     - Provides the fill color for the drawable. The drawable's visual color depends on this resource, enabling centralized color theming and easy modifications across the app.
   - **Default value**
     - N/A


**Code Walkthroughs**
1. **Lines:** 4-4
   - **What it does**
     - Sets the corner radius of the rectangle to 10dp, producing rounded corners on the drawable.
   - **Why it matters**
     - Corner radius is a visual detail that affects how the component integrates with surrounding UI; it's specified in density-independent pixels (dp) to maintain consistent appearance across screen densities.

2. **Lines:** 5-5
   - **What it does**
     - Fills the shape with a solid color referenced by the resource identifier @color/color_tab_inactive_bg.
   - **Why it matters**
     - Color is not hard-coded; referencing a color resource centralizes theme control and allows the color to be changed without editing this drawable.


**Style Conventions**
1. **Lines:** 1-6
   - **Guideline**
     - Uses concise XML structure and Android resource references. The file uses dp units for dimensions and color resource reference for theming consistency.
   - **Rationale**
     - Following Android resource conventions (dp for sizes, @color for colors) ensures predictable rendering across devices and centralizes visual attributes.
