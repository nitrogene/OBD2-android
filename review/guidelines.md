# Reviewer Guidelines — OBD2-android Companion Application

Welcome! This document outlines instructions, technical requirements, and reporting standards for any **reviewer** (AI agent or human engineer) auditing the **OBD2-android** project.

The goal of a technical review is to provide rigorous, evidence-based critical scrutiny to ensure reliability, safety, maintainability, and adherence to project requirements.

---

## 1. Format & Naming Conventions

### File Naming
Submit your review inside the `review/` directory using the following format:
`review/reviewXXX.md` *(e.g., `review001.md`, `review_architecture_01.md`)*.

> [!NOTE]
> **Lifecycle:** Once your review is placed in `review/`, the project team (user and AI assistant) will evaluate your observations, arbitrate each item, integrate accepted action points into `todo.md`, and then **delete** your `reviewXXX.md` file. Deletion signifies full resolution and keeps the intake clean for subsequent reviews.

### Mandatory Metadata Header
Every review **must begin** with this metadata block to guarantee full audit traceability:

```markdown
# Technical Review: [Short and descriptive title]

- **Reviewer / Agent Name:** [E.g. Claude, Gemini, Domain Expert, etc.]
- **Date:** [YYYY-MM-DD]
- **Audited Scope:** [E.g. Module / Subsystem / Architecture / PR / Release]
- **Examined Reference / Version:** [Git commit hash, tag, or document version]
- **Overall Verdict:** [🟢 Approved / 🟠 Approved with Reservations / 🔴 Rejected (Critical fixes required)]
```

---

## 2. Project Context, Audit Axes & Pitfalls to Avoid (Domain-Specific)

### 2.0 Universal Auditor Mindset: Tabula Rasa & Evidence-Based Scrutiny
- Disregard any prior conversational memory or unverified assumptions. Adopt the stance of an impartial third-party auditor discovering the project fresh.
- Ground every observation strictly in versioned repository artifacts and the authoritative firmware contract (`OBD2-firmware/android.md`).
- Cross-check documentation claims against actual implementation before raising an alert.

### 2.1 Project Context & Known Constraints
- **Companion Firmware Contract:** Must strictly match [`OBD2-firmware/android.md`](https://github.com/nitrogene/OBD2-firmware/blob/master/android.md) (BLE `OBD2-ELM327` `0xFFF0`/`0xFFE0`, Wi-Fi `OBD2-Scanner` `192.168.42.1:35000`, `transport_stream` `5000 ms` lock, ELM327 v1.5 framing, `AT+EXT`, `AT+SIM`).
- **Target Vehicles:** 12V passenger vehicles including `10,400 baud` K-Line vehicles (e.g., 2003 Daewoo Kalos, `~5–10` PID req/s bandwidth).
- **Current Progress State:** Check `todo.md`, `README.md`, and `AGENTS.md` before raising findings to distinguish intentional roadmap items from regressions or unfulfilled claims.

### 2.2 Domain-Specific Audit Axes
- **Axis 1 — Wireless Transports & Android Lifecycle:**
  - Verify Android 12+ (`BLUETOOTH_SCAN`/`CONNECT`) and <= 11 (`ACCESS_FINE_LOCATION`) permission handling, ATT MTU negotiation, CCCD notification subscription before writes, and clean GATT/socket teardown on lifecycle/background transitions.
  - Verify Wi-Fi TCP/HTTP sockets are bound to the Wi-Fi `Network` (`ConnectivityManager`) so cellular data does not hijack `192.168.42.1`.
- **Axis 2 — Protocol Framing, Concurrency & Fault-Context Recorder:**
  - Verify ELM327 response accumulation across fragmented BLE notifications until `>` without dropping or corrupting frames.
  - Verify thread-safe ring buffer (`T_pre` / `T_post`) and atomic/transactional persistence of captured fault incidents (no data loss on disconnect or app backgrounding).
- **Axis 3 — Security & Safety:**
  - Verify `AT+EXT` / state-modifying SIDs are never sent automatically without explicit user confirmation, and no plaintext credentials are logged.
- **Axis 4 — Maintainability, Testing & CI:**
  - Verify pure-Kotlin protocol and recorder logic is decoupled from Android framework classes and covered by JVM unit tests (`./gradlew test`, `./gradlew lint`).

### 2.3 Known Design Decisions (Do Not Flag)
- **Single Active Transport:** Only one transport (`BLE` or `Wi-Fi`) may talk to the dongle at a time due to `transport_stream` arbitration on the ESP32-S3.
- **In-App Fault Context Intelligence:** DTC-to-PID rule profiles and `T_pre`/`T_post` ring buffering live in the Android app so rules can evolve without reflashing the ESP32-S3 firmware.

---

## 3. Mandatory Criticality Classification

Observations must be strictly categorized into 3 severity levels:

### 🔴 1. Blocking / Critical (*High / Critical*)
*Issues that prevent operation, break core invariants, or introduce severe safety/security/data-loss risks.*

### 🟠 2. Important (*Medium*)
*Issues that compromise robustness, reliability, edge-case handling, performance, or compliance.*

### 🟢 3. Minor / Suggestion (*Low / Info*)
*Opportunities to improve clarity, ergonomics, testability, or maintainability.*

---

## 4. Expectations for High-Quality Feedback

For observations to be directly actionable, each remark must provide:

1. **Pinpoint Precision:** Cite exact file paths, line ranges, symbols, or modules.
2. **Technical Demonstration (Why?):** Support analysis with concrete evidence, traces, specs, or standards.
3. **Turnkey Actionable Solution (How to fix?):** Propose a clear, concrete remediation step without speculative placeholders.
