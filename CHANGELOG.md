# Changelog

All notable changes to `OBD2-android` are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.3.0] - 2026-11-19

### Added
- Global **Disconnect** (`LinkOff`) action in the top status banner that stops live polling, BLE scanning, and `RecorderForegroundService`.
- Wi-Fi SoftAP diagnostic logging (`AppDiagLog`) and `WifiNetworkSpecifier` fallback (Android 10+) + `WifiManager.isWifiEnabled` check in `WifiNetworkBinder`.

### Changed
- `ConnectScreen` title now reflects `Connect` / `Connected` state.

### Fixed
- Excluded `TRANSPORT_VPN` (e.g. Tailscale `tun0`) and `NET_CAPABILITY_INTERNET` when binding to the `OBD2-Scanner` SoftAP `Network` in `WifiNetworkBinder`.
- Used `socketFactory.createSocket(host, port)` in `WifiTcpElmTransport` for reliable bound TCP connections on Android 14.

## [0.2.0] - 2026-11-18

### Added
- Implemented configurable pre/post-fault context capture (`FaultContextRecorder`, `RecorderModels` with `P0131` O2 B1S1 profile, `CaptureExporter` JSON/CSV) in `:core:obd`, plus local JSON/DataStore repositories, `LocationContextProvider` GPS context, `FileProvider` share/copy helper, and `RecorderForegroundService` (`connectedDevice`) in `:app`.
- Built Jetpack Compose Material 3 UI (`ConnectScreen`, `LiveDashboardScreen`, `RecorderScreen`, `BenchScreen`, `ProfileEditorDialog`, `CaptureDetailDialog`, `AtConsoleDialog`, global transport/VBAT status banner, and `ObdViewModel`).
- Added About dialog (`BuildConfig.BUILD_TIME`) and in-app diagnostic log (`AppDiagLog` with Copy/Share Logs) for field BLE/ELM327 debugging.

### Fixed
- Fixed Android BLE scan filtering (software filter for `0xFFF0`/`0xFFE0` or `OBD`/`ELM` name + optional All BLE toggle) and `ConnectScreen` segmented BLE/Wi-Fi layout.
- Fixed `BleElmTransport` race between NimBLE's connect-time `onMtuChanged` and `discoverServices()` (`servicesDiscovered` guard + GATT cache refresh).

## [0.1.0] - 2026-11-18

### Added
- Initialized repository documentation (`README.md`, `AGENTS.md`, `todo.md`, `CHANGELOG.md`), `review/` audit gateway (`review/guidelines.md` + `.agents/skills/review/`), and `.gitignore`.
- Bootstrapped multi-module Gradle (Kotlin DSL) project (`:core:obd` pure Kotlin/JVM + `:app` Android with Jetpack Compose Material 3, Hilt/KSP, `kotlinx.serialization`, Jetpack DataStore, Gradle Wrapper 8.10.2) and GitHub Actions CI (`.github/workflows/ci.yml`).
- Implemented unified `ElmTransport` / `TransportManager` / `ElmPromptFramer` / `ElmCommandExchange` in `:core:obd`, native BLE 5.0 GATT client (`OBD2-ELM327`, `0xFFF0`/`0xFFE0`, `requestMtu(256)`), and native Wi-Fi SoftAP TCP client (`192.168.42.1:35000` with `ConnectivityManager` Wi-Fi `Network` binding) in `:app`.
- Implemented ELM327 v1.5 initialization (`ElmInitializer`), CAN/ISO-TP/K-Line response parser (`ObdResponseParser`), `Mode 01`/`02`/`03`/`07`/`09` + `ATRV` decoders (`ObdDecoders`), K-Line `<= 8` PIDs cadence guard (`PollingCadenceGuard`), and `AT+EXT`/SID security gate (`DiagnosticSecurityGate`).
