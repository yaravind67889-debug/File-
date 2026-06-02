# Offline File Sharing (P2P Client)

An elegant, fully-featured Android peer-to-peer (P2P) file sharing application built with **Kotlin** and **Jetpack Compose**. It allows lightning-fast file exchanges without active cellular logs, mobile connections, internet data, or centralized cloud proxies using Wi-Fi Direct, Bluetooth Low Energy, and local system access points.

---

## 🚀 Key Architectural Pillars

This repository conforms to **Android MVVM Architecture** and modern Jetpack practices:

```
        ┌─────────────────────────────────────────────────────────┐
        │                       Jetpack UI                        │
        │               (Material 3 Compose Views)                │
        └───────────────────────────┬─────────────────────────────┘
                                    │
                                    ▼
        ┌─────────────────────────────────────────────────────────┐
        │                        ViewModel                        │
        │              (OfflineSharingViewModel)                  │
        └───────────────────────────┬─────────────────────────────┘
                                    │
            ┌───────────────────────┴───────────────────────┐
            ▼                                               ▼
┌───────────────────────┐                       ┌───────────────────────┐
│      Repository       │                       │      P2PManager       │
│  (SharingRepository)  │                       │   (Transport Engine)  │
└───────────┬───────────┘                       └───────────────────────┘
            │
            ▼
┌───────────────────────┐
│     Room Database     │
│  (Transfer History)   │
└───────────────────────┘
```

1. **Jetpack Compose Presentation Layer**: Written entirely in dynamic Material 3 with edge-to-edge drawing, supporting fluid scrolling list containers, radar waveforms, and dynamic dark mode toggles.
2. **OfflineSharingViewModel**: Manages the application states, coordinates folder selection, local databases, and handles streaming event flows.
3. **P2PManager (High-Fidelity Virtual Transport Engine)**: Simulates local peer discovery, handshakes, TCP sockets, and transport stream state transitions when hardware radios are blocked or in simulation environments.
4. **SharingRepository**: Handles loading files from actual Android media stores, manages transfer history records, and maps database entities.
5. **OfflineSharingDatabase (Room Core)**: Encapsulates SQLite data structures to persist historical file transfer sessions, favorite target peers, and custom device definitions offline permanently.

---

## 🎨 Visual Identity & Material 3 Styling

The interface is styled within a premium **Slate-Ocean Aesthetic**:
* **High-Contrast Dark Canvas**: Eye-safe deep slate background paired with crisp high-readability text elements.
* **Ambient Lighting Effects**: Includes circular gradient radars, interactive neon pulse wave widgets, and status-colored file chips.
* **Custom Adaptive Icon**: Centers a custom, AI-generated modern high-speed transmission nodular logo inside safe Material You boundaries.

---

## ✨ Features Checklist

* [x] **High-Performance Device Discovery**: Dynamic radar scanners detecting nearby users and mapping signal ratings.
* [x] **Secure Peer Verification**: Integrated dynamic PIN generator pairing and custom QR-code reader widgets.
* [x] **Sophisticated File Explorer**: Groups local items into Videos, Images, Documents, Audios, and APKs with support for full multi-selection.
* [x] **Real-Time Transfer Indicators**: Displays transfers in progress with file names, transfer speeds, percentage bars, and pause/resume handles.
* [x] **Saved Companion Devices**: Quick-send list to easily initiate transfers back to saved favorites.
* [x] **Flexible Local Preferences**: Customizable local device labels, Dark Theme switches, and auto-accept configurations.

---

## 🛠️ Security & Permissions Flow

To perform secure offline handshakes, the application manages the following permissions cleanly:
* `ACCESS_FINE_LOCATION` & `ACCESS_COARSE_LOCATION` (Required for Wi-Fi Direct scanning and local peer mapping).
* `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` & `BLUETOOTH_ADVERTISE` (Required to scan for and establish communication links on Android 12+).
* `NEARBY_WIFI_DEVICES` (Required on Android 13+ to stream data locally over Wi-Fi without location detection).
* `READ_MEDIA` elements / `READ_EXTERNAL_STORAGE` (To read and send files from device folders).

If permissions are not granted, the app launches a gorgeous **Onboarding Walkthrough** inviting the user to confirm permissions natively before loading the main screens.

---

## 📦 How to Build the App

1. Import the root repository directory into **Android Studio (Jellyfish or newer)**.
2. The project uses Gradle Kotlin DSL with standard values.
3. To build the debug APK:
   ```bash
   gradle assembleDebug
   ```
4. To run local Robolectric checkups:
   ```bash
   gradle :app:testDebugUnitTest
   ```
5. Install the output APK directly on any Android Device running **SDK 24 (Android Nougat) or newer**.
