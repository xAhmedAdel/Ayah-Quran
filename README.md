# Ayah — A verse in every moment

Ayah is a native Android application centered on the Holy Quran. Its
signature feature (arriving in Phase 3) is an automated system that renders
authentic Quranic verses into premium wallpapers and applies them to the
Home and Lock screens via `WallpaperManager`.

**Current status: Phase 5 (Audio Foundation) complete** — full offline Quran
reading with the verified Tanzil Uthmani dataset, bookmarks, reading
position, and the premium wallpaper engine (five templates, immersive
full-screen preview, confirmed Home/Lock application, automatic updates via
WorkManager, managed history), on a polished, accessible, animated UI.
See [docs/reports](docs/reports) for phase reports.

## Features (Phase 2)

- Complete Quran text: 114 surahs, 6,236 ayahs, Uthmani script, stored
  verbatim from the verified [Tanzil.net](https://tanzil.net) dataset
  (CC-BY 3.0; see [docs/QURAN_DATA.md](docs/QURAN_DATA.md)).
- Madani mushaf structure: juz and page metadata for every ayah.
- Surah index with Arabic/transliterated/English name search.
- Reader with RTL Arabic typography (Amiri, SIL OFL 1.1), mushaf-convention
  bismillah header, ayah numbering in Arabic-Indic digits, prev/next surah
  navigation.
- Bookmarks (persisted in Room) and last-reading-position restore.
- First-launch dataset import: validated before write, transactional,
  idempotent, with honest progress/failure states.
- Appearance themes (System/Light/Dark) persisted via DataStore.
- Integrity guarantees: 30 automated JVM tests (including a full-text
  cross-check of all 6,236 ayahs against an independent second dataset) and
  8 instrumented tests running the real import on-device.

## Building

Requirements: JDK 17+ (developed on JDK 21), Android SDK 36.

```bash
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # JVM tests (dataset integrity + logic)
./gradlew connectedDebugAndroidTest   # on-device tests (emulator/device needed)
./gradlew assembleRelease        # R8-minified release APK (unsigned)
```

Open the project root in Android Studio (Koala or newer) for IDE support.

## Architecture

Clean architecture in a single `:app` module with strict package boundaries
(`domain/`, `data/`, `core/`, `ui/`, `di/`, `navigation/`), Jetpack Compose
(Material 3, custom design system), Hilt, Room (schema exported),
DataStore, Kotlin Coroutines/Flow. Details and decisions:
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Documentation

- [docs/QURAN_DATA.md](docs/QURAN_DATA.md) — dataset source, checksums,
  license, verification methodology.
- [docs/LICENSES.md](docs/LICENSES.md) — third-party licenses.
- [docs/reports/](docs/reports/) — per-phase reports with screenshots.

## Roadmap

Phase 5 (Audio Foundation) complete: Media3/ExoPlayer architecture + honest
empty state. Phase 6 — Release preparation (signing, physical-device
compatibility, TalkBack walkthrough, audio licensing).
