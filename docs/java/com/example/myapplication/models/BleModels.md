**FileName:** BleModels.kt   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/java/com/example/myapplication/models/BleModels.kt   
**Tags:** models, android, bluetooth, utility, kotlin   

**File Summary**
Defines simple Kotlin model types and a small UI utility used in the Android app. The file contains data classes representing BLE and classic Bluetooth devices, an enum for active UI tab selection, and an Int extension to convert density-independent pixels (dp) to screen pixels using Android display metrics. The file mixes domain models and a UI conversion helper.

**Function Summaries**
1. **BleDeviceItem**
   - Category: data class, model
   - Lines: 5-9
   - **Description**
     - Represents a Bluetooth Low Energy (BLE) device item with minimal properties used by the app (likely for listing devices in the UI or internal state).
     - Holds the device name, MAC address, and RSSI (signal strength) as immutable properties.
   - **Parameters description**
     - Three primary immutable properties that describe a BLE device: name, address, and current RSSI.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | name | String | User-visible or discovered name of the BLE device. |
     | address | String | Device MAC address or unique identifier string. |
     | rssi | Int | Received signal strength indicator for the device (integer). |
   - **Returns description**
     - Data class instance representing a BLE device.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | BleDeviceItem | BleDeviceItem | An immutable value object containing the provided device fields. |

2. **ClassicDeviceItem**
   - Category: data class, model
   - Lines: 11-16
   - **Description**
     - Represents a classic (BR/EDR) Bluetooth device item including optional/defaulted fields.
     - Includes name and address, with default values for rssi and type to simplify construction when those values are not available.
   - **Parameters description**
     - Four immutable properties describing a classic Bluetooth device; rssi and type have default integer values.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | name | String | User-visible or discovered name of the classic Bluetooth device. |
     | address | String | Device MAC address or unique identifier string. |
     | rssi | Int | Received signal strength indicator; defaults to 0 if not provided. |
     | type | Int | Device type code (integer) with a default of 0 when unspecified. |
   - **Returns description**
     - Data class instance representing a classic Bluetooth device.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | ClassicDeviceItem | ClassicDeviceItem | An immutable value object containing the provided device fields, using defaults where omitted. |

3. **ActiveTab**
   - Category: enum
   - Lines: 18-20
   - **Description**
     - Enumerates which device listing tab is currently active in the UI: BLE or CLASSIC.
     - Likely used to toggle or track UI state for showing BLE devices vs classic Bluetooth devices.
   - **Parameters description**
     - No parameters; enum values represent distinct UI tabs.
   - **Returns description**
     - An enum value indicating the active tab.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | ActiveTab | ActiveTab | One of ActiveTab.BLE or ActiveTab.CLASSIC representing selected view state. |

4. **Int.dp (extension)**
   - Category: extension function, utility
   - Lines: 22-24
   - **Description**
     - Kotlin extension function on Int that converts a value expressed in dp (density-independent pixels) to raw device pixels using the provided Android Context's display metrics.
     - Used to translate layout/size values from dp to pixels for drawing or measurement at runtime.
   - **Parameters description**
     - Accepts an Android Context to access resources and display metrics for density-based conversion.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | context | Context | Android Context used to access resources.displayMetrics.density for conversion. |
   - **Returns description**
     - An integer pixel value computed from the dp value multiplied by screen density; value is truncated via toInt().
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | pixelValue | Int | The computed pixel count resulting from dp * density, converted to Int using truncation. |


**Code Walkthroughs**
1. **Lines:** 22-24
   - **What it does**
     - Converts a numeric dp value (the receiver Int) to physical pixels using context.resources.displayMetrics.density.
     - The multiplication result is converted to Int using toInt(), which truncates any fractional component rather than rounding.
   - **Why it matters**
     - This conversion is central to correct sizing on different screen densities; the choice of toInt() implies truncation behavior which affects final pixel rounding.


**Style Conventions**
1. **Lines:** 5-16
   - **Guideline**
     - Uses concise Kotlin data class declarations with primary constructor properties and default parameter values for ClassicDeviceItem.
     - Naming follows lowerCamelCase for properties and UpperCamelCase for types; files are organized under package com.example.myapplication.models.
   - **Rationale**
     - This style leverages Kotlin idioms for immutable value types and clear, compact model definitions suitable for serialization or UI display.

2. **Lines:** 22-24
   - **Guideline**
     - Defines an extension function on Int for dp conversion placed in the same models file.
     - Extension function uses Context as parameter rather than a global or injected resource provider.
   - **Rationale**
     - Placing utility extensions with models is acceptable but notable because it mixes model types and UI utilities in one file.
