# UI-1 Completion Report — Design System & Localization Foundation

Summary of Slice UI-1 implementation under approved **Direction D (Al-Mushaf Al-Hadith)**. No UI-2 navigation or full-screen redesigns were started.

---

## 1. Pre-UI-1 Corrections Completed

1. Replaced mislabeled `docs/UI-UX-HANDOVER/screenshots/quran/15_quran_list.png` with authentic Quran Surah list capture and updated `docs/UI-UX-HANDOVER/SCREENSHOT-INDEX.md`.
2. Captured 10 pre-UI-1 baseline screenshots across Light Mode, Dark Mode, Arabic RTL, and English LTR in `docs/UI-UX-REDESIGN/screenshots/baseline/`.
3. Updated `docs/UI-UX-REDESIGN/prototypes/ayah-redesign-prototype.html` to reflect the approved compact Reader Ayah Action Pill (`Play · Bookmark · More` overflow for Repeat Range & Wallpaper).
4. Reconfirmed branch `main`, HEAD `43fc2a1` -> committed UI-0 package at `a0cc5eb`.

---

## 2. IBM Plex Sans Arabic Verification & APK Impact

- **Official Source**: `https://github.com/google/fonts/tree/main/ofl/ibmplexsansarabic` (IBM Corp. official release).
- **License**: SIL Open Font License 1.1 (`Copyright © 2017 IBM Corp. with Reserved Font Name "Plex"`).
- **License Files & Attribution**:
  - Full license added at `docs/licenses/ibm-plex-sans-arabic/OFL.txt`.
  - Documented in `docs/LICENSES.md`.
  - Surfaced in-app in `settings_licenses_summary` (both `values/strings.xml` and `values-ar/strings.xml`).
- **Weights Bundled (Static TTFs — rock-solid on minSdk 29..36)**:
  - `ibm_plex_sans_arabic_regular.ttf` (235,924 B)
  - `ibm_plex_sans_arabic_medium.ttf` (242,100 B)
  - `ibm_plex_sans_arabic_semibold.ttf` (244,616 B)
  - `ibm_plex_sans_arabic_bold.ttf` (246,992 B)
- **APK Size Impact (Debug APK)**:
  - Pre-UI-1 baseline (`3d4cb56`): `84,752,756` bytes (`80.83 MB`)
  - Post-UI-1 (`app-debug.apk`): `85,596,759` bytes (`81.63 MB`)
  - Delta: `+844,003` bytes (`+0.80 MB` in debug APK; smaller in R8-shrunk compressed release).
- **Typography Separation**:
  - `QuranTextFontFamily` (Amiri) stays dedicated to Quranic Uthmani text, wordmark, and approved Surah/calligraphic headers.
  - `AyahUiFontFamily` (IBM Plex Sans Arabic) powers all functional Arabic & English UI typography with tabular numerals (`tnum`) support.

---

## 3. Design System & Localization Implemented

### Theme & Core Tokens (`core/designsystem/`)
- `theme/Color.kt`: Sanctuary Emerald (`#0F3D32`), Antique Gold (`#C4A052` / AA `#8F6E2A`), Warm Parchment (`#F6F2E9` / `#FCF9F2`), Obsidian Moss (`#0C1210` / `#131B17`), complete 5-step `surfaceContainer*` ramp, and `AyahExtendedColors` (`LocalAyahColors`: hero, mushaf canvas/highlight/marker, semantic success/warning, scrim).
- `theme/Type.kt`: `AyahUiFontFamily`, `AyahWordmarkStyle`, `AyahSurahDisplayStyle`, `AyahTabularCountdownStyle`, `withTabularNumbers()`, generous Arabic-friendly line heights.
- `theme/Spacing.kt` & `theme/Shapes.kt`: `AyahSpacing`, `AyahRadii`, `AyahTouchTargets` (`min = 48.dp`), `AyahShapes`.
- `theme/Theme.kt`: Wires `LocalAyahColors` + `AyahShapes` + `SideEffect` syncing Android status bar & navigation bar icon appearance (`isAppearanceLightStatusBars = !useDark`, fixing invisible status bar icons on in-app Dark/Light override!).
- `motion/AyahMotion.kt`: Adds `MotionDurations.MICRO`, `MotionDurations.resolve()`, and `AyahMotionSpecs` respecting `rememberMotionEnabled()`.
- `AyahNumbering.kt`: Preserves `toArabicIndic()`, adds `formatForLocale()` and `isolateLtr()` (`\u2066...\u2069`).
- `AyahIcons.kt`: One-icon-one-meaning registry + non-mirrored RTL-aware `mushafPreviousArrow()` / `mushafNextArrow()`.

### Reusable Components (`core/designsystem/component/`)
- Upgraded API-compatibly: `AyahCard` (tonal 0dp-shadow + 1dp hairline border + 16dp radius), `SectionHeader`, `SectionCard`, `EmptyState` (64dp tonal icon medallion + optional action slot), `ConfirmDialog` (28dp sheet radius + tonal surface).
- Added: `AyahSurfaceCard`, `AyahHeroCard`, `AyahGroupCard` (+ `AyahInsetDivider`), `AyahButtons` (`AyahPrimaryButton`, `AyahTonalButton`, `AyahOutlinedButton`), `AyahSearchBar`, `AyahStatusBadge`, `AyahSettingRow`, `AyahBannerStrip`, `AyahSelectionSheet`, `AyahStepProgress`, `SurahNumberEmblem`, `MushafSurahBanner`, `ReaderAyahActionPill` (compact primary pill `Play · Bookmark · More` with overflow menu for Repeat Range & Wallpaper), `AyahComponentPreviews` (AR Light, AR Dark, EN 1.5x font scale).

### Localization Fixes (`res/values/strings.xml`, `res/values-ar/strings.xml`)
- Fixed `"توقّع النظام"` -> `"تلقائي (النظام)"`.
- Fixed `"صبح فخري"` -> `"صباح فخري"`, `"نسب المنسوب"` -> `"نسب المصنَّف"`, added IBM Plex Sans Arabic SIL OFL 1.1 attribution.
- Fixed `"رحم الله القرّاء"` on living reciters -> neutral respectful attribution with BiDi isolation.
- Removed `"WorkManager"` and `"phase"` developer jargon from user copy.
- Standardized `"التشغيل في الخلفية"` (no longer colliding with Wallpapers) and `"المنبهات الدقيقة"`.
- Fixed grammar & clarity: `"تمت جدولة"`, `"عند الحصول على الحقوق"`, `"وضع القراءة المستمرة"`, `"٦٠٤ صفحات أصلية"`, `"تخطي الآن"`, `"حذف العنصر"`, `"من المختارات"`.
- Marked `wallpapers_history_ayah_format` `translatable="false"` with BiDi isolation (fixes missing AR translation).
- Added `<plurals>` (`ayah_count_plurals`, `duration_minutes_plurals`, `duration_hours_plurals`) and shared UI-1 action strings (`action_cancel`, `action_clear`, `action_more`, `action_dismiss`, `reader_repeat_range`, `reader_make_wallpaper`).

---

## 4. Verification & Test Results

- **Build**: `.\gradlew :app:assembleDebug` — **BUILD SUCCESSFUL** (0 errors, 0 warnings in new UI-1 code).
- **JVM Unit Tests**: `.\gradlew :app:testDebugUnitTest` — **153 / 153 PASSED** (148 existing + 5 new in `AyahThemeContrastTest` and `AyahNumberingTest` verifying WCAG AA/AAA contrast >= 4.5:1 / >= 7.0:1 across Light & Dark palettes and BiDi isolation).
- **Instrumented Tests on `ayah_avd` (API 36)**: `.\gradlew :app:connectedDebugAndroidTest` — **58 / 58 PASSED** (+1 expected API 33+ skipped revoke test; 0 failed). Quran import, 604-page Mushaf reader, Audio tab, Prayer times, Permissions, and Wallpaper engine tests all green.
- **Emulator Visual Verification**: Captured 12 post-UI-1 screenshots in `docs/UI-UX-REDESIGN/screenshots/ui-1/` verifying Warm Parchment Light, Obsidian Moss Dark, status bar icon contrast fix, IBM Plex Sans Arabic UI rendering, unchanged Amiri Uthmani Quran rendering, English LTR, and `1.5x` font scaling.

---

## 5. Known Limitations (Intentionally Deferred to UI-2..UI-8)

- Existing 6-tab bottom bar wraps `"الإعدادات"` at `1.5x` font scale and `"Prayer times"` in English — resolved in **UI-2** (5-tab navigation + Home header Settings).
- `SettingsScreen` still renders `BuildConfig.VERSION_NAME` without `AyahNumbering.isolateLtr()` (reversed `beta.6-debug-1.0.0` in Arabic) — resolved in **UI-3** when migrating `SettingsScreen`.
- `MushafText` still uses `MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)` until **UI-5** wires `LocalAyahColors.current.mushafAyahHighlight`.
