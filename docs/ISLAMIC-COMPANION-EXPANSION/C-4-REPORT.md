# AYAH — Phase C-4 Report: Premium Hadith Library

- **Date:** 2026-11-20
- **Branch:** `main`
- **Base C-3 Commit:** `df9adf4d695d70ec9bcce2a39bd61e48903f9624`
- **App Version:** `1.0.0-beta.9` (`versionCode = 10` — unchanged)
- **Database Schema:** Room `v4` (`4.json` — unchanged; zero schema migration needed)
- **Push / Release Status:** Local commits ONLY (not pushed, no release tag created)
- **Phase Boundary:** Phase C-4 ONLY. Phase C-5 (Home Companion Strip, Daily Reflection switcher, Reminders), Share Cards, Adhkar Audio, and external Hadith downloads have **NOT** been started.

---

## 1. Existing Hadith Data Inspected & Reused

Zero new datasets or storage systems were introduced. C-4 strictly reuses C-1's verified Room `v4` tables (`hadith_collections`, `hadith_items`, `hadith_fts`, `hadith_user_state`) populated from `app/src/main/assets/companion/hadith_starter.json`:

- **Total Records:** `202` verified Hadiths across `6` collections (0 empty Arabic texts; longest Arabic Hadith: `1,822` characters).
- **`nawawi40` (Forty Hadith of An-Nawawi — `42` Hadiths):** Verbatim classical Arabic from `fawazahmed0/hadith-api` (`ara-nawawi`, The Unlicense + Public Domain classical Arabic). Zero fabricated English translations (`textEn = null`), zero fabricated explanations, grade honestly reported as `"مُبيَّن في متن الإمام النووي"` / `"Stated within Imam An-Nawawi's text"`, grader `"Imam An-Nawawi (source text)"`, version `fawazahmed0-ara-nawawi-unlicense-2026-11-20`.
- **5 HadeethEnc.com Collections (`160` bilingual AR + EN Hadiths):**
  - `hadeethenc_quran` — The Noble Quran & Its Sciences (`30`)
  - `hadeethenc_aqeedah` — Creed & Faith (`30`)
  - `hadeethenc_worship` — Worship & Jurisprudence (`30`)
  - `hadeethenc_virtues` — Virtues, Manners & Dhikr (`40`)
  - `hadeethenc_seerah` — Prophetic Biography & History (`30`)
  - Each retains exact unmodified Arabic & English text, verified grading (`صحيح`, `حسن`, `صحيح بشواهده`, `ضعيف`, etc.), scholarly `Sharh` explanation (`160/160`), explicit `HadeethEnc.com` attribution, and version `hadeethenc-api-v1-2026-11-20`.

---

## 2. Files Changed & Architecture Decisions

### New Production Files
- `app/src/main/java/com/ayah/quran/ui/companion/hadith/HadithLibraryViewModel.kt` — Reactive ViewModel combining Room `HadithRepository` Flows + `CompanionContentImporter.importState` (debounced 150 ms hybrid FTS4 + normalized Arabic/English search, collection filtering via `flatMapLatest`, favorites-only mode synced reactively into search results, explicit loading/error/retry states).
- `app/src/main/java/com/ayah/quran/ui/companion/hadith/HadithDetailViewModel.kt` — Detail ViewModel observing `observeById(hadithId)` + collection metadata, recording reads via `hadithRepository.recordRead(hadithId)`, toggling favorites, and clamping reader font scale (`0.85f..1.4f`).
- `app/src/main/java/com/ayah/quran/ui/companion/hadith/HadithSharedUi.kt` — Localization helpers, honest grade-to-badge mapping (`صحيح`/`Authentic` -> `SUCCESS`, `حسن`/`Hasan` -> `GOLD`, `ضعيف`/`Weak` -> `WARNING`, source-embedded/other -> `NEUTRAL`), and clipboard copy with source attribution.
- `app/src/main/java/com/ayah/quran/ui/companion/hadith/HadithLibraryScreen.kt` — Direction D Hadith Library screen (`TopAppBar` with Favorites toggle, `AyahSearchBar` with keyboard dismiss on Search IME action, verified collection `FilterChip` row, lazy list of `HadithSummaryCard`s showing collection, `#number`, grade badge, Arabic excerpt, licensed English excerpt in EN mode, bookmark toggle with snackbar feedback, and dedicated Loading / Error / Empty Favorites / No Search Results states).
- `app/src/main/java/com/ayah/quran/ui/companion/hadith/HadithDetailScreen.kt` — Calm Direction D reading screen on `mushafCanvas` (`SelectionContainer` for full unmodified Arabic text, verified English translation shown ONLY when present, LTR-isolated single-line `A-`/`A+` controls, collapsible Scholarly Explanation / `Sharh` when present, and full `Source & Verification` `AyahGroupCard` displaying Collection, Chapter, Number, exact Grade, Grader Attribution, Reference, Source Attribution, and Dataset Version).

### Modified Production Files
- `app/src/main/java/com/ayah/quran/core/database/dao/companion/HadithDao.kt` — Added `observeAll()` (`INNER JOIN hadith_collections` ordered stably by `c.sortOrder ASC, h.sortOrder ASC`). Zero schema change.
- `app/src/main/java/com/ayah/quran/domain/repository/companion/CompanionRepositories.kt` — Added `observeAll(): Flow<List<HadithItem>>` to `HadithRepository`.
- `app/src/main/java/com/ayah/quran/data/companion/CompanionRepositoriesImpl.kt` — Implemented `observeAll()`.
- `app/src/main/java/com/ayah/quran/navigation/AyahApp.kt` — Added `Routes.HADITH_LIBRARY` and `Routes.HADITH_DETAIL`; kept `AudioMiniPlayer` visible on `HADITH_LIBRARY` and hidden on `HADITH_DETAIL` for distraction-free reading without affecting audio playback; preserved the 5 bottom navigation tabs unchanged.
- `app/src/main/java/com/ayah/quran/ui/companion/adhkar/AdhkarHubScreen.kt` — Added `Hadith Library` Companion entry card (`adhkar_hadith_card`).
- `app/src/main/res/values/strings.xml` — Added 34 English strings.
- `app/src/main/res/values-ar/strings.xml` — Added 34 matching Arabic strings.

### New & Modified Test Files
- `app/src/test/java/com/ayah/quran/ui/companion/hadith/HadithViewModelTest.kt` — 6 JVM unit tests covering list loading without duplicates, collection filtering, Arabic & English search + no-results state, favorites persistence across ViewModel recreation & reactivity inside search, exact detail text/grade/attribution preservation & Nawawi null-English integrity, grade badge mapping (`Sahih`/`Hasan`/`Da'if`/source-text), and error/retry state.
- `app/src/test/java/com/ayah/quran/navigation/MiniPlayerVisibilityTest.kt` — Added assertions for `Routes.HADITH_LIBRARY` (`true`) and `Routes.HADITH_DETAIL` (`false`).
- `app/src/androidTest/java/com/ayah/quran/ui/HadithUiTest.kt` — 2 end-to-end Compose instrumented tests covering Companion navigation -> Hadith Library -> collection filtering -> Arabic (`النيات`) & English (`Quran`) search -> no-results empty state -> bilingual Hadith Detail verbatim text, English translation, grade badge, `HadeethEnc.com` attribution -> Favorites toggle & filtering.

---

## 3. Content Integrity & Licensing Verification

- Only C-1's verified bundled `hadith_starter.json` (`202` records) is used; zero new datasets, zero external downloads, zero audio added.
- Arabic (`textAr`) and licensed English (`textEn`) texts are rendered verbatim on `HadithDetailScreen` with zero shortening, paraphrasing, or fabrication.
- `nawawi40` Hadiths omit the English translation section cleanly because no licensed English translation is bundled, and display their exact neutral grade (`مُبيَّن في متن الإمام النووي` / `Stated within Imam An-Nawawi's text`) rather than claiming `Sahih`.
- `HadeethEnc.com` Hadiths display exact grades (including `WARNING` styling for `ضعيف` when present), `Graded by: HadeethEnc.com`, `Source Attribution: HadeethEnc.com — Encyclopedia of Translated Prophetic Hadiths`, and `Dataset Version: hadeethenc-api-v1-2026-11-20`.

---

## 4. Verification & Regression Results

### Build & JVM Unit Tests
- Command: `.\gradlew :app:testDebugUnitTest :app:assembleDebug`
- Result: **`BUILD SUCCESSFUL`**
- JVM Unit Tests: **200 / 200 passed**, **0 failed**, **0 skipped** (194 pre-existing/C-1/C-2/C-3 + 6 new C-4 unit tests).

### Complete Android Instrumented Regression Suite
> **Validation Environment Disclosure:** All instrumented tests, process-kill checks, and visual captures were executed on the **`ayah_avd` Android Emulator (Android 16 / API 36, 1080×2400)**, NOT a physical Android device.

- Command: `.\gradlew :app:connectedDebugAndroidTest`
- Result: **`BUILD SUCCESSFUL in 4m 51s`** (single uninterrupted run, zero crashes)
- Exact XML Counts (`TEST-ayah_avd(AVD) - 16-_app-.xml`):
  - **Total:** 73
  - **Passed:** 72 (including 2/2 new `HadithUiTest`, 2/2 `TasbihUiTest`, 2/2 `AdhkarAndDuasUiTest`, 3/3 `PrayerTimesUiTest`, `CompanionDaoInstrumentedTest`, `AyahDatabaseMigrationTest`, `AppUiTest`, `MushafPageReaderUiTest`, `AudioTabUiTest`, `NotificationAndDownloadInstrumentedTest`)
  - **Failed:** 0
  - **Skipped:** 1 (`com.ayah.quran.ui.PrayerPermissionsInstrumentedTest.notificationDenialDisablesPostingPath` — pre-existing API-gated skip)
  - **Aborted:** 0

### Live Process-Death Favorites Persistence Verification
- Saved HadeethEnc Hadith `#5907` on emulator -> terminated app via `adb shell am force-stop com.ayah.quran.debug` -> relaunched `MainActivity` -> navigated to Hadith Library -> toggled Favorites filter -> `#5907` (`تعاهدوا هذا القرآن...`, `صحيح`) persisted and displayed immediately (`1 حديثاً`).

### Emulator Visual Validation (`docs/ISLAMIC-COMPANION-EXPANSION/screenshots/c-4/`)
All 7 screenshots were captured on `ayah_avd` (API 36) and visually inspected with zero layout, clipping, overflow, or contrast defects:
1. `docs/ISLAMIC-COMPANION-EXPANSION/screenshots/c-4/01_hadith_library_ar_light.png` — Hadith Library (AR Light RTL, `202 حديثاً`, verified collection chips, Nawawi neutral grade badge).
2. `docs/ISLAMIC-COMPANION-EXPANSION/screenshots/c-4/02_hadith_search_ar_light.png` — Bilingual search (`Quran` -> `30 حديثاً`, emerald `صحيح` badges, collection & number shown).
3. `docs/ISLAMIC-COMPANION-EXPANSION/screenshots/c-4/03_hadith_detail_ar_light.png` — Hadith Detail (AR Light RTL, `#5907`, `A-`/`A+`, `صحيح`, verbatim Arabic + verified English translation, collapsible `Sharh`, `Source & Verification` section).
4. `docs/ISLAMIC-COMPANION-EXPANSION/screenshots/c-4/04_hadith_favorites_ar_light.png` — Saved Favorites filter (`1 حديثاً`, verified after `am force-stop`).
5. `docs/ISLAMIC-COMPANION-EXPANSION/screenshots/c-4/05_hadith_detail_ar_dark.png` — Hadith Detail in true Dark Mode (Obsidian Moss + Antique Gold).
6. `docs/ISLAMIC-COMPANION-EXPANSION/screenshots/c-4/06_hadith_library_en_light.png` — Hadith Library in English LTR (`202 hadiths`, honest Nawawi grade, verbatim Arabic with no fabricated English).
7. `docs/ISLAMIC-COMPANION-EXPANSION/screenshots/c-4/07_hadith_detail_font150.png` — Hadith Detail at `font_scale = 1.5` (single-line `A-`/`A+`, clean vertical scroll, zero overflow).

---

## 5. Known Limitations & Unresolved Issues
- **Physical Device Pending:** Verified on `ayah_avd` emulator (API 36); physical device validation remains for user sign-off after review.
- **Nawawi 40 English Translations:** Intentionally absent because only the Public Domain / Unlicense classical Arabic text is bundled in C-1; no unverified translation was added.

---

## 6. Explicit Exclusions & Completion Gate Confirmation
- **Untouched / Not Started:** C-5 Home Companion Strip, Daily Reflection switcher, Reminders/Notifications, Share Cards, Adhkar Audio, new bottom tabs, external Hadith downloads, Quran text, Prayer/Adhan logic, and `versionName`/`versionCode` (`1.0.0-beta.9`).
- **Stopping at C-4 Completion Gate.** Local commits only; not pushed, not released. Awaiting review and explicit C-5 authorization.
