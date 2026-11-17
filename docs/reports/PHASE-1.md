# Ayah — Phase 1 Report (Foundation)

Date: 2026-11-17 · Status: **complete, build green**

## What was implemented

1. **Complete Android project from zero**, including the development
   environment itself: Android SDK (cmdline-tools, platform 36, build-tools
   36, platform-tools, emulator) installed from scratch; Gradle 8.13
   wrapper generated and seeded.
2. **Gradle foundation:** version catalog (`gradle/libs.versions.toml`),
   root/app build scripts (Kotlin DSL), `debug`/`release` build types with
   R8 + resource shrinking enabled from day one, Room schema export.
3. **Clean-architecture skeleton** in a single `:app` module with strict
   package boundaries (`core/`, `data/`, `domain/`, `ui/`, `di/`,
   `navigation/`) — see docs/ARCHITECTURE.md for the module-extraction path.
4. **Room database** `ayah.db` v1: `surahs`, `ayahs` (unique composite
   reference, global verse id, FK), `bookmarks` (unique verse reference, FK,
   cascade), `reading_progress` (single-row upsert); all DAOs compiled with
   Flow-based APIs. Schema exported to `app/schemas/1.json`. The tables are
   empty by design — data import is Phase 2 and will come exclusively from a
   verified dataset (docs/QURAN_DATA.md).
5. **DataStore preferences** + the first functional preference end-to-end:
   appearance (System/Light/Dark) persisted via
   `UserPreferencesRepository` (interface in domain, DataStore impl in data)
   through use cases, applied app-wide from `MainViewModel`.
6. **Design system:** brand palette (Emerald/Gold/Cream/Ink) as light+dark
   Material 3 schemes (dynamic color deliberately off), 4-pt spacing scale,
   Amiri typeface bundled (SIL OFL 1.1) exposed as `QuranTextFontFamily`,
   shared `AyahCard`/`SectionHeader`/`EmptyState` components.
7. **Navigation & all 5 sections:** Home, Quran, Wallpapers, Audio,
   Settings in a bottom-nav scaffold with state-preserving top-level
   navigation. Splash screen (core-splashscreen), edge-to-edge, adaptive
   launcher icon (8-point Islamic star vector + monochrome layer).
8. **Honest empty states:** every not-yet-implemented feature is visibly
   labeled as pending (rule: no incomplete feature disguised as working) —
   notably no placeholder Quranic text anywhere.
9. **Unit tests:** 7 green tests (ThemeMode storage round-trip/degradation,
   navigation contract, Settings ViewModel→use case→repository chain with
   MockK/Turbine/coroutines-test).

## Files created

Project root: `settings.gradle.kts`, `build.gradle.kts`,
`gradle.properties`, `gradle/libs.versions.toml`, `.gitignore`,
`local.properties` (machine-specific), wrapper (`gradlew`,
`gradlew.bat`, `gradle/wrapper/*`).

App module (`app/`): build.gradle.kts, proguard-rules.pro,
AndroidManifest.xml, `res/` (strings, themes, colors, adaptive launcher
icon, splash icon, Amiri fonts), 30 Kotlin sources under
`com.ayah.quran` (domain 7, data 1, database 9, di 3, designsystem 6,
navigation 2, ui 7 incl. ViewModels, root 3), 3 JVM test classes.

Docs: `docs/ARCHITECTURE.md`, `docs/QURAN_DATA.md`, `docs/LICENSES.md`,
this report.

## Build & test results

| Check | Result |
|---|---|
| `gradlew assembleDebug` | ✅ 66 MB APK (unminified debug) |
| `gradlew assembleRelease` | ✅ 2.7 MB APK (R8 + shrinkResources), unsigned |
| `gradlew testDebugUnitTest` | ✅ 7 tests, 0 failures, 0 skipped |
| Room schema export | ✅ `app/schemas/com.ayah.quran.core.database.AyahDatabase/1.json` |
| Emulator run (API 36, Pixel 6 AVD, WHPX) | ✅ install → launch → all checks below |

**On-device verification** (headless emulator, android-36 AOSP x86_64):

- APK installs (`Success`) and `MainActivity` reaches `topResumedActivity`;
  zero fatal exceptions in logcat.
- Home renders: Amiri wordmark, tagline, cream palette, honest empty-state
  cards, bottom navigation (screenshots in `docs/reports/screenshots/`).
- Settings: version shows `0.1.0-debug`; theme radio switches Light → Dark
  live across the whole UI.
- Theme persistence: after `am force-stop` + relaunch the app reopens in
  Dark — the DataStore → use case → ViewModel → theme chain survives
  process death.
- `ayah.db` not yet created on device: correct — Room opens the file on
  first DAO access, and Phase 1 has no query path yet. Phase 2's import
  will create it.

## Environment & compatibility findings (important)

- **Hilt/Kotlin/AGP knot (verified, not guessed):** Hilt 2.59+ refuses
  AGP < 9; Hilt 2.58's processor caps Kotlin metadata at 2.3.0, pinning
  Kotlin to 2.3.21. Compose UI 1.12+/Lifecycle 2.11/Navigation 2.10.2
  require compileSdk 37 + AGP 9.1. The project therefore pins the last
  fully-compatible generation: AGP 8.13.2 / Kotlin 2.3.21 / Hilt 2.58 /
  BOM 2026.06.00 / Lifecycle 2.10.0 / Navigation 2.9.8 — all verified by
  green builds after bisecting failures. AGP 9 migration documented in
  ARCHITECTURE.md as a dedicated future phase.
- **Environment note:** this machine's Java cannot reach github.com
  (Gradle distribution host); the wrapper cache was seeded from a curl
  download. Documented here so a future `./gradlew` failure is understood.

## Known issues / limitations

1. **No Quran content yet** — by design. Quran/Wallpaper/Home data screens
   show explicit pending states; the dataset import (with integrity tests)
   is the first task of Phase 2.
2. **`androidTest` (instrumented Compose tests) not written yet** — the
   emulator now exists and stays available for Phase 2, where Room DAO
   instrumented tests and UI tests accompany the data import.
3. **Wallpaper screen controls disabled** — visual layout only; engine
   arrives in Phase 3 (per roadmap).
4. **Audio** is a designed "coming soon" screen (per spec).
5. **Release APK is unsigned** — signing config is a release-preparation
   task (Phase 6).
6. **Development-environment quirk:** Java processes on this machine cannot
   reach github.com (curl can); the Gradle wrapper cache was seeded
   manually. If `./gradlew` ever tries to re-download the distribution and
   hangs, this is why.

## Next phase (Phase 2 — Quran Engine)

1. Obtain verified Uthmani dataset (Tanzil.net primary candidate),
   record checksum + license in docs/QURAN_DATA.md.
2. First-launch importer into Room (surahs + 6236 ayat with juz/page).
3. Integrity test suite: totals (114/6236/30/604), per-surah counts,
   id continuity, referential integrity, non-empty text, spot checks
   against KFGQPC reference for known verses.
4. Surah list + reader UI with Amiri/Quranic typography, bookmarks,
   reading position, search-by-surah-name.
