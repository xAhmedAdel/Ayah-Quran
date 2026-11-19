# Navigation Map

Implementation: single-activity Compose navigation — `ui/navigation/AyahApp.kt`.
Routes declared as string constants in the `Routes` object;
top-level destinations in `TopLevelDestination` enum. Bottom bar is
rendered by the app `Scaffold` and hidden on reader screens (immersive).

## Routes (verified from AyahApp.kt)

| Route pattern | Screen |
|---|---|
| `home` | HomeScreen |
| `quran` | QuranScreen |
| `prayer` | PrayerTimesScreen |
| `wallpapers` | WallpapersScreen |
| `audio` | AudioScreen |
| `settings` | SettingsScreen |
| `reader/{surahId}?ayah={ayah}` | ReaderScreen (continuous) |
| `mushaf?surahId={surahId}&page={page}&ayah={ayah}` | MushafPageReaderScreen |
| `bookmarks` | BookmarksScreen |
| `prayer/settings` | PrayerSettingsScreen |
| `prayer/onboarding` | PrayerOnboardingScreen |
| `prayer/adhan-voice` | AdhanVoiceScreen |
| `prayer/adhan-library/{prayer}` | AdhanLibraryScreen |
| `preview/{surahId}/{ayahNumber}` | FullscreenPreviewScreen |
| `audio/now-playing` | NowPlayingScreen |

## Mermaid — major flows

```mermaid
flowchart LR
    subgraph BottomTabs["Bottom tabs (6)"]
        HOME[الرئيسية Home]
        QURAN[القرآن Quran list]
        PRAYER[الصلوات Prayer times]
        WALL[الخلفيات Wallpapers]
        AUDIO[الصوت Audio catalog]
        SETTINGS[الإعدادات Settings]
    end
    HOME -->|متابعة القراءة| MUSHAF[Mushaf page reader]
    HOME -->|العلامات المرجعية| BOOKMARKS[Bookmarks]
    HOME -->|تفعيل تنبيهات الصلاة| ONBOARDING[Prayer onboarding]
    HOME -->|إعدادات الخلفيات| WALL
    QURAN -->|surah tap| MUSHAF
    MUSHAF -->|وضع النص المتغير| CONT[Continuous reader]
    CONT -->|وضع المصحف| MUSHAF
    MUSHAF -->|page jump dialog| JUMPDialog[PageJumpDialog]
    PRAYER -->|gear| PSETTINGS[Prayer settings]
    PSETTINGS -->|أصوات الأذان| VOICES[Adhan voices]
    VOICES -->|اختيار الصوت| LIB[Adhan library]
    PSETTINGS -->|onboarding rows| ONBOARDING
    AUDIO -->|mini player tap| NOWPLAYING[Now Playing]
    WALL -->|preview| FSPREVIEW[Fullscreen preview]
    SETTINGS -->|updates card| UPDATER[check / download / install states]
    MINI[Mini player bar — all tabs] -->|tap| NOWPLAYING
```

## Behavior notes

- **Back stack**: tab switches use `navigateToTopLevel` (restore state,
  single-top). Reader screens push on the stack; system back pops.
  The reader hides the bottom bar; leaving the reader returns to the tab
  it came from.
- **State restoration**: nav state survives process recreation (Compose
  rememberSaveable + SavedStateHandle args, e.g. mushaf
  `surahId/page/ayah`).
- **Deep links**: none declared.
- **Transitions**: fade + subtle slide (`AyahApp` NavHost transitions,
  respecting a motion-disabled setting).
- **Mini player**: rendered by the outer Scaffold above the NavHost on
  every route EXCEPT `audio`; tapping navigates to `audio/now-playing`.
