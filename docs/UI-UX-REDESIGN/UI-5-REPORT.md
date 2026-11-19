# AYAH — UI-5 Report: Mushaf Page Reader & Continuous Reader

## 1. Scope Completed

Phase UI-5 redesigns the chrome and interactive controls of both **Mushaf Page Reader** (`MushafPageReaderScreen` / `MushafPageViewModel`) and **Continuous Adjustable-Text Reader** (`ReaderScreen` / `ReaderViewModel`) into Direction D (**Al-Mushaf Al-Hadith**), while strictly preserving the byte-identical Tanzil Uthmani text pipeline, 604-page mapping, `MushafText` span/offset math, pinch-zoom (`1f..4f`) + double-tap reset, and `MushafFollowPolicy` recitation page-follow.

### Mushaf Page Reader (`MushafPageReaderScreen` + `MushafPageViewModel`)
- **Heirloom Canvas & Minimalist Top Bar**:
  - Uses `LocalAyahColors.current.mushafCanvas` (`#FBF7EE` Warm Parchment Light / `#101613` Obsidian Moss Dark) across TopAppBar, pager canvas, and bottom navigation bar.
  - Declutters TopAppBar to Back (`reader_back`), Surah title (`singleSurahTitle()`), and Mode Switch (`continuous_mode_cd`, `testTag("mushaf_switch_continuous")`).
- **Ornamental Surah Header (`MushafSurahBanner`)**:
  - Replaces the floating white `AyahCard` shadow box with the Direction D inline ornamental `MushafSurahBanner` (emerald-tinted band framed by top and bottom antique-gold hairline rules, Amiri Surah calligraphy, and centered Bismillah).
- **Compact Floating `ReaderAyahActionPill` + `More` Menu**:
  - Appears smoothly above the bottom page bar whenever an ayah is tapped/selected on the current page.
  - Displays `Surah · surahId:ayahNumber`, primary `Play` pill (`playFromAyah`), reactive `Bookmark` toggle (`observeIsBookmarked` updated to `highlight.flatMapLatest`), `More` menu (`Repeat range` -> `RepeatRangeDialog`, `Create wallpaper` -> `Routes.fullscreenPreview`), and 1-tap `Dismiss` (`X`).
- **Repeat Range Dialog (`RepeatRangeDialog`)**:
  - Exposes `ExoPlayerAyahPlaylistPlayer.setRepeatRange(QuranRepeatRange(from, to))` with validated `1..maxAyah` numeric `From ayah` / `To ayah` inputs, `Start repeat` (applies range and starts recitation at `fromAyah`), and `Clear repeat` when a range is active.
- **Calm Bottom Page Bar (`MushafPageNavBar`)**:
  - Uses non-deprecated `AyahIcons.mushafPreviousArrow(isRtl)` / `AyahIcons.mushafNextArrow(isRtl)` (eliminating `Icons.Filled.KeyboardArrowLeft`/`Right` deprecation warnings), a tonal gold pill indicator (`mushaf_page_indicator`) opening `PageJumpDialog` (`1..604` with numeric keyboard and `action_cancel`), and generous bottom page padding so the last verse line never clips.

### Continuous Reader (`ReaderScreen` + `ReaderViewModel`)
- **Unified Top Bar & Ornamental Banner**:
  - Replaces the 5-icon crowded TopAppBar and deprecated `ScreenRotation` icon with 3 clear actions: `FontSize` (`AyahIcons.FontSize`), `AutoFollowRecitation` (`AyahIcons.AutoFollowRecitation`), and `MushafMode` (`AyahIcons.MushafMode`, which now also persists `ReadingMode.MUSHAF` symmetrically).
  - Replaces the duplicate-title `SurahHeader` card with `MushafSurahBanner` displaying the Arabic Surah title, metadata subtitle (`revelation · ayahs · Juz · Pages`), and Bismillah.
- **Fixed Font Size & Auto-Scroll Card (`FontSizeControls`)**:
  - Fixes the broken no-op close button (`onClick = { }` -> `onClick = { showFontControls = false }`, `testTag("reader_close_font_controls")`) and includes a labeled `Follow recitation automatically` switch alongside the `0.5f..2.0f` font-size slider.
- **Bottom-Anchored `ReaderAyahActionPill` & Bottom Surah Bar**:
  - Anchors `ReaderAyahActionPill` above the bottom Surah bar outside the scroll container so selecting any verse in a long Surah (such as 286-ayah Al-Baqarah) keeps `Play`, `Bookmark` (`viewModel.toggleBookmark`), `Repeat range`, and `Create wallpaper` immediately visible without scrolling to the bottom of the Surah.
  - Moves Previous/Next Surah navigation to a dedicated bottom `ReaderSurahNavBar` (`Surah · N / 114`) with RTL-consistent directional arrows.

### `MushafText.kt` Token Read Update
- Updated `MushafText` to read `LocalAyahColors.current.mushafAyahHighlight` and `LocalAyahColors.current.mushafMarker` with **zero changes** to `buildMushafString` or `findAyahAtOffset`.

## 2. Files Changed

- `app/src/main/java/com/ayah/quran/core/designsystem/component/ReaderAyahActionPill.kt`
- `app/src/main/java/com/ayah/quran/navigation/AyahApp.kt`
- `app/src/main/java/com/ayah/quran/ui/reader/MushafPageReaderScreen.kt`
- `app/src/main/java/com/ayah/quran/ui/reader/MushafPageViewModel.kt`
- `app/src/main/java/com/ayah/quran/ui/reader/MushafText.kt`
- `app/src/main/java/com/ayah/quran/ui/reader/ReaderScreen.kt`
- `app/src/main/java/com/ayah/quran/ui/reader/ReaderViewModel.kt`
- `app/src/main/java/com/ayah/quran/ui/reader/RepeatRangeDialog.kt` (new)
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-ar/strings.xml`
- `app/src/androidTest/java/com/ayah/quran/ui/MushafPageReaderUiTest.kt`

## 3. Verification & Test Results

- **Debug Build (`:app:assembleDebug`)**: PASS
- **JVM Unit Tests (`:app:testDebugUnitTest`)**: **163/163 passed** (0 failed, 0 skipped), including `MushafFollowPolicyTest`, `GetMushafPageUseCaseTest`, `MushafPageMappingIntegrityTest`, and `QuranDatasetIntegrityTest`.
- **Instrumented Tests (`:app:connectedDebugAndroidTest` on `ayah_avd`, Android 16 / API 36)**: **63/63 active passed** (1 pre-existing API-gated skip `PrayerPermissionsInstrumentedTest.notificationDenialDisablesPostingPath`, 0 failed), including all 7 `MushafPageReaderUiTest` cases (6 existing + new `switchBetweenMushafAndContinuousAndCloseFontControls`).

## 4. Visual Verification Screenshots (`docs/UI-UX-REDESIGN/screenshots/ui-5/`)

1. `01_mushaf_page1_ar_light.png` — Mushaf Page 1 (`الفاتحة`) AR Light (`mushafCanvas`, ornamental `MushafSurahBanner`, tonal page pill).
2. `02_mushaf_ayah_selected_ar_light.png` — Mushaf Page 1 with Ayah 4 selected (gold highlight wash + floating `ReaderAyahActionPill`).
3. `03_mushaf_more_menu_ar_light.png` — `ReaderAyahActionPill` `More` button state.
4. `04_mushaf_repeat_range_dialog_ar_light.png` — `More` menu (`تكرار نطاق آيات`, `إنشاء خلفية`) and `RepeatRangeDialog` (`من الآية` / `إلى الآية`).
5. `05_mushaf_page2_baqarah_ar_light.png` — Mushaf Page 2 (`البقرة` with ornamental banner and Bismillah).
6. `06_continuous_reader_ar_light.png` — Continuous Reader (`سورة البقرة` with ornamental banner, metadata subtitle, Bismillah, and bottom Surah bar `البقرة · 2 / 114`).
7. `07_continuous_font_controls_ar_light.png` — Continuous Reader with `FontSizeControls` card open (working Close button, slider, and Auto-Scroll switch).
8. `08_mushaf_ar_dark.png` — Mushaf Reader in Dark Mode (Obsidian Moss canvas, gold banner rules, gold ayah highlight, and floating action pill).
9. `09_continuous_ar_dark.png` — Continuous Reader in Dark Mode.
10. `10_mushaf_en_light.png` — Mushaf Reader in English LTR (`Al-Faatiha · 1:4` pill, `Page 1 of 604`).
11. `11_continuous_en_light.png` — Continuous Reader in English LTR (`Surah Al-Faatiha`, `Meccan · 7 ayahs · Juz 1 · Pages 1–1`, `Al-Faatiha · 1 / 114`).

## 5. Known Limitations & Next Gate

- Per phased delivery rules, work stops here at the **UI-5 Review Gate** before starting UI-6 (Audio Studio & Now Playing).
