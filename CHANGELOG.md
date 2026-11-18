# Changelog

All notable changes to `OBD2-android` are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Implemented configurable pre/post-fault context capture (`FaultContextRecorder`, `RecorderModels` with `P0131` O2 B1S1 profile, `CaptureExporter` JSON/CSV) in `:core:obd`, plus local JSON/DataStore repositories, `LocationContextProvider` GPS context, `FileProvider` share/copy helper, and `RecorderForegroundService` (`connectedDevice`) in `:app`.
- Built Jetpack Compose Material 3 UI (`ConnectScreen`, `LiveDashboardScreen`, `RecorderScreen`, `BenchScreen`, `ProfileEditorDialog`, `CaptureDetailDialog`, `AtConsoleDialog`, global transport/VBAT status banner, and `ObdViewModel`).

## [0.1.0] - 2026-11-18

### Added
- Initialized repository documentation (`README.md`, `AGENTS.md`, `todo.md`, `CHANGELOG.md`), `review/` audit gateway (`review/guidelines.md` + `.agents/skills/review/`), and `.gitignore`.
- Bootstrapped multi-module Gradle (Kotlin DSL) project (`:core:obd` pure Kotlin/JVM + `:app` Android with Jetpack Compose Material 3, Hilt/KSP, `kotlinx.serialization`, Jetpack DataStore, Gradle Wrapper 8.10.2) and GitHub Actions CI (`.github/workflows/ci.yml`).
- Implemented unified `ElmTransport` / `TransportManager` / `ElmPromptFramer` / `ElmCommandExchange` in `:core:obd`, native BLE 5.0 GATT client (`OBD2-ELM327`, `0xFFF0`/`0xFFE0`, `requestMtu(256)`), and native Wi-Fi SoftAP TCP client (`192.168.42.1:35000` with `ConnectivityManager` Wi-Fi `Network` binding) in `:app`.
- Implemented ELM327 v1.5 initialization (`ElmInitializer`), CAN/ISO-TP/K-Line response parser (`ObdResponseParser`), `Mode 01`/`02`/`03`/`07`/`09` + `ATRV` decoders (`ObdDecoders`), K-Line `<= 8` PIDs cadence guard (`PollingCadenceGuard`), and `AT+EXT`/SID security gate (`DiagnosticSecurityGate`).
