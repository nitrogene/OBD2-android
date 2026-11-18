# Project Rules: OBD2-android Companion Application

## 1. Firmware Contract & Single Source of Truth
- **Authoritative Interface Spec:** All BLE UUIDs, Wi-Fi SoftAP IP/port (`192.168.42.1:35000`), ELM327 framing, `AT+EXT` gating, and `AT+SIM` bench commands are defined in [`nitrogene/OBD2-firmware/android.md`](https://github.com/nitrogene/OBD2-firmware/blob/master/android.md).
- **No Invented Protocol Behavior:** Never guess GATT UUIDs, SoftAP credentials, CAN PCI framing, or KWP2000 SIDs. Cross-check against `OBD2-firmware/android.md` and `OBD2-firmware/README.md`.

## 2. Build, Tooling & Code Modularity
- **Stack:** Kotlin, Jetpack Compose (Material 3), Coroutines / `Flow`, Gradle (Kotlin DSL).
- **Python Tooling via `uv`:** Any helper or review script (such as `.agents/skills/review/scripts/review_tool.py`) must be executed via `uv run`.
- **Modular Architecture:** Keep protocol framing (`elm327`), transport layers (`ble`, `wifi`), OBD-II/KWP2000 codecs, fault-context ring buffer (`recorder`), and Jetpack Compose UI cleanly separated so pure-Kotlin protocol and recorder logic runs in JVM unit tests without an Android device.

## 3. Android Safety, Permissions & Transport Rules
- **Network Binding on Wi-Fi SoftAP:** Because `OBD2-Scanner` has no internet access, bind TCP/HTTP sockets to the Wi-Fi `Network` (`ConnectivityManager.bindProcessToNetwork()` or `network.socketFactory`) so Android does not route `192.168.42.1` over cellular data.
- **BLE Permissions & MTU:** Handle `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` (API 31+) and `ACCESS_FINE_LOCATION` (API <= 30) gracefully; request ATT MTU `>= 247` (`requestMtu(256)`) and enable `0xFFF1` (or `0xFFE1`) CCCD notifications before writing commands.
- **Single-Active-Transport Discipline:** Release/close the active BLE GATT connection or TCP socket cleanly when disconnecting or switching transports (`transport_stream` holds a single-transport lock with a `5000 ms` inactivity timeout).
- **Diagnostic Security Gating:** Never unlock `AT+EXT` or issue state-modifying SIDs (`0x11`, `0x27`, `0x28`, `0x2E`–`0x3D`, `0x85`) automatically without explicit user confirmation.
- **In-Vehicle Safety:** Never encourage driver interaction while moving; keep fault-context recording automatic once armed.

## 4. Mandatory Pre-Completion & Verification Gates
- **JVM Unit Tests & Lint:** Run `./gradlew test` and `./gradlew lint` (or `ktlint` / `detekt` if configured) before marking any code task `[x]` in `todo.md`.
- **Bench Validation (`VIRTUAL_BENCH`):** Validate BLE/Wi-Fi transport, ELM327 init (`ATZ`..`0100`), and pre/post-fault capture triggers (`GPIO 14` / `AT+SIM:DTC=...`) against the LilyGO T-Display S3 before claiming end-to-end compatibility.
- **Autonomy Limits:** Maximum 3 consecutive attempts to fix a recurring Gradle, dependency, or protocol failure. If blocked, stop, summarize the root cause, and yield control to the user.

## 5. Technical Review Gateway (`review/`)
- Scan `review/review*.md`, validate format with `uv run .agents/skills/review/scripts/review_tool.py validate`, sort findings by criticality (Blocking / Important / Minor) per `review/guidelines.md`, arbitrate with the user, integrate validated items into `todo.md` via `uv run .agents/skills/review/scripts/review_tool.py triage --apply --delete-review`, and delete the processed review file.

## 6. Post-Validation & Commit Policy
- Commit and push **only** after explicit textual approval from the user on the commit message (or when explicitly asked by the user to open a Pull Request on a feature branch).

## 7. Documentation Language & Formatting
- **English-Only Documentation:** All project documentation (`README.md`, `todo.md`, `CHANGELOG.md`, `AGENTS.md`, `review/`, `.github/`, `.agents/skills/`, as well as code comments and KDoc) must be written exclusively in English.

## 8. Documentation Governance (`README.md` vs `todo.md` vs `CHANGELOG.md`)
Each root documentation file has a single, non-overlapping role:
- **`README.md` (Current Functional Contract):** High-level description of what the app **currently does** (architecture, supported transports, features, build & test workflows). Must stay free of implementation minutiae and never claim unimplemented behavior.
- **`todo.md` (Active Backlog Only):** Open action items (`- [ ]`) and pending bench/vehicle validations. Prune completed items (`- [x]`) regularly once reflected in `CHANGELOG.md` (and `README.md` if applicable).
- **`CHANGELOG.md` (Historical Record):** Versioned log of all notable changes following Keep a Changelog / SemVer.

### Routing Rule When Completing a `todo.md` Item
Completing a task in `todo.md` updates `CHANGELOG.md` (when user-visible or release-relevant), and updates `README.md` **only if** it changes the external contract (user-facing features, supported protocols, permissions, or build/test workflows) — not for internal bug fixes, refactors, or hardware validation of already-documented features.
