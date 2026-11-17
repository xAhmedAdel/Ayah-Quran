# Ayah — Architecture

Status: Phase 1 (Foundation) — updated at the end of each phase.

## Toolchain & versions

| Component | Version | Notes |
|---|---|---|
| Kotlin | 2.3.21 | K2; Compose compiler ships as `org.jetbrains.kotlin.plugin.compose` |
| AGP | 8.13.2 | Mature final 8.x line |
| Gradle | 8.13 | Required by AGP 8.13; runs on JDK 21 |
| JDK | 21 (Temurin) | Compiles to Java 17 bytecode (`jvmTarget=17`) |
| compileSdk / targetSdk | 36 | Android 16 |
| minSdk | 29 | Android 10 (per product spec) |

Dependency pins (see `gradle/libs.versions.toml`) — all verified by a green
build, not assumed:

- Hilt **2.58** — two hard constraints collide at the newest releases:
  Hilt 2.59+ rejects AGP < 9.0 at plugin application (verified), and Hilt
  2.58's processor reads Kotlin metadata only up to 2.3.0, which is what
  pins Kotlin to 2.3.21 (Kotlin 2.4 emits metadata 2.4.0 and fails
  `hiltJavaCompileDebug` — verified). Breaking this knot requires the AGP 9
  migration; revisit it then.
- Compose BOM **2026.06.00** (ui 1.11.3, material3 1.4.0) — Compose UI
  1.12.1+ requires compileSdk 37 and AGP 9.1+ (verified via AGP dependency
  check), so the BOM is held one generation back.
- Navigation Compose **2.9.8** — 2.10.2 itself requires compileSdk 37 +
  AGP 9.1 (verified).
- Lifecycle **2.10.0** — 2.11.0 requires AGP 9.1 (verified).
- Room 2.8.5, DataStore 1.2.1, Activity Compose 1.12.4, core-ktx 1.17.0,
  androidx.hilt 1.3.0, core-splashscreen 1.2.0, coroutines 1.11.0.

**AGP 9 migration note (future):** AGP 9 replaces the standalone Kotlin
Android plugin with built-in Kotlin support and requires Gradle 9.x. When
the project migrates: AGP 9.x + Hilt 2.60+ + Kotlin 2.4+ + Compose BOM
2026.09+ + compileSdk 37, with `navigation`/`lifecycle` moved back to their
latest lines. Do this as its own phase with a full regression pass.

## Module structure decision

Phase 1 ships a **single `:app` Gradle module** with strict
clean-architecture *package* boundaries instead of a multi-module build:

```
com.ayah.quran
├── core/
│   ├── database/        Room: entities, DAOs, AyahDatabase
│   ├── designsystem/    theme (Color/Type/Theme/Spacing) + shared components
│   └── (wallpaper, notifications join in Phase 3+)
├── data/
│   └── settings/        DataStore-backed UserPreferencesRepository
├── domain/
│   ├── model/           Surah, Ayah, Bookmark, ReadingPosition, ThemeMode
│   ├── repository/      interfaces (data layer implements)
│   └── usecase/         one class per business operation
├── di/                  Hilt modules (Database, DataStore, bindings)
├── navigation/          TopLevelDestination + AyahApp scaffold
├── ui/                  feature packages (home, quran, wallpapers, audio, settings)
└── docs/                architecture & data documentation
```

**Why:** a 10-module Gradle setup adds inter-module API churn, slower
configuration, and heavier CI for zero benefit while the domain model is
still evolving. Package boundaries mirror the intended module boundaries
(`core:*`, `feature:*` …), so extraction later is mechanical:
each package above becomes a Gradle module without renaming any class.

**Layer rules (enforced by convention, moved into modules when extracted):**

1. `ui/**` never imports Room, DataStore, or `android.app.WallpaperManager`.
   UI observes state from ViewModels; ViewModels call use cases.
2. `domain/**` has no Android framework dependencies (pure Kotlin where
   possible) and defines repository interfaces.
3. `data/**` and `core/database|datastore|wallpaper/**` implement domain
   interfaces and are the only layers touching I/O frameworks.
4. Background workers (Phase 3) live outside the UI and depend only on
   domain + data.

## Design system

- Fixed brand palette (Emerald `#153D32`, Gold `#D5B56A`, Cream `#F7F2E8`,
  Ink `#111714`, text `#F5F2EA` on dark). **Dynamic color is deliberately
  disabled** — the brand must render identically on all devices.
- Typography: system family for Latin UI; **Amiri** (`res/font`, SIL OFL 1.1)
  for Arabic display text and the wordmark, exposed as
  `QuranTextFontFamily`. The dedicated Uthmani-script rendering font for the
  wallpaper engine is selected during the data phase (Phase 2/3) — Amiri is
  a UI display font, not necessarily the final mushaf font.
- Spacing scale: `AyahSpacing` (4-pt base); radii: `AyahRadii`.

## Persistence

- **Room** (`ayah.db`, schema exported to `app/schemas`): `surahs` (incl.
  Arabic/transliterated/English names, revelation type, ayah count),
  `ayahs` (composite unique `(surahId, ayahNumber)`, global verse id as PK,
  FK → surahs, page/juz indexes), `bookmarks` (unique `(surahId,
  ayahNumber)`, FK → ayahs, cascade), `reading_progress` (single-row
  upsert table).
- **Schema history:** v1 = Phase 1, v2 = `surahs.nameEnglish` added (Phase
  2). **Until first release**, `fallbackToDestructiveMigration()` is the
  documented policy — no user database from an older schema exists in the
  wild; a real migration plan starts with the first public schema (v2).
- **DataStore Preferences** (`ayah_preferences`): enum-name strings, unknown
  values degrade to defaults (`ThemeMode.fromStorage`).

## Quran data pipeline (Phase 2)

- Dataset (Tanzil Uthmani v1.1 + metadata + Madani page/juz metrics) is
  bundled in `app/src/main/assets/quran/`; provenance, checksums, license
  and the full verification methodology: docs/QURAN_DATA.md.
- `QuranTextParser` parses strictly (any anomaly → `QuranParseException`),
  cross-tying all three files: ayah sequence, per-surah counts, global ids
  from XML start indices, juz/page ranges, revelation types.
- `QuranImporter` runs at app start on an application-scoped coroutine:
  validate-then-write, one Room transaction, idempotent, post-insert count
  verification, honest `QuranImportState` (NotStarted/Importing/Completed/
  Failed) observed by the UI.
- Mushaf display conventions (bismillah header incl. the 95/97 shadda
  variant) are applied at RENDER time from dataset-derived values — the
  stored text is never modified.

## Wallpaper engine (Phase 3)

- `core/wallpaper/`: `WallpaperTemplate` (structured-data templates —
  palette, background, decorations, typography, safe-area insets) and
  `WallpaperRenderer` (StaticLayout RTL Amiri text, fit-to-content shrink
  pass — never ellipsizes Quranic text; safe area accounts for clock,
  gestures, punch-hole).
- `core/quran/AyahSelector`: pure selection algorithms (random with recent
  avoidance + full-pool fallback, sequential with persistent cursor, daily
  by epoch day). Pools come only from the verified dataset.
- `data/wallpapers/`: `WallpaperRepositoryImpl` (render → apply via
  `WallpaperManager.setBitmap` per FLAG_SYSTEM/FLAG_LOCK → record history;
  SUCCESS only after Android confirms), `WallpaperSettingsRepositoryImpl`
  (DataStore), `WallpaperSchedulingRepositoryImpl` (WorkManager unique
  periodic work, UPDATE policy, battery-not-low constraint).
- `background/WallpaperWorker`: `@HiltWorker` CoroutineWorker; on-demand
  WorkManager init via `Configuration.Provider` + manifest provider removal.
- Room schema v3: `wallpaper_history` + `selection_history` tables.
- Renderer pitfalls (documented from real bugs): never set
  `Paint.textAlign = CENTER` on a TextPaint used by an RTL StaticLayout
  (double-offset clipping); render at screen dimensions, not
  `desiredMinimum*` (launcher crops the scrollable canvas); qualify
  receiver members inside `apply{}` when same-named locals exist.

## Known Android-platform notes

- **WallpaperManager & screens:** `FLAG_SYSTEM` / `FLAG_LOCK` application
  behaves differently across Android versions and OEMs; documented with the
  wallpaper engine (Phase 3).
- **WorkManager periodic work** has a 15-minute minimum interval and is
  inexact under Doze; the 30-minute option in the spec is the lowest offered
  and execution times are estimates, never promises.
- **JDK note:** Gradle and toolchains run on JDK 21; Kotlin targets JVM 17
  bytecode (the baseline AGP 8.x supports for desugaring-free minSdk 29).

## Testing strategy

- JVM unit tests (`app/src/test`): domain logic, ViewModel seams, mapping.
  Phase 1: 7 tests across ThemeMode storage mapping, navigation contract,
  and the settings ViewModel→use case→repository chain (MockK + Turbine +
  coroutines-test), all green.
- Room DAO and database tests require Robolectric or instrumentation —
  introduced with the Quran data import (Phase 2), where they guard text
  integrity.
- Compose UI tests arrive with Phase 4 screens.
- Instrumented tests cannot run in the current development environment
  (no emulator/device attached); `androidTest` dependencies are configured
  so they execute unchanged once a device is available.
