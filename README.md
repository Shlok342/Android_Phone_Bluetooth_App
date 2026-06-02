OpenBlue Suite
🔵 OpenBlue Suite

OpenBlue Suite is a free and open-source Android Bluetooth toolkit designed for developers, students, hobbyists, and Bluetooth enthusiasts.

The goal is simple:

Build once. Reuse everywhere.

Instead of rebuilding Bluetooth scanners, pairing systems, connection managers, and debugging utilities from scratch for every project, OpenBlue Suite provides a polished foundation that developers can learn from, extend, or directly integrate into their own Bluetooth workflows.

The project focuses on three principles:

Professionalism over feature bloat
Clean architecture over quick hacks
Practical usability over unnecessary complexity
✨ Why OpenBlue Suite?

Most Bluetooth applications support either:

Bluetooth Low Energy (BLE)

or

Classic Bluetooth

but rarely both in a unified experience.

OpenBlue Suite combines both technologies into a single application while providing powerful debugging and inspection tools that help developers understand what is happening behind the scenes.

🚀 Core Features
Bluetooth Discovery
BLE Device Scanning
Real-time BLE scanning
Fast device discovery
Refresh and rescan support
Scan termination controls
Classic Bluetooth Discovery
Discover nearby Classic Bluetooth devices
Device pairing and bonding support
Connection management
Device Management
Pairing & Bonding

Supports:

BLE devices
Classic Bluetooth devices

with simplified workflows and clear status feedback.

Device Connections
Connect to discovered devices
Disconnect active connections
Connection state monitoring
Reconnection support (where applicable)
Device Insights System

One of the flagship features of OpenBlue Suite.

The Insights Engine provides an IDE-like logging experience for Bluetooth operations.

Examples:

Application Perspective
Scan Started
BLE Scan Callback Registered
Device Found
Connection Attempt Started
Connection Established
Device Perspective
Process Started for Device XYZ
Service Discovery Initiated
Characteristic Read Requested
Notification Stream Enabled
Especially designed logs for managing A2DP connection and providing the user with live logs. 

This makes Bluetooth debugging significantly easier compared to relying solely on Android Logcat.

🎨 Productivity Features
Device Search

Search devices using:

Device Name
MAC Address

making it easier to locate devices in large scan results.

Custom Device Names

Assign your own aliases to devices.

Example:

AA:BB:CC:DD:EE:FF
↓
Living Room Sensor
Favorite Devices

Star frequently used devices for quick access.

Perfect for:

Development boards
Testing hardware
Frequently connected peripherals
🏗 Architecture

The project is organized into modular packages for easier maintenance and future expansion.

com.example.myapplication
│
├── ble
│   ├── Scanning
│   ├── GATT Services
│   ├── Notifications
│   └── BLE Utilities
│
├── classic
│   ├── Discovery
│   ├── Connections
│   ├── Messaging
│   ├── File Transfer
│   └── Reconnection Logic
│
├── insights
│   ├── Insight Manager
│   ├── Formatting
│   └── Insight Models
│
├── models
│   └── Shared Bluetooth Models
│
├── util
│   ├── Permissions
│   ├── Favorites
│   ├── Device Naming
│   ├── UUID Registry
│   └── Utility Helpers
│
└── ui
    ├── Main Screens
    ├── Adapters
    ├── Search Components
    └── Insight Panels
⚙ Technical Details
Category	Details
Platform	Android
Language	Kotlin
UI	XML
Bluetooth Support	BLE + Classic Bluetooth
Minimum SDK	API 26 (Android 8.0)
Target SDK	Android 30+
Architecture	Modular Package Structure
Scanner	Native Android Bluetooth APIs
License	Open Source
🎯 Project Goals

OpenBlue Suite aims to become:

A learning resource for Android Bluetooth development
A reusable Bluetooth foundation for future projects
A developer-friendly Bluetooth debugging tool
A continuously improving open-source Bluetooth ecosystem

📊 Future Implementation: 
1) Adding filtering based on starring, saved devices and nearby devices.
2) File Transfer Feature.
3) Adding a base for printing.

   
🤝 Contributing

Contributions, feature requests, bug reports, and architectural improvements are welcome.

Whether you're fixing a bug, improving documentation, or adding new Bluetooth capabilities, every contribution helps make OpenBlue Suite better.

📌 Vision

OpenBlue Suite is not trying to be the Bluetooth app with the most features.

It aims to be the Bluetooth app that developers actually enjoy using.

Professional. Reliable. Open Source. Built for developers. 🚀
