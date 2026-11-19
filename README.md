# OBD2-android

Companion Android diagnostic application for [`nitrogene/OBD2-firmware`](https://github.com/nitrogene/OBD2-firmware) (ESP32-S3 OBD-II Scanner & ECU Simulator).

---

## 1. Purpose & Key Capabilities

- **Dual Wireless Transport (Single-Active-Transport Discipline):**
  - **BLE 5.0 GATT (`OBD2-ELM327`):** Service `0xFFF0` (`0xFFF1` Notify / `0xFFF2` Write) and alias `0xFFE0` (`0xFFE1`), ATT MTU `256` negotiation, runtime permissions (`BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` on API 31+, `ACCESS_FINE_LOCATION` on API <= 30), and automatic GATT cache refresh.
  - **Wi-Fi SoftAP / TCP (`OBD2-Scanner`):** Static IP `192.168.42.1:35000` (`TCP_NODELAY`), bound to the non-VPN Wi-Fi `Network` via `ConnectivityManager` (with `WifiNetworkSpecifier` fallback on Android 10+ and cellular routing prevention).
  - Global **Disconnect** action cleanly stops live polling, BLE scanning, and background recording before releasing the transport link.
- **ELM327 v1.5 & KWP2000 Client:**
  - Full initialization (`ATZ`..`ATSP0..9`..`0100`..`ATDP`..`ATRV`), CAN single-frame / ISO-TP multi-frame / K-Line response parsing, live PID streaming (`Mode 01`), DTC read/clear (`Mode 03`/`07`/`04` with confirmation), Freeze Frame (`Mode 02`), VIN (`0902`), and K-Line `10,400 baud` cadence guard (`<= 8` PIDs).
  - Security-gated extended mode (`AT+EXT=1` and state-modifying SIDs require explicit user confirmation) plus `VIRTUAL_BENCH` simulator controls (`AT+SIM:...`), `AT+STATS`, and raw AT console.
- **Configurable Pre/Post-Fault Context Capture:**
  - Editable local JSON DTC-to-PID rule profiles (including reference `P0131` O2 B1S1 profile for 2003 Daewoo Kalos K-Line) and DataStore preferences (`T_pre` `15–300 s`, `T_post` `10–120 s`, optional GPS context).
  - Rolling in-memory ring buffer + post-event window running inside a `connectedDevice` `ForegroundService` with persistent notification (*Armed* / *Triggered*, *Mark Event*, *Stop*).
  - Automatic triggers (new confirmed/pending DTC, MIL bit, sustained PID threshold) + manual *Mark Event* trigger, with optional best-effort GPS coordinates (`LocationManager`).
  - Local JSON persistence and self-contained **JSON + tabular CSV export** via `FileProvider` Share Sheet and one-tap **Copy JSON for AI**.
- **Jetpack Compose Material 3 UI:**
  - 4-tab `NavigationBar` (`Connect`, `Live`, `Recorder`, `Bench`), global transport / `V_BAT` status banner, profile editor / capture detail / AT console dialogs, and an **About** dialog with build timestamp and copyable diagnostic logs.

---

## 2. Interface Contract & Sibling Repositories

- **Hardware PCB:** [`nitrogene/OBD2`](https://github.com/nitrogene/OBD2)
- **ESP32-S3 Firmware:** [`nitrogene/OBD2-firmware`](https://github.com/nitrogene/OBD2-firmware)
- **Firmware-to-Android Contract:** [`OBD2-firmware/android.md`](https://github.com/nitrogene/OBD2-firmware/blob/master/android.md)

---

## 3. Architecture, Build & Test Workflows

- **Modules:**
  - `:core:obd` — Pure Kotlin/JVM module containing `ElmTransport` / `TransportManager`, `ElmPromptFramer`, `ElmInitializer`, CAN/ISO-TP/K-Line parsers & decoders, `PollingCadenceGuard`, `DiagnosticSecurityGate`, `FaultContextRecorder`, and `CaptureExporter`.
  - `:app` — Android application (`minSdk 26`, `targetSdk 35`) with Jetpack Compose Material 3, Hilt + KSP, Coroutines/`Flow`, `kotlinx.serialization`, Jetpack DataStore, native BLE/Wi-Fi transports, and `RecorderForegroundService`.
- **Build & Verification Commands:**
  - Unit tests (pure-Kotlin `:core:obd` + `:app` JVM tests): `./gradlew test`
  - Android Lint: `./gradlew lint`
  - Debug APK: `./gradlew assembleDebug` (outputs `app/build/outputs/apk/debug/app-debug.apk`)
- **Governance & Reviews:** [`AGENTS.md`](AGENTS.md), [`todo.md`](todo.md), [`CHANGELOG.md`](CHANGELOG.md), and [`review/guidelines.md`](review/guidelines.md)
