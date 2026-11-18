# OBD2-android

Companion Android diagnostic application for [`nitrogene/OBD2-firmware`](https://github.com/nitrogene/OBD2-firmware) (ESP32-S3 OBD-II Scanner & ECU Simulator).

---

## 1. Purpose & Key Capabilities

- **Dual Wireless Transport:**
  - **BLE 5.0 GATT (`OBD2-ELM327`):** Service `0xFFF0` (`0xFFF1` Notify / `0xFFF2` Write) and alias `0xFFE0` (`0xFFE1`), ATT MTU negotiation (`>= 247` bytes), ~8.1 req/s.
  - **Wi-Fi SoftAP / TCP (`OBD2-Scanner`):** Static IP `192.168.42.1:35000` (`TCP_NODELAY`), bound to the Wi-Fi `Network` via `ConnectivityManager` (no cellular routing conflict), ~136.9 req/s.
- **ELM327 v1.5 & KWP2000 Client:** Full initialization, live PID polling, DTC read/clear (`Mode 03`/`04`/`07`), Freeze Frame (`Mode 02`), VIN (`0902`), extended mode (`AT+EXT`), and `VIRTUAL_BENCH` simulator control (`AT+SIM:...`).
- **Configurable Pre/Post-Fault Context Capture:**
  - Editable DTC-to-PID rule profiles (including reference `P0131` O2 sensor profile for 2003 Daewoo Kalos K-Line).
  - Rolling in-memory ring buffer (`T_pre`, e.g. `60 s`) + post-event window (`T_post`, e.g. `30 s`) triggered by new DTCs, custom thresholds, or manual event marking.
  - Self-contained **JSON / CSV export** ready for AI assistant or mechanic analysis.

---

## 2. Interface Contract & Sibling Repositories

- **Hardware PCB:** [`nitrogene/OBD2`](https://github.com/nitrogene/OBD2)
- **ESP32-S3 Firmware:** [`nitrogene/OBD2-firmware`](https://github.com/nitrogene/OBD2-firmware)
- **Firmware-to-Android Contract:** [`OBD2-firmware/android.md`](https://github.com/nitrogene/OBD2-firmware/blob/master/android.md)

---

## 3. Planned Tech Stack & Repository Layout

- **Language & UI:** Kotlin, Jetpack Compose (Material 3), Coroutines / `Flow`
- **Build System:** Gradle (Kotlin DSL), Android SDK (`minSdk 26`, `targetSdk 35`)
- **Testing:** JUnit5 / MockK unit tests (protocol framing, PID decoders, ring buffer state machine) + `VIRTUAL_BENCH` (LilyGO T-Display S3) integration tests
- **Governance & Reviews:** [`AGENTS.md`](AGENTS.md), [`todo.md`](todo.md), [`CHANGELOG.md`](CHANGELOG.md), and [`review/guidelines.md`](review/guidelines.md)
