**FileName:** themes.xml   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/res/values-night/themes.xml   
**Tags:** android, theme, ui, night-mode, material3   

**File Summary**
An Android night-mode theme resource that defines the app's Material3 DayNight theme overrides for dark (night) mode. It sets primary and surface colors, text-on colors, and window background/navigation/status bar colors, and explicitly configures the status bar light/dark content behavior for API levels using the tools namespace.

**Function Summaries**
1. **Theme.MyApplication (night)**
   - Category: Style Resource, Theme
   - Lines: 2-11
   - **Description**
     - Defines a named theme style (Theme.MyApplication) that inherits from Theme.Material3.DayNight.NoActionBar for night mode values.
     - Overrides color tokens and system window colors (status bar, navigation bar, and window background) to values suitable for dark/night mode.
     - Specifies whether the system status bar should use light status bar content (icons/text) via android:windowLightStatusBar with a tools:targetApi hint.
   - **Parameters description**
     - This style contains item entries which reference color resources. Each item sets a theme attribute (like colorPrimary or android:windowBackground) to a color resource defined elsewhere in the app.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | colorPrimary | color resource reference | Primary brand color used across the UI; points to @color/color_state_idle for night mode. |
     | colorOnPrimary | color resource reference | Text/icon color that should be used on top of the primary color; points to @color/color_text_primary. |
     | colorSurface | color resource reference | Surface color for UI components like cards and sheets in night mode; points to @color/color_surface. |
     | colorOnSurface | color resource reference | Text/icon color that should be used on top of surfaces; points to @color/color_text_primary. |
     | android:windowBackground | color resource reference | Window background color for activities; points to @color/color_background in night mode. |
     | android:statusBarColor | color resource reference | Color for the system status bar background; set to @color/color_background to match the window background. |
     | android:navigationBarColor | color resource reference | Color for the system navigation bar background; set to @color/color_background to match the window background. |
     | android:windowLightStatusBar | boolean | Boolean indicating whether the status bar should use light status bar styling (dark icons). Here explicitly set to false for night mode via tools:targetApi hint. |
   - **Returns description**
     - No runtime return values — it's a resource style applied by the Android framework when this theme is active.


**Configuration References**
1. **@color/color_state_idle**
   - Line: 3
   - **What it does:**
     - Used as the colorPrimary for this theme in night mode; determines the primary accent color seen across UI components.
   - **Default value**
     - N/A

2. **@color/color_text_primary**
   - Line: 4,6
   - **What it does:**
     - Used as on-primary and on-surface text/icon color to ensure readability on primary and surface backgrounds in night mode.
   - **Default value**
     - N/A

3. **@color/color_surface**
   - Line: 5
   - **What it does:**
     - Used as the surface color for components like cards and sheets in night mode.
   - **Default value**
     - N/A

4. **@color/color_background**
   - Line: 7,8,9
   - **What it does:**
     - Used for the window background, status bar, and navigation bar to provide a consistent dark background across the UI in night mode.
   - **Default value**
     - N/A


**Code Walkthroughs**
1. **Lines:** 1-1
   - **What it does**
     - Defines the XML namespace for the 'tools' attribute usage within this resource file.
   - **Why it matters**
     - tools:targetApi is used later on line 10; the namespace declaration is required for that attribute to be valid and to limit its effect to tooling/build-time rather than runtime behavior.

2. **Lines:** 10-10
   - **What it does**
     - Sets android:windowLightStatusBar to false and uses tools:targetApi="m" to indicate that the attribute value is relevant starting from API level M (Android 6.0).
   - **Why it matters**
     - The tools:targetApi hint is a build-time indicator to Lint and tools; android:windowLightStatusBar controls whether status bar icons should be dark (true) or light (false). Explicitly setting it to false here ensures night mode uses light status bar content, and the tools:targetApi avoids runtime warnings on older APIs.


**Style Conventions**
1. **Lines:** 2-11
   - **Guideline**
     - Uses a single <style> block to group all theme overrides for night mode.
     - Follows Android resource naming conventions (@color/...), and uses Material3 DayNight parent theme to inherit adaptive behavior.
   - **Rationale**
     - Keeping theme overrides centralized and inheriting from Material3 DayNight helps maintain consistency across light/dark themes and leverages platform-provided behaviors.

2. **Lines:** 1-10
   - **Guideline**
     - Uses the tools namespace to provide build-time annotations (tools:targetApi) without affecting runtime on older devices.
   - **Rationale**
     - tools attributes are a common pattern in resource XML to suppress Lint warnings or indicate API-specific attributes.
