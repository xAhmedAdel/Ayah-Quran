# 09 — Phased Implementation Roadmap

Each phase is self-contained, reviewable, and gated by explicit user approval, passing tests, and visual verification screenshots before advancing.

---

## Phase Dependency Graph

```text
C-1 (Data, Schema v4, Normalizer, Importer & Migration Tests)
 ├──► C-2 (Adhkar Hub, Guided Sessions & Unified Duas Hub)
 ├──► C-3 (One-Handed Digital Tasbih & Custom Counters)
 └──► C-4 (Hadith Library, Detail, Search, Favorites & Share Cards)
       └──► C-5 (Home Companion Strip, Daily Reflection & Optional Reminders)
             └──► C-6 (Hardening, Accessibility Audit, Full Regression & Release)
```

---

## Phase C-1: Data Foundation, Room Migration (`v3 -> v4`) & Verified Content Pipeline

- **Scope**: Zero UI changes. Establish verified offline datasets, `ArabicTextNormalizer`, Room `v3 -> v4` schema + `Migration_3_4`, DAOs, repositories, `CompanionContentImporter`, and integrity/migration test suites.
- **User-Facing Functionality**: None yet (invisible foundation; app behaves identically to `v1.0.0-beta.9`).
- **Architecture & Database Changes**:
  - `AyahDatabase` bumped `v3 -> v4` with explicit `Migration_3_4` (all 10 companion tables + `hadith_fts`). Export `4.json`.
  - Add `core/text/ArabicTextNormalizer.kt`, `data/companion/CompanionContentImporter.kt`, `di/CompanionModule.kt`.
- **Content Requirements**:
  - Freeze and verify `assets/companion/adhkar_hisn.json`, `assets/companion/quranic_duas.json`, `assets/companion/hadith_starter.json` (HadeethEnc.com snapshot with intact version metadata + 40 Nawawi).
- **Permissions**: None.
- **Tests**: `AyahDatabaseMigrationTest` (`3.json -> v4`), `AdhkarDatasetIntegrityTest`, `QuranicDuasIntegrityTest`, `HadithDatasetIntegrityTest`, `ArabicTextNormalizerTest`, `CompanionDaoInstrumentedTest`.
- **Risks**: Unverified English translations slipping into JSON; schema drift. Mitigated by CI integrity tests & license checklist.
- **Acceptance Criteria**: All 163 existing JVM tests + new integrity/migration tests green; `Migration_3_4` preserves 100% of v3 user data; first-launch import `< 400ms` off main thread.
- **Estimated Complexity**: Medium-High.
- **Dependencies**: User approval of Phase 0 decisions (licensing + navigation option).

---

## Phase C-2: Adhkar Hub, Guided Sessions & Unified Duas Hub

- **Scope**: Deliver complete **Adhkar Hub**, **Guided Adhkar Session** (persistent daily progress, Undo, auto-advance, Daily Sunnah Surahs Mushaf links), and **Unified Duas Hub** (Quranic Duas joined live from `AyahEntity` + Prophetic + Situational Duas).
- **User-Facing Functionality**:
  - Time-of-day recommended Adhkar session (`Morning`, `Evening`, `After Prayer`, `Sleep`, `Waking`).
  - Guided `HorizontalPager` session with tap counter, Undo, Reset (`ConfirmDialog`), Favorite, Copy, and optional list view toggle.
  - Daily Sunnah Surahs (`Al-Kahf`, `Al-Mulk`, `As-Sajdah`) deep-linking directly to `MushafPageReaderScreen`.
  - Duas Hub (`Quranic | Prophetic | By Situation`), Arabic/English diacritic-normalized search, Favorites, and `Open in Mushaf` for Quranic Duas.
  - Contextual `"After-Prayer Adhkar"` chip on `PrayerTimesScreen`.
- **UI/UX & Navigation**: `AdhkarHubScreen`, `AdhkarSessionScreen`, `DuasHubScreen`; routes `companion/adhkar`, `companion/adhkar/session/{categoryId}`, `companion/duas`.
- **Database / Permissions**: Uses C-1 tables; zero new permissions.
- **Tests**: `AdhkarSessionViewModelTest`, `DuasViewModelTest`, Compose UI persistence & Mushaf deep-link tests, AR Light/Dark + EN Light screenshots.
- **Risks**: Accidental progress loss on configuration change or back navigation. Mitigated by immediate Room persistence + `rememberSaveable` pager page.
- **Acceptance Criteria**: Session progress survives process death/back nav, resets cleanly on next day; Quranic Duas display exact Uthmani text and open exact Mushaf page; zero clipping at `1.5x` font scale.
- **Estimated Complexity**: Medium-High.
- **Dependencies**: Phase C-1.

---

## Phase C-3: One-Handed Digital Tasbih

- **Scope**: Independent one-handed **Digital Tasbih** with built-in presets, custom Dhikr/Salawat creation & editing, configurable haptic & optional soft tick sound, Undo (`-1`), confirmation-guarded Reset, and persistent Room counters.
- **User-Facing Functionality**:
  - Full lower-thumb tactile counting surface with circular progress arc and tabular numbers.
  - Preset switcher (`SubhanAllah 33`, `Alhamdulillah 33`, `Allahu Akbar 33`, `Astaghfirullah 100`, `Salawat 10`, Free Count) + `+ Custom Dhikr` sheet (`1..10000` target).
  - Quick `"Next Preset"` flow for effortless `33 + 33 + 33` post-prayer Tasbih.
  - Direct launch link from high-count Adhkar items (`>= 33`).
- **UI/UX & Navigation**: `TasbihScreen`, `TasbihTapSurface`, `TasbihViewModel`; route `companion/tasbih?counterId={counterId}`.
- **Database / Permissions**: Uses `tasbih_counters` & `tasbih_daily_log` from C-1; uses Compose `HapticFeedback` (no dangerous permissions).
- **Tests**: `TasbihViewModelTest` (increment, round rollover, undo clamp, reset confirm, custom CRUD), Compose UI test, reduced-motion + TalkBack semantics verification, screenshots.
- **Risks**: Rapid tapping dropping counts or janking UI. Mitigated by atomic Room `UPDATE` on `Dispatchers.IO` with immediate UI state flow.
- **Acceptance Criteria**: Zero dropped taps at rapid 8 taps/sec; counts survive app kill; TalkBack announces count/target clearly; comfortable one-handed reachability.
- **Estimated Complexity**: Medium.
- **Dependencies**: Phase C-1.

---

## Phase C-4: Hadith Library, Search, Favorites, Daily Hadith & Shareable Cards

- **Scope**: Complete **Hadith Library** (`40 Hadith An-Nawawi` + curated `HadeethEnc.com` thematic collections), FTS4 offline search, Hadith Detail with verified grading + grader attribution + collapsible `Sharh`, Favorites, Reading History, Daily Hadith selector, and Direction D **Shareable Cards** (shared across Hadith, Dhikr, and Duas!).
- **User-Facing Functionality**:
  - Browse by collection/chapter, filter by `Saved` and `Recent`, search without tashkeel.
  - Read Arabic Matn + verified English translation + grading badge + grader + HadeethEnc version attribution + collapsible explanation.
  - Share any Hadith, Dhikr, or Dua as a Sanctuary Emerald / Warm Parchment PNG card via native Android Share Sheet (`FileProvider`).
  - Update `SettingsScreen` Licenses sheet with HadeethEnc.com, Hisn al-Muslim, and MIT dataset attributions.
- **UI/UX & Navigation**: `HadithLibraryScreen`, `HadithCollectionScreen`, `HadithDetailScreen`, `CompanionShareCardSheet`; routes `companion/hadith`, `companion/hadith/collection/{id}`, `companion/hadith/detail/{hadithId}`.
- **Database / Permissions**: Uses C-1 `hadith_*` tables + `hadith_fts`; add `<cache-path name="shared_cards" .../>` in `res/xml/file_paths.xml`; zero new permissions.
- **Tests**: `HadithLibraryViewModelTest`, `DailyHadithSelectorTest`, `CompanionShareCardRendererTest`, Compose FTS search + attribution + favorite UI test, screenshots.
- **Risks**: Text overflow on long Hadiths in share cards. Mitigated by adaptive font-fit shrinking (same algorithm as `WallpaperRenderer`) and clean truncation/multi-line rules.
- **Acceptance Criteria**: Sub-20ms offline Arabic/English search; every Hadith shows explicit grade, grader, and dataset version; share cards render cleanly in AR RTL and EN LTR without clipping.
- **Estimated Complexity**: High.
- **Dependencies**: Phase C-1 (and optionally C-2 so Share Card works across Adhkar/Duas immediately).

---

## Phase C-5: Home Companion Integration & Optional Daily Reminders

- **Scope**: Surface Companion seamlessly on Home without visual bloat + add opt-in, quiet-hours-aware **Daily Companion Reminders** inside Settings Hub.
- **User-Facing Functionality**:
  - **Home Companion Strip**: 4 compact tiles (`Adhkar` with live time-of-day progress badge, `Tasbih`, `Hadith`, `Duas`).
  - **Daily Reflection Card**: Clean `[ Daily Ayah | Daily Hadith ]` switcher inside existing Daily Ayah card footprint (Daily Hadith toggleable in Settings).
  - **Companion Reminders (`Settings -> Daily Reminders`)**: Opt-in toggles (all OFF by default) for Morning Adhkar, Evening Adhkar, Daily Hadith, Daily Reading, Friday Al-Kahf + Quiet Hours window + permission banner if notifications denied.
  - Smart completion suppression: skips Morning/Evening reminder if user already completed that session today.
- **Architecture & Background**: `CompanionReminderWorker` (`@HiltWorker`), dedicated `"ayah_companion_reminders"` channel (IDs `7001..7005`), WorkManager `ExistingWorkPolicy.REPLACE`, boot/time reschedule hook. **Zero touch to `PrayerTimeManager` / `AdhanPlaybackService`.**
- **Permissions**: Reuses existing `POST_NOTIFICATIONS` runtime check gracefully; zero new manifest permissions.
- **Tests**: `CompanionReminderPolicyTest`, `CompanionReminderSchedulingTest` (`work-testing`), `HomeViewModelTest` updates, `AppUiTest` verification, AR/EN Light/Dark screenshots.
- **Risks**: Reminder interference with Adhan alarms or duplicate notifications. Mitigated by separate WorkManager pipeline, separate channel/IDs, and `REPLACE` policy.
- **Acceptance Criteria**: Home stays calm and passes `AppUiTest`; reminders never fire when disabled, during quiet hours, or after completion; Adhan scheduling tests 100% green.
- **Estimated Complexity**: Medium.
- **Dependencies**: Phases C-2, C-3, C-4.

---

## Phase C-6: Hardening, Optional On-Demand Hadith Books Evaluation, Full Regression & Release

- **Scope**: End-to-end polish, `1.5x` font scale + reduced-motion + TalkBack audit across all new screens, APK size & memory verification, full JVM + full instrumented regression suite, signed release build & changelog.
- **Optional Scope (Only if Approved)**: On-demand HTTPS downloadable full classical Arabic books (`Sahih al-Bukhari`, `Sahih Muslim`, `Riyad as-Salihin`) with SHA-256 verification and Storage Manager deletion.
- **Acceptance Criteria**: 100% JVM + 100% instrumented regression green; APK delta within budget (`<= 2.2 MB` for core); signed release smoke-tested on emulator; full final report + screenshots archived.
- **Estimated Complexity**: Low-Medium.
- **Dependencies**: Phase C-5.
