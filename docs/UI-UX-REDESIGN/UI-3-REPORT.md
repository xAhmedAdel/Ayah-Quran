# AYAH — UI-3 Report: Home Sanctuary & Unified Settings Hub

## 1. Scope Completed

Phase UI-3 redesigns **Home** (`HomeScreen` / `HomeViewModel`) and **Settings** (`SettingsScreen` / `SettingsHubViewModel`) into Direction D (**Al-Mushaf Al-Hadith**) while preserving every existing feature, business rule, navigation contract, and verification test.

### Home Sanctuary (`HomeScreen` + `HomeViewModel`)
- **Compact Sanctuary Header**: Amiri calligraphic wordmark (`آية` / `Ayah`), live localized Hijri date (`HijrahChronology` honoring the user's `hijriAdjustmentDays` and reactive to in-app AR/EN switches), and a `48dp` tonal Settings button (`testTag("home_settings_button")`).
- **Conditional Prayer Permissions Strip**: Slim `AyahBannerStrip(WARNING)` shown only when `showPrayerOnboardingCard` is true, stacking the action pill cleanly below the message so `1.5x` font scaling stays legible with zero text cramping.
- **Continue Reading Hero Card (`AyahHeroCard`)**:
  - Sanctuary Emerald surface with gold hairline border and gold CTA pill.
  - Shows saved Surah name in Amiri, formatted position (`home_continue_reading_format`), 604-page Mushaf page badge (`PageForAyahUseCase`), completion percentage & accessible progress bar, primary `Continue` button, and direct `Open Quran` action.
  - Honest cold-launch state prompting the user to open any Surah without fabricating progress.
- **Live Next Prayer Card (`NextPrayerHomeCard`)**:
  - Reuses existing `PrayerTimeManager` (`todayTimes`, `tomorrowFajr`, `countdown`) with a 1-second lifecycle-aware ticker (`SharingStarted.WhileSubscribed(5_000)`).
  - Displays configured city, next prayer name in Amiri, BiDi-isolated tabular countdown (`HH:MM:SS`), next prayer time, and Adhan enabled/disabled status.
  - Honest setup prompt (`Choose your city to see prayer times`) when no location is configured; taps route directly to the Prayer tab.
- **Daily Ayah Card (`DailyAyahCard`)**:
  - Reuses existing deterministic `AyahSelector.selectDaily` + `WallpaperRepository` and observes `ObserveQuranImportStateUseCase` so the card appears automatically once first-launch Quran import completes.
  - Renders centered Uthmani text in Amiri (`TextDirection.Rtl`), Surah reference (`Surah · S:A`), tap-to-open-in-reader, and a trailing Wallpaper preview shortcut (`Routes.fullscreenPreview`).
- **Wallpaper Summary & Quick Access**:
  - Preserves live rendered thumbnail preview (cached by entry key) when a wallpaper has been applied, honest empty state otherwise, and explicit text automation badge (`home_wallpaper_automation_on` / `off`).
  - Bottom quick-access row for Bookmarks (with live bookmark count from `ObserveBookmarkedAyahsUseCase`) and Audio Recitations.
- **Live Locale Switching**: Both Arabic and transliterated/English labels are carried in UI state and resolved via `AppLocale.isArabic` at composition time so `Activity.recreate()` never displays stale pre-switch strings.

### Unified Settings Hub (`SettingsScreen` + `SettingsHubViewModel`)
- **Visual Appearance Cards**: 3 side-by-side mini-preview cards (`Follow system`, `Light`, `Dark`) showing Parchment/Obsidian/Emerald/Gold miniatures with primary border and container tint on the active mode.
- **Grouped Language Card**: `AyahGroupCard` with Arabic/English radio rows and inset divider.
- **Grouped Preferences Hub (`SettingsHubViewModel`)**:
  - Dedicated `@HiltViewModel` aggregating `ReaderPreferencesRepository`, `AudioPreferencesRepository`, `PrayerPreferencesRepository`, and `ObserveWallpaperScheduleStatusUseCase` without altering `SettingsViewModel`'s constructor or existing tests.
  - **Reading & Mushaf**: live summary (`Mushaf (604 pages) · 100%` with LTR-isolated percentage) opening `AyahSelectionSheet` to switch between 604-page Mushaf and Continuous text mode.
  - **Prayer & Adhan**: live configured city (or `Not set`) navigating to `Routes.PRAYER_SETTINGS`.
  - **Audio & Recitations**: live selected reciter + LTR-isolated playback speed navigating to Audio tab.
  - **Wallpapers**: live WorkManager automation summary navigating to Wallpapers tab.
- **Updates Section**: Rounded progress indicator and BiDi-isolated release tag and APK size (`v1.0.0-beta.6`, `13.0 MB`).
- **About & Licenses**:
  - BiDi-isolated app version (`AyahNumbering.isolateLtr(BuildConfig.VERSION_NAME)`) fixing the RTL reversal defect (`1.0.0-beta.6-debug` now renders in exact order in Arabic).
  - Tappable `Licenses` row opening a scrollable `ModalBottomSheet` with full open-source and Quran/font/audio/city attributions.

## 2. Files Changed

- `app/src/main/java/com/ayah/quran/core/designsystem/component/AyahBannerStrip.kt`
- `app/src/main/java/com/ayah/quran/navigation/AyahApp.kt`
- `app/src/main/java/com/ayah/quran/ui/home/HomeScreen.kt`
- `app/src/main/java/com/ayah/quran/ui/home/HomeViewModel.kt`
- `app/src/main/java/com/ayah/quran/ui/settings/SettingsHubViewModel.kt` (new)
- `app/src/main/java/com/ayah/quran/ui/settings/SettingsScreen.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-ar/strings.xml`
- `app/src/test/java/com/ayah/quran/ui/settings/SettingsHubViewModelTest.kt` (new)
- `app/src/androidTest/java/com/ayah/quran/ui/AppUiTest.kt`

## 3. Verification & Test Results

- **Debug Build (`:app:assembleDebug`)**: PASS
- **JVM Unit Tests (`:app:testDebugUnitTest`)**: **159/159 passed** (0 failed, 0 skipped), including 3 new `SettingsHubViewModelTest` tests and unchanged `SettingsViewModelTest` (5/5).
- **Instrumented Tests (`:app:connectedDebugAndroidTest` on `ayah_avd`, Android 16 / API 36)**: **61/61 active passed** (1 pre-existing API-gated skip `PrayerPermissionsInstrumentedTest.notificationDenialDisablesPostingPath`, 0 failed), including 2 new UI-3 tests in `AppUiTest` (`homeSanctuaryShowsNextPrayerAndDailyAyah`, `settingsHubShowsPreferencesAndOpensLicensesSheet`).

## 4. Visual Verification Screenshots (`docs/UI-UX-REDESIGN/screenshots/ui-3/`)

1. `01_home_ar_light_cold.png` — Home Sanctuary AR Light (cold install: empty hero, prayer setup prompt, live Daily Ayah).
2. `02_home_ar_light_active.png` — Home Sanctuary AR Light (active: Al-Baqara Continue Reading Hero + live Cairo Fajr countdown + Daily Ayah).
3. `03_home_ar_light_scrolled.png` — Home Sanctuary AR Light scrolled (Wallpaper card, automation badge, Bookmarks & Audio row).
4. `04_home_ar_dark.png` — Home Sanctuary AR Dark (Obsidian Moss canvas, gold wordmark & countdown).
5. `05_settings_ar_light.png` — Unified Settings Hub AR Light (3 visual Appearance cards, Language, live Preferences with Cairo & LTR-isolated `100%`).
6. `06_settings_ar_light_bottom.png` — Unified Settings Hub AR Light bottom (Updates card + About card with BiDi-isolated `1.0.0-beta.6-debug`).
7. `07_settings_ar_licenses_sheet.png` — Licenses `ModalBottomSheet` in AR Light.
8. `08_settings_ar_dark.png` — Unified Settings Hub AR Dark.
9. `09_home_en_light.png` — Home Sanctuary EN Light (LTR, English Hijri date, English Next Prayer + Cairo, Al-Baqara Hero).
10. `10_settings_en_light.png` — Unified Settings Hub EN Light (LTR).
11. `11_home_ar_font150.png` — Home Sanctuary AR Light at `font_scale = 1.5` (verified zero clipping or banner cramping).
12. `12_settings_ar_font150.png` — Unified Settings Hub AR Light at `font_scale = 1.5`.

## 5. Known Limitations & Next Gate

- Home's `WallpaperHomeCard` active thumbnail state was verified via existing unit/UI logic and code review; emulator visual capture exercised the honest empty wallpaper state (wallpaper apply mutates device launcher wallpaper).
- Per phased delivery rules, work stops here at the **UI-3 Review Gate** before starting UI-4 (Quran Hub & Mushaf Reader).
