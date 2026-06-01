**FileName:** bg_star_btn_active.xml   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/res/drawable/bg_star_btn_active.xml   
**Tags:** drawable, ui, xml, resource, styling   

**File Summary**
This XML resource defines a rectangle shape drawable used as a background for a UI element (likely an active 'star' button). It sets rounded corners, a semi-transparent fill color, and a 1dp stroke. The file is a platform Android drawable resource used by layouts or programmatic UI components as a visual asset.

**Function Summaries**
1. **bg_star_btn_active (shape drawable)**
   - Category: drawable, xml, shape
   - Lines: 2-7
   - **Description**
     - Declare a rectangle shape drawable resource used as a background for a UI control (named by filename as an active star button background).
     - Configure rounded corners, a solid fill color (with alpha/transparency), and a 1dp stroke border to render the visual appearance of the control's active state.
   - **Parameters description**
     - This XML uses attributes inside the <shape> element: shape type, corner radius, solid color, and stroke width/color to control the drawable's visual properties.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | android:shape | String | Specifies the geometric type of the drawable; here set to 'rectangle' to draw a rectangular shape. |
     | android:radius (corners) | Dimension (dp) | Defines the corner radius applied to the rectangle to produce rounded corners; set to 10dp. |
     | android:color (solid) | Color (hex) | The fill color for the shape. An 8-digit hex value is used (#AARRGGBB) providing alpha/transparency along with RGB; here it produces a semi-transparent fill. |
     | android:width (stroke) | Dimension (dp) | The stroke/border thickness for the shape; set to 1dp. |
     | android:color (stroke) | Color (hex) | The color for the stroke/border. A 6-digit hex value (#RRGGBB) is used here (opaque). |
   - **Returns description**
     - N/A — this file declares a resource asset and does not return values at runtime.


**Code Walkthroughs**
1. **Lines:** 2-2
   - **What it does**
     - Declare the <shape> element and bind the Android XML namespace used for android:* attributes.
   - **Why it matters**
     - The namespace declaration (xmlns:android) is required so all subsequent android: attributes (shape, color, radius, stroke) are recognized by the Android resource parser.

2. **Lines:** 4-4
   - **What it does**
     - Set rounded corners for the rectangle via a radius value of 10dp.
   - **Why it matters**
     - Corner radius uses device-independent pixels (dp); this affects visual rounding consistently across screen densities.

3. **Lines:** 5-5
   - **What it does**
     - Define the solid fill color using an 8-digit hex code (#55FFB800) which encodes alpha + RGB.
   - **Why it matters**
     - The 8-digit hex indicates ARGB format where the leading byte (0x55) provides partial transparency (~33%), resulting in a semi-transparent fill over the underlying content.

4. **Lines:** 6-6
   - **What it does**
     - Define a stroke (border) with 1dp width and a solid color (#FFCA28).
   - **Why it matters**
     - The stroke visually outlines the shape; the color is specified as a 6-digit hex (opaque) and the width uses dp to maintain consistent thickness across densities.


**Style Conventions**
1. **Lines:** 1-7
   - **Guideline**
     - Follows standard Android drawable XML conventions: XML prolog, namespace declaration, and element/attribute structure.
     - File name uses prefix 'bg_' indicating background resource and suffix 'active' indicating state — consistent naming convention for drawable states.
   - **Rationale**
     - Consistent naming and standard XML formatting make the resource discoverable and predictable for layout usage and state-specific selector references.
