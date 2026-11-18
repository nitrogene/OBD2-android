# Changelog

All notable changes to `OBD2-android` are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Initialized repository documentation (`README.md`, `AGENTS.md`, `todo.md`, `CHANGELOG.md`), `review/` audit gateway (`review/guidelines.md` + `.agents/skills/review/`), and `.gitignore`.
- Bootstrapped multi-module Gradle (Kotlin DSL) project (`:core:obd` pure Kotlin/JVM + `:app` Android with Jetpack Compose Material 3, Hilt/KSP, `kotlinx.serialization`, Jetpack DataStore, Gradle Wrapper 8.10.2) and GitHub Actions CI (`.github/workflows/ci.yml`).
