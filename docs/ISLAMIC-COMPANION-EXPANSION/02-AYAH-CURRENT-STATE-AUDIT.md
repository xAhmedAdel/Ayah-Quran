# 02 — Ayah Current-State Technical & UX Audit

## 1. Verified Baseline

- **Working Directory**: `C:\AYAH`
- **Branch / HEAD**: `main` @ `9c757e83b50714a3467d910f26410778ea10efd54`
- **Version**: `1.0.0-beta.9` (`versionCode = 10`)
- **Min / Target / Compile SDK**: `29` / `36` / `36`
- **Stack**: Kotlin `2.3.21`, AGP `8.13.2`, KSP `2.3.14`, Compose BOM `2026.06.00`, Material 3, Navigation Compose `2.9.8`, Hilt `2.58`, Room `2.8.5`, DataStore Preferences `1.2.1`, WorkManager `2.12.0`, Media3 `1.11.1`, Coroutines `1.11.0`, `adhan-java` (offline prayer calculations).

---

## 2. Existing Architecture & Module Structure

Single Android `:app` module organized cleanly by Clean Architecture layers:

- `core/database/`: Single Room database `AyahDatabase` (`ayah.db`, **version 3**, `exportSchema = true`, schemas exported at `app/schemas/com.ayah.quran.core.database.AyahDatabase/{1,2,3}.json`).
  - **Entities (6)**: `SurahEntity`, `AyahEntity` (6,236 verified Tanzil Uthmani ayahs with authentic 604-page mapping), `BookmarkEntity` (unique `(surahId, ayahNumber)` with FK cascade to `AyahEntity`), `ReadingProgressEntity`, `WallpaperHistoryEntity`, `SelectionHistoryEntity`.
  - **Migration Policy (`DatabaseModule.kt`)**: **No destructive fallback** (beta is live on real devices). Every schema bump from `v3 -> v4+` requires an explicit `Migration` object registered via `.addMigrations(...)` and verified with `room-testing`.
- `di/`: Hilt singleton modules (`AppModule`, `AudioModule`, `CoroutinesModule`, `DatabaseModule`, `DataStoreModule`, `PrayerModule`).
- `data/`: Repository implementations backed by Room, single Preferences DataStore (`ayah_preferences`), bundled assets (`assets/quran/quran-uthmani.txt`, `quran-data.xml`, `ayah-metrics.txt`, `assets/prayer/cities.json`), bundled raw Adhan audio (`res/raw/`), Media3 `QuranPlaybackService`, and GitHub Releases in-app updater (`data/update/`).
- `domain/`: Pure Kotlin models, repository interfaces, and focused single-responsibility use cases.
- `prayer/`: Offline prayer orchestration (`PrayerTimeManager` with `reconcileMutex` keeping exactly one `AlarmManager` alarm armed at a time), `PrayerAlarmReceiver`, `PrayerSystemReceiver` (`BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `DATE_CHANGED`, `MY_PACKAGE_REPLACED`), `PrayerNotifications`, `AdhanPlaybackService` (Adhan-wins audio focus), and `DiagnosticAlarm`.
- `background/`: `WallpaperWorker` (`@HiltWorker` periodic WorkManager task, unique name `wallpaper_periodic_update`).
- `core/designsystem/`: Complete Direction D (**Al-Mushaf Al-Hadith**) token and component library (see Section 5).
- `ui/`: Feature packages (`home`, `quran`, `reader`, `bookmarks`, `prayer`, `audio`, `wallpapers`, `settings`, `main`).

---

## 3. Navigation & Current Screen Map

### Top-Level Bottom Navigation (`TopLevelDestination.kt`)
5 destinations with `saveState = true`, `restoreState = true`, `launchSingleTop = true`, and a globally docked `MiniPlayerBar` above the bottom bar (hidden on `NOW_PLAYING`, `FULLSCREEN_PREVIEW`, `MUSHAF`, `READER`):

1. **Home (`home`)** — Amiri header + Hijri date + Settings icon button, conditional prayer onboarding warning banner, Sanctuary Emerald Continue Reading hero card, live Next Prayer card, Daily Ayah card (with Open in Reader + Create Wallpaper actions), Wallpaper summary card, and Bookmarks + Audio quick buttons.
2. **Quran (`quran`)** — 3-segment switcher (`Surahs · Juz · Bookmarks`), `AyahSearchBar`, 114 surahs with starting Mushaf page badges, 30 canonical Juz cards, embedded `BookmarksListContent` with Undo snackbar, and Quick Page Jump (`1..604`).
3. **Prayer (`prayer`)** — Sanctuary Emerald Next Prayer Hero card (city selector, Hijri + Gregorian date, countdown, Adhan status), structured 5-prayer timetable with row-tap `AdhanMode` sheet, separated Sunrise row, conditional exact-alarm banner.
4. **Audio (`audio`)** — Virtualized single `LazyColumn`, 5 horizontal reciter cards, search + `All`/`Downloaded` filter, 114 surahs with play/pause, inline download progress, and delete confirmation.
5. **Wallpapers (`wallpapers`)** — Live TopAppBar auto-rotation badge, sticky clipped `9:16` phone preview stage, 5 visual template swatches, font scale / position / Surah sheet / Ayah input / selection mode controls, Automation card, History card, pinned Destination + Apply bar.

### Secondary Routes (`Routes` in `AyahApp.kt`)
- `settings` (Settings Hub: visual System/Light/Dark preview cards, AR/EN language switcher, Reading Mode sheet, Prayer/Audio/Wallpapers links, In-App Updates, Licenses sheet)
- `mushaf?surahId=&page=&ayah=` & `reader/{surahId}?ayah=` (Mushaf 604-page reader & Continuous reader with `ReaderAyahActionPill` -> Play / Bookmark / More -> `RepeatRangeDialog` & Create Wallpaper)
- `bookmarks` (Standalone Bookmarks route)
- `prayer/settings`, `prayer/adhan-voice`, `prayer/adhan-library/{prayer}`, `prayer/onboarding`
- `audio/now-playing` (Now Playing with Open-in-Mushaf, `AyahHeroCard`, ayah slider, Repeat Range, Sleep Timer, Speed)
- `preview/{surahId}/{ayahNumber}` (Fullscreen Wallpaper Preview)

---

## 4. Reusable Infrastructure for Companion Expansion

| Existing Infrastructure | File(s) | How Companion Expansion Reuses It |
| :--- | :--- | :--- |
| **Verified Quran Database** | `AyahEntity`, `AyahDao`, `QuranRepository` | **Quranic Duas** store `(surahId, ayahNumber)` references and query `AyahEntity` directly — guaranteed authentic Uthmani script + one-tap Mushaf jump. |
| **Daily Deterministic Selector** | `AyahSelector` (`core/quran/AyahSelector.kt`) | Same `epochDay`-seeded deterministic selection pattern for **Daily Hadith** and **Daily Dhikr** (zero randomness flicker across restarts). |
| **Room Schema & Export Setup** | `AyahDatabase`, `app/schemas/` | Add companion tables cleanly in `Migration(3, 4)` without touching existing 6 Quran/Wallpaper tables; test migrations via `androidx.room:room-testing` (already in `libs.versions.toml`!). |
| **Single DataStore** | `DataStoreModule` (`ayah_preferences`) | Store lightweight user preferences (Tasbih haptic/sound toggles, reminder toggles & times, quiet hours, Home daily-card visibility) using namespaced key prefixes (`companion_*`). |
| **Notification & Permission Checks** | `PrayerNotifications.canNotify()`, `PermissionIntents`, `PrayerSystemStatusProbe` | Reuse `POST_NOTIFICATIONS` permission check and system notification settings intent. **Keep reminder channels strictly separate** from `PrayerNotificationsChannels` (`PLAYBACK`, `ALERT`, `SILENT`). |
| **WorkManager (`@HiltWorker`)** | `AyahApplication` (`Configuration.Provider`), `WallpaperWorker` | Reuse Hilt-backed WorkManager for optional non-exact reminder jobs (Morning/Evening Adhkar, Daily Hadith, Daily Reading), avoiding `SCHEDULE_EXACT_ALARM` contention with `PrayerTimeManager`. |
| **System Boot / Time Receiver** | `PrayerSystemReceiver` | Extend (or add a dedicated `CompanionReminderReceiver`) to reschedule pending companion reminders on `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`. |
| **Offscreen Share Card Renderer** | `WallpaperRenderer`, `WallpaperTemplate` | Reuse Canvas + Amiri font + Direction D Sanctuary Emerald / Warm Parchment framing to render shareable **Hadith / Dhikr / Dua cards** via `FileProvider` (`xml/file_paths.xml` already configured!). |
| **Localization & BiDi** | `AppLocale`, `AyahNumbering.isolateLtr`, `values/` + `values-ar/` + `<plurals>` | Apply existing BiDi isolation to Hadith numbers (`#42`), progress counts (`14/33`), and targets, with full AR RTL and EN LTR parity. |

---

## 5. Reusable Design System Assets (`core/designsystem/`)

- **Palette (`Color.kt`, `Theme.kt`)**: Sanctuary Emerald (`#0F3D32`), Antique Gold (`#C4A052` / AA `#8F6E2A`), Warm Parchment Light (`#F6F2E9` / `#FCF9F2`), Obsidian Moss Dark (`#0C1210` / `#131B17`), semantic `LocalAyahColors` (`statusSuccess`, `statusWarning`, `mushafCanvas`).
- **Typography (`Type.kt`)**: Bundled `Amiri` (calligraphic Arabic headers, Quranic/Hadith/Dhikr display text) + `IBM Plex Sans Arabic` (SIL OFL 1.1, functional UI Arabic & English), `withTabularNumbers()` for counters and progress indicators.
- **Shared Components (14)**: `AyahHeroCard`, `AyahSurfaceCard`, `AyahGroupCard`, `AyahInsetDivider`, `AyahSettingRow`, `AyahSearchBar`, `AyahSelectionSheet`, `AyahStatusBadge`, `AyahBannerStrip`, `AyahStepProgress`, `AyahPrimaryButton`, `AyahTonalButton`, `AyahOutlinedButton`, `ConfirmDialog`, `EmptyState`, `SurahNumberEmblem`, `MushafSurahBanner`.
- **Motion (`AyahMotion.kt`)**: `MotionDurations.resolve(ms, motionEnabled)` + `rememberMotionEnabled()` honoring system reduced-motion (`ANIMATOR_DURATION_SCALE = 0`).

---

## 6. Existing Permissions & Overlap Check

- **Existing Manifest Permissions**: `SET_WALLPAPER`, `ACCESS_COARSE_LOCATION`, `SCHEDULE_EXACT_ALARM`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `INTERNET`, `REQUEST_INSTALL_PACKAGES`.
- **New Permissions Needed for Expansion**: **ZERO new dangerous permissions** (only optional `VIBRATE` normal permission if explicit `Vibrator` fallback is used alongside `HapticFeedback` API, which requires zero permissions!).
- **Feature Overlap Check**:
  - `BookmarkEntity` is strictly FK-bound to `AyahEntity(surahId, ayahNumber)` — keep Quran bookmarks pure and add a dedicated lightweight `companion_favorites` table (or per-domain `isFavorite` column) rather than polluting `bookmarks`.
  - `HomeScreen` already has `DailyAyahCard` — integrate `Daily Hadith` as a clean tabbed/segmented toggle inside the existing Daily Reflection slot rather than stacking a second large quote card on Home.
  - `SearchSurahsUseCase` searches Surah names in-memory (`QuranViewModel`); full-text search across Hadith and Adhkar should use a shared Arabic normalization utility + SQLite FTS4/FTS5 (`@Fts4`) for instant offline queries.

---

## 7. Existing Test Infrastructure & Coverage Baseline

- **JVM Unit Tests (`app/src/test/`, 31 files, 163 tests)**: JUnit 4, MockK `1.14.12`, Turbine `1.2.1`, `kotlinx-coroutines-test`, WCAG contrast verification (`AyahThemeContrastTest`), BiDi tests (`AyahNumberingTest`), Quran dataset & 604-page mapping integrity tests (`QuranDatasetIntegrityTest`, `MushafPageMappingIntegrityTest`), Prayer calculator reference tests, ViewModel unit tests.
- **Instrumented Tests (`app/src/androidTest/`, 13 files, 64 tests)**: Compose UI tests (`AppUiTest`, `AudioTabUiTest`, `MushafPageReaderUiTest`, `PrayerTimesUiTest`), Room DAO test (`BookmarkReadingProgressDaoTest`), Wallpaper renderer & WorkManager scheduling tests (`work-testing`), Quran import test, Adhan audio & permissions tests.
- **Existing Coverage Gap to Close in Expansion**: `androidx-room-testing` is already in `libs.versions.toml` and schemas `1..3.json` are exported, but no `AyahDatabaseMigrationTest` exists yet. Adding automated `MigrationTestHelper` tests (`v3 -> v4`) is mandatory before shipping any companion table.
