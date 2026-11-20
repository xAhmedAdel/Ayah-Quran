# AYAH — Phase C-2 Report: Adhkar & Dua Experience

- **Date:** 2026-11-20
- **Branch:** `main`
- **Base C-1 Commit:** `b121808fb056788028a1de56e941ead0837ce15a` (`test(c-1): add dataset integrity, normalizer, Room migration, and companion DAO tests + C-1 report`)
- **App Version:** `1.0.0-beta.9` (`versionCode = 10` — unchanged)
- **Database Schema:** Room `v4` (`4.json` — unchanged; zero schema modifications in C-2)
- **Push / Release Status:** Local commits ONLY (not pushed, no release created)
- **Phase Boundary:** Phase C-2 ONLY. Phase C-3 (Tasbih UI), C-4 (Hadith UI), C-5 (Home Companion Strip / Daily Reflection Switcher / Reminders), Share Cards, and Adhkar Audio have **NOT** been started.

---

## 1. Files Created & Modified

### New Production Files
- `app/src/main/java/com/ayah/quran/core/time/CompanionDateProvider.kt` — `CompanionDateProvider` interface + `SystemCompanionDateProvider` (`todayKey()` in `YYYY-MM-DD`, `currentHour()` using `ZoneId.systemDefault()`).
- `app/src/main/java/com/ayah/quran/ui/companion/adhkar/AdhkarHubViewModel.kt` — Reactive state over Room `AdhkarRepository` (time-of-day recommended session, Daily Sessions, Sunnah Surahs, Hisn al-Muslim categories, favorites filter, diacritic-insensitive search, day-rollover refresh).
- `app/src/main/java/com/ayah/quran/ui/companion/adhkar/AdhkarHubScreen.kt` — Direction D Adhkar Hub screen + shared localization/clipboard helpers (`localizedCategoryTitle`, `localizedItemTitle`, `localizedItemSource`, `copyDhikrText`).
- `app/src/main/java/com/ayah/quran/ui/companion/adhkar/AdhkarSessionViewModel.kt` — Guided session state machine (per-item progress, `repeatCount.coerceAtLeast(1)` guard, `Mutex` + 70 ms rapid-tap guard, auto-advance on completion, Undo stepping back across auto-advance, confirmation-guarded reset, day-rollover reset, font-scale adjustment, focus/list view toggle).
- `app/src/main/java/com/ayah/quran/ui/companion/adhkar/AdhkarSessionScreen.kt` — Guided `HorizontalPager` reading screen (`mushafCanvas` surface, `A-`/`A+` controls, verified source & non-duplicate expandable virtue, favorite/copy/open-Surah actions, pinned one-handed counting dock + Undo + Previous/Next, list overview mode, calm completion state, `ConfirmDialog` reset guard, TalkBack `LiveRegionMode.Polite` semantics, reduced-motion support).
- `app/src/main/java/com/ayah/quran/ui/companion/duas/DuasHubViewModel.kt` — Unified Dua Hub ViewModel over live `ResolvedQuranicDua` (joined against `ayahs` table), Prophetic Duas, and Situational Hisn al-Muslim categories, with category filter chips, favorites toggle, and diacritic-insensitive search via `ArabicTextNormalizer`.
- `app/src/main/java/com/ayah/quran/ui/companion/duas/DuasHubScreen.kt` — Unified 3-segment Dua Hub (`Quranic`, `Prophetic`, `By Situation`) with filter chips, live Uthmani Quranic verse rendering + `Open in Mushaf`, copy with snackbar feedback, and favorites toggle.

### Modified Production Files
- `app/src/main/java/com/ayah/quran/di/CompanionModule.kt` — Bound `SystemCompanionDateProvider` to `CompanionDateProvider`.
- `app/src/main/java/com/ayah/quran/navigation/AyahApp.kt` — Added `Routes.ADHKAR_HUB`, `Routes.ADHKAR_SESSION`, `Routes.DUAS_HUB`, hid `AudioMiniPlayer` on `ADHKAR_SESSION` (preserving it on `ADHKAR_HUB` & `DUAS_HUB`), wired contextual callbacks into `PrayerTimesScreen`, kept the 5 bottom navigation destinations unchanged.
- `app/src/main/java/com/ayah/quran/ui/prayer/PrayerTimesScreen.kt` — Added contextual `AfterPrayerAdhkarCard` (`Start Session` -> `after_prayer`, `All Adhkar & Duas` -> `ADHKAR_HUB`) using existing Direction D `AyahSurfaceCard` + `AyahTonalButton` + `TextButton`.
- `app/src/main/java/com/ayah/quran/core/designsystem/component/AyahStepProgress.kt` — Wrapped step fraction (`$clampedStep / $safeTotal`) in `AyahNumbering.isolateLtr(...)` after RTL visual verification caught `0 / 26` rendering as `26 / 0` in Arabic.
- `app/src/main/res/values/strings.xml` — Added 57 English strings for Adhkar Hub, Guided Session, Duas Hub, and Prayer contextual card.
- `app/src/main/res/values-ar/strings.xml` — Added 57 matching Arabic strings.

### New & Modified Test Files
- `app/src/test/java/com/ayah/quran/ui/companion/adhkar/AdhkarSessionViewModelTest.kt` — 5 JVM unit tests (opening guard & zero-repeat coercion, auto-advance + Undo step-back, persistence restoration & reset, day-rollover fresh state, rapid-tap guard).
- `app/src/test/java/com/ayah/quran/ui/companion/adhkar/AdhkarAndDuasHubViewModelTest.kt` — 3 JVM unit tests (time-of-day recommended session selection, category summaries & Sunnah Surahs grouping, Dua Hub Quranic filtering & diacritic-insensitive search).
- `app/src/test/java/com/ayah/quran/navigation/MiniPlayerVisibilityTest.kt` — Added assertions confirming mini-player shows on `ADHKAR_HUB` & `DUAS_HUB` and hides on `ADHKAR_SESSION`.
- `app/src/androidTest/java/com/ayah/quran/ui/AdhkarAndDuasUiTest.kt` — 2 end-to-end Compose instrumented UI tests covering Prayer After-Prayer entry point, Guided Session increment/persistence/undo/accessibility semantics, Adhkar Hub categories & morning session launch, Duas Hub 3-tab switching, search filtering (`ربنا` -> `qdua_02`), and Quranic Dua `Open in Mushaf` navigation.

---

## 2. Architecture, Navigation & UX Summary

### Navigation Integration
- Bottom navigation remains strictly **5 tabs** (`Home`, `Quran`, `Prayer`, `Audio`, `Wallpapers`). No sixth tab and no Home screen changes were added in C-2.
- Added 3 internal routes inside `AyahApp.kt`:
  - `adhkar` (`Routes.ADHKAR_HUB`)
  - `adhkar_session/{categoryId}?itemId={itemId}` (`Routes.ADHKAR_SESSION`)
  - `duas` (`Routes.DUAS_HUB`)
- Entry point in C-2: `PrayerTimesScreen` -> `AfterPrayerAdhkarCard` (`Start Session` opens `adhkar_session/after_prayer`; `All Adhkar & Duas` opens `adhkar`, which links to `duas` via TopAppBar action and dedicated Hub card).

### Persistent Daily Progress, Undo & Completion
- All progress reads/writes flow exclusively through C-1's `AdhkarRepository` -> Room `dhikr_daily_progress` keyed by `(dayKey, itemId)` (`YYYY-MM-DD`, local timezone via `CompanionDateProvider`).
- Opening a session never marks items completed; `repeatCount` is guarded via `.coerceAtLeast(1)`.
- Tapping the pinned primary dock button acquires a coroutine `Mutex`, enforces a 70 ms rapid-tap guard, increments Room progress, triggers light/completion haptic feedback, and auto-advances after 320 ms (`0 ms` when reduced motion is active) ONLY when the item completes and a next item exists.
- `Undo` decrements the active item when `currentCount > 0`, or steps back to `lastAction.pageIndex` and decrements the previous item immediately after auto-advance.
- Destructive reset requires explicit `ConfirmDialog` confirmation.
- On `ON_RESUME`, both `AdhkarHubViewModel` and `AdhkarSessionViewModel` invoke `refreshDayKey()`, starting a fresh daily record automatically across midnight without overwriting prior days.
- No streaks, XP, points, or gamification mechanics exist anywhere in C-2.

### Dua Hub, Quranic Mushaf & Prayer Integration
- **Quranic Duas:** Live-joined from `quranic_duas` pointers against `ayahs` (`text_uthmani`) via `AdhkarRepository.observeQuranicDuas()`. Never duplicates Quran text. Tapping `Open in Mushaf` resolves the exact page/ayah through the existing `QuranRepository.getAyahPage(surahId, ayahNumber)` + `Routes.mushaf(...)` path.
- **Daily Sunnah Surahs:** `Al-Kahf` (18), `Al-Mulk` (67), and `As-Sajdah` (32) open directly in the existing Mushaf reader (`getSurahFirstPage(surahId)` -> `Routes.mushaf(...)`).
- **Prayer Screen Integration:** Contextual `AfterPrayerAdhkarCard` is placed non-intrusively below the primary prayer card without touching `PrayerTimeManager`, Adhan alarms, permissions, or prayer calculations.

---

## 3. Content Integrity & Licensing Verification
- Zero new unverified datasets or external text sources were imported in C-2; C-2 reads exclusively from C-1's verified Room database (`adhkar_hisn.json`, `quranic_duas.json`).
- Arabic dhikr text is rendered verbatim (`item.textAr`) with `QuranTextFontFamily` and `TextDirection.Rtl`.
- English translation (`item.textEn`) is rendered only when present in the verified licensed dataset and when English locale is active; Hisn al-Muslim items without verified English translations gracefully render verbatim Arabic text with localized English category/source metadata without machine translation.
- Expandable virtue disclosure renders only when `virtue` is explicitly present and distinct from `source` (preventing redundant display).

---

## 4. Verification Results

### Static Checks
- Zero comments (`//`, `/*`) across all new C-2 Kotlin source and test files.
- Zero emojis across all new code, string resources, and documentation.

### Build & JVM Unit Tests
- Command: `.\gradlew :app:testDebugUnitTest :app:assembleDebug`
- Result: **BUILD SUCCESSFUL**
- JVM Unit Tests: **188 / 188 passed**, 0 failed, 0 skipped (180 pre-existing/C-1 + 8 new C-2 unit tests).

### Android Instrumented Tests (Emulator — `ayah_avd`, Android 16 / API 36, 1080×2400)
> **Validation Environment Disclosure:** All instrumented and visual tests below were executed on the **`ayah_avd` Android Emulator (API 36)**, NOT a physical Android device.

- **Targeted C-2 Suite (`AdhkarAndDuasUiTest`):** **2 / 2 passed**, 0 failed, 0 skipped.
- **Full Regression Suite (`connectedDebugAndroidTest`):**
  - Run 1 (all 70 tests): **66 / 67** non-`PrayerTimesUiTest` tests passed (including `AdhkarAndDuasUiTest`, `AyahDatabaseMigrationTest`, `CompanionDaoInstrumentedTest`, `AppUiTest`, `MushafPageReaderUiTest`, `AudioTabUiTest`, `NotificationAndDownloadInstrumentedTest`) with **1 pre-existing API-gated skip** (`PrayerPermissionsInstrumentedTest.notificationDenialDisablesPostingPath`). At test 68/70, the API 36 emulator's `FinalizerWatchdogDaemon` timed out on `HardwareRenderer$DestroyContextRunnable` (`system_server` crash).
  - Run 2 (fresh emulator reboot, isolated `PrayerTimesUiTest`): **3 / 3 passed**, 0 failed, 0 skipped.
  - **Combined Instrumented Result:** **69 / 70 passed**, **0 failed**, **1 pre-existing API-gated skip** (`PrayerPermissionsInstrumentedTest.notificationDenialDisablesPostingPath`).
- Post-polish re-verification after RTL/font-scale fixes: `:app:testDebugUnitTest` (188/188), `:app:assembleDebug`, and `AdhkarAndDuasUiTest` (2/2) all **PASSED**.

### Emulator Visual Verification (`docs/ISLAMIC-COMPANION-EXPANSION/screenshots/c-2/`)
All 7 screenshots were captured on `ayah_avd` (API 36) and visually inspected:
1. `01_prayer_after_adhkar_ar_light.png` — Prayer screen contextual After-Prayer Adhkar card (AR Light RTL).
2. `02_adhkar_hub_ar_light.png` — Adhkar Hub: search bar, time-of-day hero (`0 / 8`), Daily Sessions grid, Daily Sunnah Surahs (AR Light RTL).
3. `03_adhkar_session_ar_light.png` — Guided Adhkar Session: `0 / 26` & `1 / 26` progress, `A-`/`A+` controls, verbatim Arabic text on `mushafCanvas`, verified source, pinned emerald counting dock + Undo (AR Light RTL).
4. `04_duas_hub_quranic_ar_light.png` — Unified Duas Hub: 3-segment switcher, filter chips, live Uthmani Quranic Duas + `Open in Mushaf` & copy buttons (AR Light RTL).
5. `05_adhkar_session_ar_dark.png` — Guided Adhkar Session in true Dark Mode (Obsidian Moss + Antique Gold primary dock).
6. `06_adhkar_hub_en_light.png` — Adhkar Hub in English LTR (`Recommended Now`, `Daily Sessions`, `Daily Sunnah Surahs`).
7. `07_adhkar_session_font150.png` — Guided Adhkar Session at `font_scale = 1.5`: single-line `A-`/`A+` buttons, scrollable text card, pinned counting dock accessible with zero clipping.

### Visual Defects Found & Fixed During Inspection
1. `AyahStepProgress` rendered `0 / 26` reversed as `26 / 0` in Arabic RTL — fixed via `AyahNumbering.isolateLtr(...)`.
2. `A-` / `A+` mirrored to `-A` / `+A` in RTL and wrapped onto two lines at `1.5x` font scale — fixed with `AyahNumbering.isolateLtr(...)`, `maxLines = 1`, `softWrap = false`, and `48.dp` min touch target.
3. Duplicate virtue disclosure when `virtueAr == sourceAr` — suppressed duplicate virtue row when trimmed strings match.

---

## 5. Known Limitations & Deviations
- **Physical Device Pending:** Verified on `ayah_avd` (API 36 emulator); physical Android device testing remains for user sign-off after approval.
- **English Dhikr Translations:** Per C-1 licensing rules, Hisn al-Muslim items without verified English translations display verbatim Arabic text rather than unverified translations.
- **No Unapproved Scope:** Home Companion Strip, Tasbih UI, Hadith UI, Daily Reflection Switcher, Reminders, Share Cards, and Adhkar Audio are intentionally excluded per C-2 authorization.

---

## 6. Completion Gate Status
Phase C-2 is **COMPLETE** locally and ready for review. Stopping here; Phase C-3 has **NOT** been started.
