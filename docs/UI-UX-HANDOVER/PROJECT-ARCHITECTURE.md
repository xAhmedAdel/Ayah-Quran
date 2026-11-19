# Project Architecture

## Layering (Clean-ish, single module `:app`)

```
app/src/main/java/com/ayah/quran/
├── core/
│   ├── database/        # Room entities + DAOs (Quran text, mushaf pages)
│   ├── designsystem/    # theme (Color/Type/Spacing/Theme) + 4 shared components
│   ├── locale/          # AppLocale (Arabic-first), attachBaseContext wrapping
│   ├── quran/           # Quran data layer support
│   └── wallpaper/       # WallpaperRenderer, WallpaperTemplate
├── data/
│   ├── audio/           # QuranAudioCatalog, QuranAudioDownloadRepository,
│   │                    # ExoPlayerAyahPlaylistPlayer (MediaController client),
│   │                    # QuranPlaybackService (MediaSessionService),
│   │                    # ExoPlayerAudioRepository (legacy Phase 5), prefs impl
│   ├── prayer/          # BundledAdhanCatalog, AdhanDownloadRepository,
│   │                    # AdhanPlaybackSourceResolver, AdhanPreferences…
│   ├── quran/           # QuranImporter, QuranTextParser (Tanzil Uthmani)
│   └── update/          # AppUpdateRepository (GitHub Releases), AppUpdateInstaller
├── di/                  # Hilt modules (App/Audio/Coroutines/DataStore/Database/Prayer)
├── domain/
│   ├── model/           # Surah, Ayah, prayer + audio models
│   ├── repository/      # interfaces (QuranRepository, AudioRepository, …)
│   ├── update/          # AppUpdateVersion, AppUpdateReleaseParser
│   └── usecase/         # thin use cases (GetSurah, ObserveSurahAyahs, …)
├── navigation/          # AyahApp.kt (Scaffold + NavHost + Routes)
├── prayer/              # PrayerAlarmReceiver, PrayerTimeManager,
│                        # AdhanPlaybackService, PrayerNotifications, scheduler
└── ui/                  # 38 files: audio/ bookmarks/ home/ main/ prayer/
                         # quran/ reader/ settings/ wallpapers/
```

## Key architectural facts

- **DI**: Hilt everywhere (`@HiltViewModel`, `@AndroidEntryPoint` on both
  services). `ApplicationScope` + `DefaultDispatcher` qualifiers in
  `di/CoroutinesModule.kt`.
- **State**: unidirectional — screens collect `StateFlow<UiState>` from
  ViewModels via `collectAsStateWithLifecycle`; ViewModels combine
  repository flows. NoCompose-side business logic beyond trivial UI state.
  ⚠️ Historical bug class: a screen collecting a **locally-created**
  StateFlow instead of the real one ("dead flow") — keep flows wired.
- **Navigation**: single `NavHost` in `AyahApp.kt` with string routes in
  `Routes` object + `TopLevelDestination` enum (6 tabs). Bottom bar hidden
  on immersive screens (reader). Routes carry surahId/ayah/page args.
- **Persistence**: Room (Quran text/pages/bookmarks/reading positions) +
  DataStore (preferences: audio prefs, reader prefs, prayer prefs, theme,
  language) + SharedPreferences (quran playback restore snapshot).
- **Localization**: `res/values-ar` (primary, default) + `res/values`
  (English). `AppLocale.wrap()` in `attachBaseContext`; language switch
  recreates the activity. RTL is the default direction.
- **Audio**: `QuranPlaybackService` (Media3 `MediaSessionService`) owns the
  ExoPlayer/MediaSession; `ExoPlayerAyahPlaylistPlayer` is the in-process
  MediaController client that all UI talks to (one per-ayah MediaItem per
  playlist slot). Adhan has its own separate
  `AdhanPlaybackService` (USAGE_ALARM) — conflict policy: adhan wins.
- **Background**: two foreground services (mediaPlayback type) + exact
  alarms (AlarmManager) + `RECEIVE_BOOT_COMPLETED` re-arm +
  WorkManager (wallpaper rotation).
- **In-app updater**: Settings → GitHub Releases check → OkHttp download →
  FileProvider → system installer.

## Test suites (must stay green)

| Suite | Count | Location |
|---|---|---|
| JVM unit tests | 148 | `app/src/test/**` |
| Instrumented | 58 (+1 skipped) | `app/src/androidTest/**` |

UI-sensitive instrumented classes: `AudioTabUiTest`,
`MushafPageReaderUiTest`, `AppUiTest`,
`PrayerPermissionsInstrumentedTest` — they match real strings/content-descs
and reset persisted playback state in `@Before`
(`AudioPlaybackEntryPoint.clearRestore()`).
