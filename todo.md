# Active Backlog (`todo.md`)

> [!NOTE]
> Per [`AGENTS.md`](AGENTS.md) Section 8, `todo.md` tracks **only open work** (`- [ ]`). Completed milestones are recorded in [`CHANGELOG.md`](CHANGELOG.md), and the current functional contract is documented in [`README.md`](README.md).
> Firmware interface contract is defined in [`OBD2-firmware/android.md`](https://github.com/nitrogene/OBD2-firmware/blob/master/android.md).

---

## Phase 1 — Android Project Bootstrap & CI
- [x] Initialize multi-module Gradle (Kotlin DSL) project: `:core:obd` (pure Kotlin/JVM) + `:app` (Android `minSdk 26`, `targetSdk 35`, Jetpack Compose Material 3, Coroutines/`Flow`, Hilt with KSP, `kotlinx.serialization`, Jetpack DataStore).
- [x] Set up GitHub Actions CI (`.github/workflows/ci.yml`) running `./gradlew test` and `./gradlew lint`.

## Phase 2 — Transport Layer (`BLE` & `Wi-Fi TCP`)
- [x] Implement unified `ElmTransport` interface (in `:core:obd`) with clean connect/disconnect lifecycle, single-active-transport discipline, and `>` prompt framing.
- [x] Implement native Android BLE 5.0 GATT client (`BluetoothGatt` wrapped in Coroutines/`Flow`, `OBD2-ELM327`, `0xFFF0`/`0xFFE0` scan filter, runtime permissions, `requestMtu(256)`, `0xFFF1`/`0xFFE1` notifications).
- [x] Implement native Wi-Fi TCP client (`java.net.Socket` at `192.168.42.1:35000`, `TCP_NODELAY`, `ConnectivityManager` / `network.socketFactory` Wi-Fi `Network` binding).
- [x] Add transport unit tests (framing across split BLE notifications, prompt timeouts, clean reconnect).

## Phase 3 — ELM327 v1.5, OBD-II & KWP2000 Stack
- [x] Implement ELM327 v1.5 initialization sequence (`ATZ`, `ATE0`, `ATD0`, `ATH1`, `ATSP0`, `ATM0`, `ATS0`, `ATAT1`, `ATAL`, `ATST64`, `0100`, `ATRV`).
- [x] Implement CAN single-frame (`7E8064100...`) and ISO-TP multi-frame (`014\r0:...`) response parser + `Mode 01`/`02`/`03`/`04`/`07`/`09` decoders.
- [x] Add K-Line / KWP2000 polling cadence guard (`<= 8` PIDs for `10,400 baud` targets like 2003 Daewoo Kalos).

## Phase 4 — Configurable Pre/Post-Fault Context Capture
- [x] Implement editable local JSON DTC-to-PID rule profiles via `kotlinx.serialization` (including reference `P0131` O2 B1S1 profile) and Jetpack DataStore for recorder preferences.
- [x] Implement rolling in-memory ring buffer (`T_pre` configurable, e.g. `15–300 s`) + post-fault capture window (`T_post`, e.g. `10–120 s`) in `:core:obd`, persisted as local JSON files on completion.
- [x] Implement `ForegroundService` (`connectedDevice` type) with persistent notification (*Armed* / *Triggered* state + *Mark Event* / *Stop* actions) to keep polling and recording alive in background / screen-off.
- [x] Implement automatic triggers (new confirmed/pending DTC, MIL bit, PID threshold) + manual "Capture Now / Mark Event" trigger.
- [x] Implement self-contained JSON + tabular CSV export via `FileProvider` (Android Share Sheet + one-tap "Copy JSON for AI") including metadata, VIN, DTCs, `Mode 02` Freeze Frame, and `t_rel_ms` time-series.
- [x] Capture optional GPS coordinates (best-effort location context, e.g. lat/lon/altitude/GPS speed) alongside PID samples / fault triggers to enrich incident diagnostics.

## Phase 5 — UI & `VIRTUAL_BENCH` / Vehicle Validation
- [ ] Build Jetpack Compose Material 3 UI with 4-tab `NavigationBar` (`Connect`, `Live`, `Recorder`, `Bench`), modal/sub-screens (`ProfileEditor`, `CaptureDetail`, `AT Console`), and global transport/VBAT status banner.
- [ ] Validate end-to-end against LilyGO `VIRTUAL_BENCH` (`GPIO 0`/`GPIO 14` & `AT+SIM:DTC=P0131`) and live 2003 Daewoo Kalos K-Line.
