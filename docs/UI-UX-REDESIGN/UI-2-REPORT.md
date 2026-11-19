# UI-2 Completion Report — Shell, 5-Tab Navigation & Docked Mini-Player

Summary of Slice UI-2 implementation under approved **Direction D (Al-Mushaf Al-Hadith)**. Full Home/Settings/Quran/Reader/Audio/Prayer/Wallpaper screen redesigns remain scoped to UI-3..UI-8.

---

## 1. Changes Implemented

### Five-Tab Bottom Navigation & Settings Entry
- `navigation/TopLevelDestination.kt`: Consolidated from 6 to the 5 approved daily pillars (`HOME`, `QURAN`, `PRAYER`, `AUDIO`, `WALLPAPERS`) using `Icons.AutoMirrored.*.MenuBook` and stable `testTag("nav_${destination.route}")`.
- `res/values/strings.xml`: Updated `nav_prayer` from `"Prayer times"` to `"Prayer"` so all 5 English tab labels render on one line without misalignment.
- `navigation/AyahApp.kt`:
  - Styled `NavigationBar` with Direction D tokens (`surfaceContainer`, `secondaryContainer` pill indicator, `primary` active label, `maxLines = 1`, `softWrap = false`).
  - Registered `Routes.SETTINGS = "settings"` as a stacked route reachable from Home header.
  - Fixed the double top-bar inset gap via `contentWindowInsets = WindowInsets.navigationBars` (and `WindowInsets(0)` on `Routes.FULLSCREEN_PREVIEW`) + `consumeWindowInsets(innerPadding)`.
- `ui/home/HomeScreen.kt`: Added compact top header row pairing the Amiri wordmark with a `48dp` tonal Settings `FilledTonalIconButton` (`testTag("home_settings_button")`) and `.statusBarsPadding()`.
- `ui/settings/SettingsScreen.kt`: Added `Scaffold` + `TopAppBar` with Back navigation so Settings works cleanly as a stacked destination.

### Floating Docked Mini-Player & Route Visibility Matrix
- `navigation/AyahApp.kt`: Relocated `MiniPlayerBar` from above `NavHost` to `Scaffold(bottomBar)` directly above the 5-tab `NavigationBar` (with `navigationBarsPadding()` on stacked utility routes) and added `AnimatedVisibility` respecting `rememberMotionEnabled()`.
- Added pure helper `shouldShowMiniPlayer(route: String?)`:
  - **Visible**: all 5 top-level tabs (`home`, `quran`, `prayer`, `audio`, `wallpapers`) and utility sub-screens (`settings`, `bookmarks`, `prayer/*`).
  - **Hidden**: `audio/now-playing`, `preview/{surahId}/{ayahNumber}`, `mushaf?...`, `reader/{surahId}?...` — fixing redundant mini-player stacking on Now Playing and reader TopAppBar displacement, while restoring 1-tap Now Playing access from the Audio tab.
- `ui/audio/MiniPlayerBar.kt` & `ui/audio/MiniPlayerViewModel.kt`:
  - Preserved exact singleton flow consumption (`ExoPlayerAyahPlaylistPlayer.state`, `QuranRepository.observeSurahs()`, `AudioPreferencesRepository.preferences`).
  - Redesigned as Direction D rounded floating pill (`16dp` radius, `surfaceContainerHigh`, `1dp` `outlineVariant`, `2.5dp` top ayah-progress bar, leading semantic icon tile, `44dp` circular Play/Pause button with accurate dynamic `contentDescription`).
  - Stored both Arabic and English/transliterated titles in `MiniPlayerUiState` and resolved locale at composition time so `Activity.recreate()` switches MiniPlayerBar text immediately between Arabic and English.

---

## 2. Build & Test Results

- `.\gradlew :app:assembleDebug` — **BUILD SUCCESSFUL**.
- `.\gradlew :app:testDebugUnitTest` — **156 / 156 PASSED** (updated `TopLevelDestinationTest` for 5 tabs + `Routes.SETTINGS`; added `MiniPlayerVisibilityTest` covering every route).
- `.\gradlew :app:connectedDebugAndroidTest` on `ayah_avd` (API 36) — **59 / 59 PASSED** (+1 expected API 33+ skipped revoke test; 0 failed). Updated `AppUiTest.bottomNavigationBarIconsExposeContentDescriptions` for all 5 tabs (now covering `nav_prayer` too!) and added `homeHeaderSettingsOpensSettingsScreenAndNavigatesBack`.

---

## 3. Emulator Verification Screenshots (`docs/UI-UX-REDESIGN/screenshots/ui-2/`)

- `01_home_5tabs_ar_light.png` — 5 tabs + Home Settings button (Arabic Light).
- `02_home_5tabs_ar_font15.png` — 5 tabs at `1.5x` font scale with zero wrapping or clipping.
- `03_settings_from_home_ar_light.png` — Settings opened from Home header with `TopAppBar` + Back arrow.
- `04_audio_docked_mini_ar_light.png` — Docked MiniPlayerBar on Audio tab (1-tap access to Now Playing).
- `05_home_docked_mini_ar_light.png` — Docked MiniPlayerBar on Home with zero top inset gap.
- `06_now_playing_no_mini_ar_light.png` — Now Playing with MiniPlayerBar suppressed and clean TopAppBar.
- `07_mushaf_no_mini_ar_light.png` — Mushaf reader during active recitation: no MiniPlayerBar collision, live ayah highlight, bottom page bar cleanly above Android nav bar.
- `08_home_5tabs_ar_dark.png` — Dark Mode 5 tabs + gold-accent docked MiniPlayerBar.
- `09_home_5tabs_en_light.png` — English LTR 5 tabs (`Home · Quran · Prayer · Audio · Wallpapers`) + instantly localized `"Mishary Alafasy — Al-Faatiha"` MiniPlayerBar.

---

## 4. Known Limitations (Scoped to Later Slices)

- `AudioScreen` still renders its legacy inline `NowPlayingBar` above the surah list alongside the new docked `MiniPlayerBar` -> consolidated cleanly in **UI-6** (`AudioScreen` LazyColumn redesign).
- `HomeScreen` and `SettingsScreen` keep their existing inner card layouts until **UI-3**.
