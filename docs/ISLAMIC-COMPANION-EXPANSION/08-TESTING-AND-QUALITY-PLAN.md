# 08 — Testing & Quality Verification Plan

## 1. Non-Regression Mandate

All existing verification suites must remain 100% green and unweakened across every expansion phase:
- Existing **163 JVM unit tests** (`:app:testDebugUnitTest`) — including `QuranDatasetIntegrityTest`, `MushafPageMappingIntegrityTest`, `PrayerTimesCalculatorReferenceTest`, `AyahThemeContrastTest`, `AyahNumberingTest`, `TopLevelDestinationTest`, `MiniPlayerVisibilityTest`.
- Existing **64 instrumented Android tests** (`:app:connectedDebugAndroidTest`) — including `AppUiTest`, `MushafPageReaderUiTest`, `AudioTabUiTest`, `PrayerTimesUiTest`, `PrayerPermissionsInstrumentedTest`, `WallpaperApplyInstrumentedTest`, `WallpaperSchedulingInstrumentedTest`.

---

## 2. New Test Suites by Domain

### A. Content & Religious Integrity Tests (`app/src/test/`)
1. **`AdhkarDatasetIntegrityTest`**:
   - Verifies every bundled `adhkar_hisn.json` entry has non-empty `id`, `categoryId`, `titleAr`, `textAr`, `sourceAr`, `repeatCount >= 1`.
   - Verifies **zero truncated texts** (e.g., checks full length & closing phrase of `Sayyid al-Istighfar`, `Ayat al-Kursi`, `Morning/Evening` core entries).
   - Verifies zero duplicate IDs and valid category foreign keys.
2. **`QuranicDuasIntegrityTest`**:
   - Loads `quranic_duas.json` alongside `quran-uthmani.txt` and proves every `(surahId, startAyah..endAyah)` pointer resolves to real ayahs inside `1..114` and valid ayah ranges.
3. **`HadithDatasetIntegrityTest`**:
   - Verifies every bundled Hadith has non-empty `id`, `collectionId`, `textAr`, `gradeAr`/`gradeEn`, **non-empty `graderAttribution`**, **non-empty `referenceAr`**, and **non-empty `sourceVersion`** (enforcing HadeethEnc attribution & version rules in CI!).
   - Verifies manifest `recordCount` matches parsed entry count exactly.
4. **`ArabicTextNormalizerTest`**:
   - Tests harakat stripping (`\u064B-\u065F`), Tatweel removal, Alef variants (`أإآٱ -> ا`), `ى -> ي`, `ة -> ه`, `ؤ -> و`, `ئ -> ي`, Arabic-Indic digits (`٠-٩` -> `0-9`), empty/mixed English-Arabic queries.

### B. Domain & ViewModel Unit Tests (`app/src/test/`)
5. **`TasbihViewModelTest`**:
   - Increment increments `currentCount` and `lifetimeCount`.
   - Reaching `targetCount` increments `completedRounds` and triggers completion haptic signal.
   - Undo (`-1`) decrements safely and clamps at `0` (never negative).
   - Reset zeroes `currentCount` only after confirmation callback.
   - Free-count mode (`targetCount == 0`) increments indefinitely without false completion triggers.
   - Custom counter creation validates non-empty Dhikr text and target bounds (`0..10000`), and deletion never removes the last remaining counter.
6. **`AdhkarSessionViewModelTest`**:
   - Tap increments item count, marks completed at `repeatCount`, persists to `dhikr_daily_progress`.
   - Undo decrements and clears `completed` flag if stepping below target.
   - Day rollover (`dayKey` changes from `D` to `D+1`) starts fresh counts while preserving favorites.
   - Reset session clears only that category's progress for today.
7. **`HadithLibraryViewModelTest` & `DailyHadithSelectorTest`**:
   - Deterministic `DailyHadithSelector(epochDay)` returns identical Hadith across restarts for the same day and rotates on `epochDay + 1`.
   - Favorite toggle persists and updates Saved filter reactively.
   - Opening a Hadith updates `lastReadAtMillis` and populates Recent filter.
8. **`CompanionReminderPolicyTest`**:
   - Computes exact next trigger delay across midnight, timezone shifts, and Friday-only Al-Kahf schedule.
   - Quiet hours guard (`23:00..05:00` wrapping midnight + daytime windows) accurately suppresses reminders.
   - Completion suppression skips Morning/Evening reminder when session is already complete.

### C. Database Migration & Persistence Tests (`app/src/androidTest/`)
9. **`AyahDatabaseMigrationTest`** *(Closes existing coverage gap!)*:
   - Uses `MigrationTestHelper` with exported `app/schemas/com.ayah.quran.core.database.AyahDatabase/3.json`.
   - Inserts sample `surahs`, `ayahs`, `bookmarks`, `reading_progress`, `wallpaper_history`, `selection_history` into v3 DB -> runs `Migration_3_4` -> asserts **100% of pre-existing user rows survive intact** AND all new companion tables + `hadith_fts` exist and accept reads/writes.
10. **`CompanionDaoInstrumentedTest`**:
    - Tests `AdhkarDao`, `TasbihDao`, `HadithDao` (including FTS4 diacritic-normalized Arabic + English queries, FK cascade integrity, idempotent content re-import preserving user favorites/progress/custom counters).

### D. WorkManager, Notification & Share Card Tests (`app/src/androidTest/`)
11. **`CompanionReminderSchedulingTest`**:
    - Uses `WorkManagerTestInitHelper` (`androidx-work-testing`) to verify enabling schedules unique work (`ExistingWorkPolicy.REPLACE` — zero duplicates on repeated calls), disabling cancels unique work, and `PrayerTimeManager` alarm state is completely untouched.
12. **`CompanionShareCardRendererTest`**:
    - Renders Arabic RTL and English LTR Hadith/Dhikr cards across all 3 Direction D themes; asserts non-null bitmap dimensions, non-blank pixels, and zero OOM on long texts.

### E. Compose UI, RTL, Accessibility & Performance Tests (`app/src/androidTest/`)
13. **`CompanionUiTest`**:
    - Navigates from Home Companion Strip -> Adhkar Hub -> Morning Session -> tap counter -> Undo -> navigate back -> reopen -> **asserts count persisted** (directly testing the defect fixed from the reference repo!).
    - Navigates to Tasbih -> increment -> Undo -> Reset opens `ConfirmDialog` -> confirm resets to `0`.
    - Navigates to Duas -> Quranic Duas -> taps Open in Mushaf -> verifies Mushaf screen opens at exact Surah/Ayah.
    - Navigates to Hadith -> searches Arabic word without tashkeel -> opens detail -> asserts grade & grader attribution visible -> toggles favorite -> asserts appears in Saved tab.
14. **Accessibility & Localization Visual Verification Gate (Per Phase)**:
    - Capture & visually inspect screenshots in **AR RTL Light**, **AR RTL Dark**, **EN LTR Light**, **`1.5x` font scale**, and **reduced motion (`animator_duration_scale = 0`)**.
    - Verify `>= 48dp` touch targets, TalkBack semantics on Tasbih/Adhkar counters, and WCAG AA contrast ratios via `AyahThemeContrastTest`.

---

## 3. Performance Budgets

| Operation | Target Budget | Verification Method |
| :--- | :--- | :--- |
| Cold-start overhead when companion data already imported | `< 5 ms` | DataStore version check skip |
| First-launch companion JSON import (`adhkar` + `quranic_duas` + `hadith_starter`) | `< 400 ms` off main thread | Single Room transaction benchmark in instrumented test |
| Offline FTS4 Hadith / Adhkar search query | `< 20 ms` | Instrumented DAO query timing |
| Tasbih tap-to-UI-frame + Room persist | `< 16 ms` UI frame (zero main-thread DB blocking) | `Dispatchers.IO` write + immediate optimistic state flow |
| APK size increase (bundled starter datasets) | `<= 2.2 MB` compressed | Compare `app-release.apk` before & after |
