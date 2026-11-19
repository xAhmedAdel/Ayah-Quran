# 00 — Project Understanding

Ground-truth architectural and functional summary of **Ayah** (`com.ayah.quran`, `v1.0.0-beta.6`, commit `43fc2a1` / app baseline `3d4cb56`, branch `main`). Verified directly from source code and the 18 handover documents in `docs/UI-UX-HANDOVER/`.

---

## 1. Product Identity & Scope

Ayah is an offline-first, Arabic-default Android Quran and Islamic companion application built with Kotlin and Jetpack Compose. It combines five functional pillars in a single-module (`:app`) Clean Architecture codebase:

1. **Authentic Quran Reader**: 604-page Madani Mushaf reader (`HorizontalPager`, pinch-zoom, authentic page boundaries) plus an adjustable-text continuous reader, backed by verified Tanzil Uthmani v1.1 text (114 surahs, 6,236 ayahs, 30 juz).
2. **Per-Ayah Recitation Audio**: Media3 `MediaSessionService` background player with 5 verified reciters (`AlQuran Cloud / EveryAyah`), per-ayah offline downloads, system notification + lock-screen controls, sleep timer, playback speed, repeat ayah/range, playback restoration, and live reader sync (ayah highlight + auto page-follow + continuous auto-scroll).
3. **Offline Prayer Times & Adhan**: Fully offline calculation via `adhan-java` (10 calculation methods, Shafi'i/Hanafi Asr, high-latitude rules, per-prayer minute offsets, Hijri date adjustment), exact alarms (`AlarmManager`), boot re-arming, per-prayer Adhan modes, bundled + downloadable Adhan voice library, custom user audio file support, and strict **Adhan-wins** audio conflict policy over Quran playback.
4. **Quranic Wallpaper Engine**: On-device bitmap renderer (`WallpaperRenderer`) combining verified Uthmani ayahs with 5 structured templates, live full-screen preview, Home/Lock/Both application via `WallpaperManager`, WorkManager periodic rotation (30m to 24h), and applied wallpaper history.
5. **App Shell & Distribution**: Arabic-default RTL localization (`AppLocale.wrap()` in `attachBaseContext`) with instant activity-recreate English LTR switching, Light/Dark/System theme modes (fixed brand palette, Material You intentionally disabled), and a GitHub Releases in-app APK updater (OkHttp + FileProvider + system installer).

No user accounts, login, cloud sync, analytics, ads, or tablet two-pane layouts exist.

---

## 2. Technical Stack & Pinned Versions

| Layer | Technology / Version | Notes |
|---|---|---|
| Build / Language | AGP `8.13.2`, Kotlin `2.3.21`, KSP `2.3.14`, Gradle `8.13` | Pinned matrix; do not bump during UI redesign |
| SDK Targets | `compileSdk = 36`, `targetSdk = 36`, `minSdk = 29` (Android 10) | Release uses R8/minify |
| UI | Jetpack Compose BOM `2026.06.00`, Material 3, Activity Compose `1.12.4` | Single-activity (`MainActivity`) with `enableEdgeToEdge()` |
| DI | Hilt `2.58` (`@HiltViewModel`, `@AndroidEntryPoint`) | Custom `ApplicationScope` + `DefaultDispatcher` |
| Persistence | Room (Quran text, pages, bookmarks, reading position, wallpaper history) + DataStore (prefs) + SharedPreferences (locale, playback restore) | Schema/keys are protected |
| Audio | Media3 `1.11.1` (`exoplayer` + `session`) | `QuranPlaybackService` (`USAGE_MEDIA`) + `AdhanPlaybackService` (`USAGE_ALARM`) |
| Prayer Calc | `com.batoulapps.adhan:adhan:1.2.1` | Fully offline + bundled `assets/prayer/cities.json` |
| Scheduling | `AlarmManager` (exact alarms + fallback) + WorkManager (on-demand Hilt factory) | `RECEIVE_BOOT_COMPLETED` re-arm |
| Networking | Direct OkHttp with browser UA | System `DownloadManager` avoided due to MagicOS unreliability |
| Typography Assets | `res/font/amiri_regular.ttf`, `res/font/amiri_bold.ttf` (SIL OFL 1.1) | System `Roboto` (`FontFamily.Default`) used for all UI chrome |

---

## 3. Codebase Map (`app/src/main/java/com/ayah/quran/`)

```text
core/
├── database/        Room entities + DAOs (Quran, mushaf pages, bookmarks, reading_progress, wallpaper_history)
├── designsystem/    theme/{Color,Type,Spacing,Theme}.kt, motion/AyahMotion.kt, AyahNumbering.kt,
│                    component/{AyahCard,SectionCard,EmptyState,ConfirmDialog}.kt
├── locale/          AppLocale.kt (AR default), SurahName.kt (localizedDisplayName, revelationLabel)
├── quran/           AyahSelector.kt
└── wallpaper/       WallpaperRenderer.kt, WallpaperTemplate.kt (5 launch templates)
data/
├── audio/           QuranAudioCatalog, QuranAudioDownloadRepository, ExoPlayerAyahPlaylistPlayer,
│                    QuranPlaybackService, ExoPlayerAudioRepository (legacy Phase 5), prefs impl
├── prayer/          BundledAdhanCatalog, AdhanDownloadRepository, AdhanPlaybackSourceResolver,
│                    CityCatalog, DeviceLocationProvider, PermissionIntents, prefs impl
├── quran/           QuranImporter, QuranTextParser (Tanzil Uthmani)
└── update/          AppUpdateRepository (GitHub Releases), AppUpdateInstaller
di/                  Hilt modules + AudioPlaybackEntryPoint (used by test @Before hooks)
domain/              model/, repository/, update/, usecase/
navigation/          AyahApp.kt (Scaffold + MiniPlayerBar + NavHost + Routes), TopLevelDestination.kt
prayer/              PrayerTimeManager, PrayerAlarmReceiver, PrayerSystemReceiver,
                     AdhanPlaybackService, PrayerNotifications, DiagnosticAlarmScheduler
ui/                  38 Kotlin files across audio/, bookmarks/, home/, main/, prayer/,
                     quran/, reader/, settings/, wallpapers/
```

---

## 4. Current Navigation Structure

- **Single `NavHost`** in `navigation/AyahApp.kt`.
- **6 Top-Level Tabs** (`TopLevelDestination` enum, RTL order right-to-left):
  `home` (الرئيسية) · `quran` (القرآن) · `prayer` (الصلوات) · `wallpapers` (الخلفيات) · `audio` (الصوت) · `settings` (الإعدادات).
- **9 Stacked Destinations**:
  - `mushaf?surahId={surahId}&page={page}&ayah={ayah}` (`MushafPageReaderScreen`)
  - `reader/{surahId}?ayah={ayah}` (`ReaderScreen`)
  - `bookmarks` (`BookmarksScreen`)
  - `prayer/settings` (`PrayerSettingsScreen`)
  - `prayer/onboarding` (`PrayerOnboardingScreen`)
  - `prayer/adhan-voice` (`AdhanVoiceScreen`)
  - `prayer/adhan-library/{prayer}` (`AdhanLibraryScreen`)
  - `preview/{surahId}/{ayahNumber}` (`FullscreenPreviewScreen`)
  - `audio/now-playing` (`NowPlayingScreen`)
- **Persistent Mini Player**: Currently rendered inside a `Column` *above* the `NavHost` on every route except `audio` (causing top-bar double-inset gaps and showing redundantly on `audio/now-playing` and reader screens).

---

## 5. Protected Core Capabilities & Technical Constraints

The redesign has full creative freedom over visual design, layouts, navigation structure, copy, and shared components, while strictly preserving:

1. **Quran Integrity**:
   - Byte-identical Tanzil Uthmani dataset (`assets/quran/quran-uthmani.txt`, 6,236 ayahs, 114 surahs) and `quran-data.xml` / `ayah-metrics.txt`.
   - Authentic 604-page boundaries (`GetMushafPageUseCase`, `PageForAyahUseCase`).
   - `MushafText.kt` `AnnotatedString` builder and character-range tap resolution (`buildMushafString`, `findAyahAtOffset`).
   - `MushafFollowPolicy` one-navigation-per-recited-ayah semantics and reading-position persistence.
2. **Audio Plumbing**:
   - Single source of truth: `ExoPlayerAyahPlaylistPlayer.state` (`AyahPlaybackState`), `QuranAudioDownloadRepository.states`, and `AudioPreferencesRepository.preferences`. Never introduce disconnected local flows.
   - Restore never auto-plays; resume restarts the saved ayah; Adhan strictly stops and blocks Quran playback (`blockedByAdhan`).
3. **Prayer & OS Reliability**:
   - Offline `adhan-java` calculation, `AlarmManager` scheduling, channel IDs (`PrayerNotificationsChannels`), and `AdhanPlaybackService` (`USAGE_ALARM`).
   - Honest real-time permission checks (`POST_NOTIFICATIONS`, notification channel, `SCHEDULE_EXACT_ALARM`, vendor background guidance). Android 14+ exact-alarm revocation kills the process; HONOR/MagicOS restricts background alarms unless user disables auto-manage, so diagnostic tools (test notification, test adhan, 60s diagnostic alarm) and vendor guide must stay accessible.
4. **Wallpaper Engine**:
   - `WallpaperRenderer` RTL text fitting, `WallpaperManager` flag combinations (Home/Lock/Both), bitmap lifecycle, WorkManager rotation, and the 5 launch template IDs pinned by `WallpaperTemplateContractTest`.
5. **Baseline Test Suites**:
   - **148 JVM unit tests** (25 files, including `TopLevelDestinationTest` which currently pins the 6-tab enum).
   - **58 instrumented tests** (13 files, including 4 Compose UI suites relying on exact localized strings/content-descriptions with zero `testTag`s and `AudioPlaybackEntryPoint.clearRestore()` `@Before` hooks).
