**FileName:** MessageParser.kt   
**FilePath:** Android_Phone_Bluetooth_App/master/app/src/main/java/com/example/myapplication/classic/MessageParser.kt   
**Tags:** interface, bluetooth, parser, callback, kotlin   

**File Summary**
Kotlin interface defining a contract for parsing incoming byte streams into ClassicMessage instances. It exposes a nullable callback property for consumers to receive parsed messages, methods to feed raw bytes to the parser, and to reset parser state. This file contains only the interface declaration and no implementation details.

**Function Summaries**
1. **MessageParser**
   - Category: interface
   - Lines: 3-14
   - **Description**
     - Defines the public API for a message parser that consumes raw bytes and produces parsed ClassicMessage objects.
     - Provides a nullable callback property (onMessageParsed) that implementers should invoke when a message is successfully parsed.
     - Declares two control methods: feed(...) to supply raw bytes to the parser, and reset() to clear internal parser state. No implementation is provided here.
   - **Parameters description**
     - The interface declares a feed method that accepts a byte array and a length parameter; no other parameters are present. The onMessageParsed property is a nullable callback function that receives parsed ClassicMessage instances.
   - **Parameters:**
     | Name | Type | Description |
     | --- | --- | --- |
     | bytes | ByteArray | Array containing raw input bytes to be consumed by the parser. Implementations should read up to 'length' bytes from this array. |
     | length | Int | Number of meaningful bytes in the 'bytes' array to be parsed; implementations should limit processing to this length. |
   - **Returns description**
     - Both feed(...) and reset() do not return meaningful values (Kotlin Unit). The interface relies on the onMessageParsed callback to deliver parsed results.
   - **Returns:**
     | Name | Type | Description |
     | --- | --- | --- |
     | Unit | kotlin.Unit | feed(...) and reset() return Unit (void). Parsed messages are delivered asynchronously via the onMessageParsed callback. |


**Code Walkthroughs**
1. **Lines:** 5-6
   - **What it does**
     - Declares a nullable property 'onMessageParsed' of function type ((ClassicMessage) -> Unit)? used as a callback for parsed messages.
   - **Why it matters**
     - This property is the mechanism by which parsed messages are delivered to clients; its nullable function type and placement as an interface property affects how implementers and consumers interact with parser implementations.

2. **Lines:** 8-11
   - **What it does**
     - Signature of the feed method showing a two-parameter contract for supplying raw bytes to the parser (ByteArray and length).
   - **Why it matters**
     - Implementations must adhere to this exact signature and correctly interpret the length argument; callers must respect the length as the number of meaningful bytes in the provided array.


**Style Conventions**
1. **Lines:** 3-14
   - **Guideline**
     - Kotlin interface uses compact declaration style with multi-line formatting for readability (property and method signatures split across lines).
     - The nullable function type ((ClassicMessage) -> Unit)? is declared as a mutable var, indicating implementations may update the callback reference at runtime.
   - **Rationale**
     - The multi-line formatting improves readability for function signatures and complex types; the var vs val choice for the callback is notable for mutability semantics.


**Event Handling**
1. **onMessageParsed callback**
   - Lines: 5-6
   - **Trigger Type:** Parser implementation (internal to classes implementing this interface)
   - **Behavior**
     - A nullable callback property that, when set by a client, will be invoked by the parser implementation whenever a ClassicMessage has been successfully parsed.
     - This is an event-like mechanism: the parser produces events (parsed messages) and consumers register a handler to receive them. The parser implementation controls invocation timing and threading behavior (not specified in this interface).
   - **Impact**
     - Delivers parsed messages to callers; how and when this callback is invoked can affect callers' thread-safety and processing flow (e.g., UI updates, queuing).
