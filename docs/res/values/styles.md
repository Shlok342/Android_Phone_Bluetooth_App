**FileName:** styles.xml   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/res/values/styles.xml   
**Tags:** android, ui, styles, material-design   

**File Summary**
An Android XML resource file that defines two reusable Material-styled button styles for the app: ProfessionalPrimaryButton and ProfessionalSecondaryButton. Each style inherits from Material3 button widget styles and overrides shape and text attributes (corner radius, capitalization, and text size). The file centralizes button appearance for consistent UI across the app.

**Function Summaries**
1. **ProfessionalPrimaryButton**
   - Category: UI Style, Material3
   - Lines: 4-11
   - **Description**
     - Defines a primary button style that inherits from Widget.Material3.Button so all Material3 button attributes are available by default.
     - Customizes the button's corner radius, disables automatic all-caps text transformation, and sets a text size to enforce a consistent, professional appearance.
   - **Parameters description**
     - No function parameters. This is a style resource; attributes configured here apply to views that reference this style.
   - **Returns description**
     - No return value; this resource provides styling properties for UI elements.

2. **ProfessionalSecondaryButton**
   - Category: UI Style, Material3
   - Lines: 13-20
   - **Description**
     - Defines a secondary/outlined button style that inherits from Widget.Material3.Button.OutlinedButton to get an outlined visual by default.
     - Overrides corner radius, disables automatic all-caps text transformation, and sets a slightly smaller text size compared to the primary button for visual hierarchy.
   - **Parameters description**
     - No function parameters. This is a style resource; attributes configured here apply to views that reference this style.
   - **Returns description**
     - No return value; this resource provides styling properties for UI elements.


**Code Walkthroughs**
1. **Lines:** 5-5
   - **What it does**
     - Specifies the parent style for the primary button (Widget.Material3.Button) so the style inherits Material3 button behavior, paddings, and color handling.
   - **Why it matters**
     - Important to note inheritance because changes to Material3 defaults or theming will affect this style; reviewers should be aware the style relies on Material3 widget attributes.

2. **Lines:** 7-9
   - **What it does**
     - Sets three core appearance attributes for the primary button: cornerRadius (shape), android:textAllCaps (text transformation), and android:textSize (type scale).
   - **Why it matters**
     - These attributes control visible shape and typography; cornerRadius uses the app/framework attribute (no android: namespace) which maps to Material components' shape attribute, while text attributes use the android: namespace — mixing namespaces is notable for maintainers.

3. **Lines:** 14-14
   - **What it does**
     - Specifies the parent style for the secondary (outlined) button (Widget.Material3.Button.OutlinedButton) so the style uses Material3 outlined button defaults.
   - **Why it matters**
     - Inheritance choice determines default stroke and background behavior; any theme-level Material3 outlined button changes will propagate here.

4. **Lines:** 16-18
   - **What it does**
     - Sets corner radius, disables all-caps transformation, and sets text size for the secondary button to create a consistent but visually distinct secondary action style.
   - **Why it matters**
     - These attributes provide the primary visual differences from the primary button; size and corner radius differences enforce hierarchy.


**Style Conventions**
1. **Lines:** 4-20
   - **Guideline**
     - Style resource names use PascalCase (ProfessionalPrimaryButton, ProfessionalSecondaryButton) rather than snake_case or lowercase. This is a naming convention choice for styles in this file.
     - XML indentation is consistent and readable; attributes mix android: namespaced attributes (android:textAllCaps, android:textSize) with non-namespaced attributes (cornerRadius) — the latter are framework/app attributes from Material components.
   - **Rationale**
     - Consistency in naming and formatting improves readability for new developers; the namespace mixing is noteworthy because it indicates reliance on external (Material) attributes.
