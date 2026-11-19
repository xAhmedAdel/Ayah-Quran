# AYAH — UI-4 Report: Quran Hub (Surahs, Juz, Bookmarks & Quick Page Jump)

## 1. Scope Completed

Phase UI-4 redesigns the **Quran Hub** (`QuranScreen` / `QuranViewModel`) and **Bookmarks** (`BookmarksScreen` / `BookmarksViewModel`) into Direction D (**Al-Mushaf Al-Hadith**), unifying Surahs, Juz 1–30, Bookmarks, and quick 604-page navigation into a single calm discovery experience while preserving the first-launch Quran dataset import, Room bookmark persistence, and `Routes.BOOKMARKS` compatibility.

### Quran Hub (`QuranScreen` + `QuranViewModel`)
- **Top Bar with Quick Page Jump**:
  - Title (`القرآن` / `Quran`) paired with a tonal `الانتقال إلى صفحة` / `Go to page` pill (`testTag("quran_page_jump_button")`) opening a validated `1..604` numeric page-jump dialog (`QuranPageJumpDialog`) that navigates directly to `Routes.mushaf(page = page)`.
- **Pill Search Bar (`AyahSearchBar`)**:
  - Full-width rounded search bar (`quran_search_hint`) with instant Clear (`X`) button, filtering seamlessly across Surahs, Juz (by number, starting Surah, or Uthmani verse text), and Bookmarks.
- **3-Segment Switcher (`QuranHubTabSwitcher`)**:
  - Pill-shaped 3-way switcher (`السور` / `Surahs`, `الأجزاء` / `Juz`, `المحفوظات` / `Bookmarks` with live bookmark count badge when `> 0`) and stable test tags (`quran_tab_surahs`, `quran_tab_juz`, `quran_tab_bookmarks`).
- **Surah Row Redesign (`SurahRow`)**:
  - Octagonal geometric rosette badge (`SurahNumberEmblem`) displaying `1..114`.
  - **Arabic Locale**: Fixes the duplicate Arabic Surah name bug by showing `surah.nameArabic` once in Amiri (`23sp`) above revelation type + pluralized ayah count (`pluralStringResource(R.plurals.ayah_count_plurals, ...)`), with the authentic Mushaf starting page badge (`ص 1`..`ص 604` via `PageForSurahStartUseCase`) at the trailing edge.
  - **English Locale**: Displays `nameTransliterated` above `nameEnglish · revelation · N ayahs` at the leading edge, and calligraphic `nameArabic` + `p. N` at the trailing edge.
- **Juz 1–30 Discovery (`JuzList` / `JuzRow`)**:
  - Derives all 30 canonical Juz summaries (`QuranViewModel.JUZ_STARTS`, matching `assets/quran/quran-data.xml`) from existing verified Room data (`GetAyahUseCase` + `GetSurahUseCase`) without any database schema changes.
  - Each row shows a gold octagonal Juz emblem (`1..30`), localized title (`الجزء 1` / `Juz 1`), BiDi-isolated page range badge (`ص 1–21` / `p. 1–21`), starting Surah & Ayah reference (`الفاتحة · 1:1`), and a 1-line Uthmani verse preview in Amiri.
  - Tapping a Juz row opens the active reader (`Mushaf` or `Continuous`) at the exact starting Surah and Ayah of that Juz.

### Bookmarks Redesign (`BookmarksScreen` + `BookmarksViewModel`)
- **Shared `BookmarksListContent`**:
  - Used both inside the `Bookmarks` segment of `QuranScreen` and on the standalone `Routes.BOOKMARKS` screen.
  - **Fixes Latin-only Surah Name Bug in Arabic**: Resolves `bookmark.surahNameArabic` when `AppLocale.isArabic` is true and `bookmark.surahNameTransliterated` in English, paired with BiDi-isolated `surahId:ayahNumber` inside a gold `AyahStatusBadge`.
  - **One-Tap Remove with Undo Snackbar**: Trailing gold bookmark icon button removes the bookmark via `ToggleBookmarkUseCase` and displays a `Snackbar` (`Bookmark removed` / `تمت إزالة العلامة المرجعية`) with an `Undo` / `تراجع` action that restores the exact verse.

## 2. Files Changed

- `app/src/main/java/com/ayah/quran/navigation/AyahApp.kt`
- `app/src/main/java/com/ayah/quran/ui/bookmarks/BookmarksScreen.kt`
- `app/src/main/java/com/ayah/quran/ui/bookmarks/BookmarksViewModel.kt`
- `app/src/main/java/com/ayah/quran/ui/quran/QuranScreen.kt`
- `app/src/main/java/com/ayah/quran/ui/quran/QuranViewModel.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-ar/strings.xml`
- `app/src/test/java/com/ayah/quran/ui/bookmarks/BookmarksViewModelTest.kt` (new)
- `app/src/test/java/com/ayah/quran/ui/quran/QuranViewModelTest.kt` (new)
- `app/src/androidTest/java/com/ayah/quran/ui/AppUiTest.kt`

## 3. Verification & Test Results

- **Debug Build (`:app:assembleDebug`)**: PASS
- **JVM Unit Tests (`:app:testDebugUnitTest`)**: **163/163 passed** (0 failed, 0 skipped), including 4 new tests in `BookmarksViewModelTest` and `QuranViewModelTest`.
- **Instrumented Tests (`:app:connectedDebugAndroidTest` on `ayah_avd`, Android 16 / API 36)**: **62/62 active passed** (1 pre-existing API-gated skip `PrayerPermissionsInstrumentedTest.notificationDenialDisablesPostingPath`, 0 failed), including updated `bottomNavigationReachesQuranSurahList` and new `quranHubSwitchesAcrossJuzAndBookmarksAndOpensPageJump` in `AppUiTest` plus all 6 `MushafPageReaderUiTest` cases.

## 4. Visual Verification Screenshots (`docs/UI-UX-REDESIGN/screenshots/ui-4/`)

1. `01_quran_surahs_ar_light.png` — Quran Hub Surahs tab AR Light (`SurahNumberEmblem`, single Arabic Surah title, plural ayah count, starting page badge `ص 1`..`ص 151`).
2. `02_quran_juz_ar_light.png` — Quran Hub Juz tab AR Light (`الجزء 1`..`الجزء 6`, page ranges `ص 1–21`, starting Surah:Ayah, Uthmani snippet).
3. `03_quran_bookmarks_empty_ar_light.png` — Quran Hub Bookmarks tab empty state AR Light.
4. `04_quran_bookmarks_populated_ar_light.png` — Quran Hub Bookmarks tab populated AR Light (`المحفوظات (1)`, `الفاتحة · 1:2`, Uthmani verse, gold remove action).
5. `05_quran_page_jump_ar_light.png` — Quick Page Jump dialog (`الانتقال إلى صفحة`) open on Quran Hub AR Light.
6. `06_quran_surahs_ar_dark.png` — Quran Hub Surahs tab AR Dark (Obsidian Moss canvas, Antique Gold active segment and emblems).
7. `07_quran_juz_ar_dark.png` — Quran Hub Juz tab AR Dark.
8. `08_quran_surahs_en_light.png` — Quran Hub Surahs tab EN Light (LTR, transliterated + English names on left, Arabic calligraphy + `p. N` on right).
9. `09_quran_juz_en_light.png` — Quran Hub Juz tab EN Light (LTR, `Juz 1..6`, `p. 1–21`).
10. `10_quran_bookmarks_en_light.png` — Quran Hub Bookmarks tab populated EN Light (`Bookmarks (1)`, `Al-Faatiha · 1:2`).
11. `11_quran_ar_font150.png` — Quran Hub Surahs tab AR Light at `font_scale = 1.5`.

## 5. Known Limitations & Next Gate

- Per phased delivery rules, work stops here at the **UI-4 Review Gate** before starting UI-5 (Mushaf Page Reader & Continuous Reader chrome redesign).
