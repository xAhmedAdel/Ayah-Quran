# Contributing to Ayah

Thank you for your interest in contributing to **Ayah (آية)**. This document outlines the technical standards, data integrity rules, and development workflow required for all contributions.

---

## 1. Core Non-Negotiables

Every contribution to Ayah must uphold the following engineering and domain guarantees:

1. **Quranic Text Integrity**:
   - The bundled Uthmani Quran asset (`app/src/main/assets/quran/quran-uthmani.txt`, sourced from Tanzil v1.1) and page/Juz metadata (`surah-metadata.json`, `page-boundaries.json`, `juz-boundaries.json`) are sacred reference data.
   - Never alter, re-normalize, or edit Quranic verse strings, diacritics, or SHA-256 verification checksums.
2. **Offline-First Operation**:
   - Core reading, search, bookmarking, daily reflection, prayer time calculations, bundled Adhan playback, Adhkar, Tasbih, Duas, Hadith FTS4 search, and wallpaper generation must remain 100% functional without network connectivity.
   - Network operations are strictly limited to optional Surah audio streaming/downloading and user-initiated GitHub release update checks.
3. **Arabic-First RTL & Theme Parity**:
   - All screens must render natively in Right-to-Left (`LayoutDirection.Rtl`) Arabic layout with full support for both the Warm Ivory Light palette (`AyahLightColors`) and Deep Obsidian Dark palette (`AyahDarkColors`).
   - Interactive controls must meet minimum touch target accessibility sizing (`48dp`).
4. **Zero Tracking & Privacy Respect**:
   - Do not introduce analytics SDKs, advertising identifiers, crash telemetry uploads, or unrequested background network calls.
5. **Database Migration Safety**:
   - Never enable `fallbackToDestructiveMigration` on `AyahDatabase`.
   - Any schema change requires an explicit Room `Migration` in `AyahMigrations`, an exported Room schema JSON under `app/schemas/`, and a corresponding migration test in `AyahDatabaseMigrationTest`.

---

## 2. Reporting Bugs

When opening a bug report on [GitHub Issues](https://github.com/xAhmedAdel/Ayah-Quran/issues), please include:

- **App Version**: Version name and version code (shown at the bottom of the Settings screen, e.g., `1.0.0-beta.12`).
- **Device & Android Version**: Manufacturer, model, and Android OS version (e.g., Android 14 / API 34).
- **Reproduction Steps**: Clear, numbered steps to reproduce the issue.
- **Expected vs. Actual Behavior**: What should happen versus what actually occurred.
- **Screenshots or Logs**: Relevant screenshots or `adb logcat` excerpts if available.

---

## 3. Suggesting Enhancements

Feature proposals should align with Ayah's calm, distraction-free Islamic experience:

- Open an issue describing the user problem and proposed workflow.
- Indicate whether the feature operates offline and how it interacts with existing Room tables, DataStore preferences, or background workers.
- Keep scope focused; avoid feature bloat or social/gamified mechanics that detract from reflection and worship.

---

## 4. Development Setup & Verification

### Prerequisites

- **JDK 17**
- **Android SDK 35** (`minSdk = 26`, `targetSdk = 35`)
- **Android Studio** (Ladybug or newer recommended)

### Required Pre-PR Checks

Before submitting a Pull Request, run the verification suite from the project root:

```bash
# 1. Build the debug APK
./gradlew assembleDebug

# 2. Run the full JVM unit test suite (264 tests across 49 test classes)
./gradlew testDebugUnitTest

# 3. Check for formatting or whitespace errors
git diff --check
```

If your changes touch Room migrations, seeding, Compose RTL layouts, or wallpaper rendering, also run the instrumented test suite on an Android emulator or connected device:

```bash
./gradlew connectedDebugAndroidTest
```

---

## 5. Pull Request Guidelines

- **Focused Scope**: Keep each Pull Request scoped to a single logical fix or feature slice.
- **Clean Architecture Boundaries**:
  - `domain/` must remain pure Kotlin with zero Android framework dependencies (except where explicitly bridged by interfaces).
  - `ui/` composables and ViewModels must observe reactive `StateFlow`/`Flow` state and delegate side effects through domain/data repositories.
  - All long-running disk or network work must execute on injected coroutine dispatchers (`@IoDispatcher`), never on the main thread.
- **Tests Required**: Bug fixes and new repository/domain logic must include deterministic unit tests.
- **Attribution & Licensing**: Any new bundled font, audio recording, or dataset must carry a verified open license and be documented in [docs/LICENSES.md](docs/LICENSES.md).
