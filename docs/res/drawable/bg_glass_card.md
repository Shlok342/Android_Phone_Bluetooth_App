**FileName:** bg_glass_card.xml   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/res/drawable/bg_glass_card.xml   
**Tags:** android, drawable, ui, resource, xml   

**File Summary**
An Android drawable XML that defines a layered card-style background with rounded corners, a filled base layer and a transparent stroked overlay to create a glass-like bordered card appearance. It references project color resources for fill and border and uses Android framework attributes for shape, corners, solid, and stroke properties.

**Function Summaries**
1. **layer-list root**
   - Category: Drawable, XML resource, Container
   - Lines: 2-16
   - **Description**
     - Serves as the root container for multiple drawable layers using the Android layer-list drawable type.
     - Composes two stacked shape items (a filled rounded rectangle and a transparent rounded rectangle with a stroke) to produce a glass card visual.
   - **Parameters description**
     - No function parameters; this is an XML resource. Attributes on the root provide the Android XML namespace.
   - **Returns description**
     - Not applicable; this resource is used by Android UI components when referenced as a drawable.

2. **Base filled rounded rectangle**
   - Category: Drawable layer, Shape
   - Lines: 3-8
   - **Description**
     - Defines the bottom layer: a rectangle shape with rounded corners and a solid fill color referenced from project colors (@color/color_glass_fill).
     - Provides the main background area of the glass card and establishes the rounded-corner geometry (radius 14dp).
   - **Parameters description**
     - No parameters; uses XML attributes for shape properties (corners radius and solid color reference).
   - **Returns description**
     - Not applicable; contributes a drawable layer when inflated.

3. **Overlay stroked rounded rectangle**
   - Category: Drawable layer, Shape, Border
   - Lines: 9-15
   - **Description**
     - Defines the top layer: a rectangle with the same rounded corners and a 1dp stroke color referenced from project colors (@color/color_glass_border).
     - Uses a transparent solid so only the stroke (border) is visible, creating a bordered overlay that sits above the filled base to complete the glass-card look.
   - **Parameters description**
     - No parameters; uses XML attributes for corners radius, stroke width/color, and a transparent solid.
   - **Returns description**
     - Not applicable; contributes a drawable layer when inflated.


**Configuration References**
1. **@color/color_glass_fill**
   - Line: 6
   - **What it does:**
     - Provides the fill color used by the base shape layer; centralizes color definition outside the drawable so themes or color swaps are easier.
   - **Default value**
     - N/A

2. **@color/color_glass_border**
   - Line: 12
   - **What it does:**
     - Provides the stroke/border color used by the overlay shape; externalized to support consistent theming across the app.
   - **Default value**
     - N/A

3. **android:color/transparent**
   - Line: 13
   - **What it does:**
     - Uses the Android framework's transparent color constant to ensure the overlay does not fill the interior, allowing the base layer to show through.
   - **Default value**
     - N/A


**Code Walkthroughs**
1. **Lines:** 5-5
   - **What it does**
     - Sets the corner radius to 14dp for both layers to ensure matching rounded corners.
   - **Why it matters**
     - Corner radius must match across layers so the stroke and fill align perfectly visually; 14dp is the chosen visual radius.

2. **Lines:** 6-6
   - **What it does**
     - References a project color resource for the fill of the base layer: @color/color_glass_fill.
   - **Why it matters**
     - Color is externalized to the project's color resources so theme changes or color-tuning happen centrally.

3. **Lines:** 12-13
   - **What it does**
     - Defines a 1dp stroke using @color/color_glass_border and sets the fill to transparent so only the border is visible on the top layer.
   - **Why it matters**
     - Using a transparent solid plus a stroke creates an outline overlay over the base fill without obscuring it.


**Style Conventions**
1. **Lines:** 2-16
   - **Guideline**
     - Uses consistent indentation and grouping: root element at top, each <item> block contains a single <shape> definition with its attributes on separate lines.
     - Resource references use the @color namespace and Android platform namespace where appropriate.
   - **Rationale**
     - This formatting improves readability for XML drawables and aligns with typical Android resource conventions.
