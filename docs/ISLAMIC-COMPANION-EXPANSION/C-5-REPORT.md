# AYAH — Phase C-5 Report: Home Companion, Daily Reflection & Smart Reminders

- **Date:** 2026-11-20
- **Branch:** `main`
- **Base C-4 Commit:** `e1f353b7aa37689a807dddc26d9071cada783b67`
- **App Version:** `1.0.0-beta.9` (`versionCode = 10` — unchanged)
- **Database Schema:** Room `v4` (`4.json` — unchanged; zero schema migration needed)
- **Push / Release Status:** Local commits ONLY (not pushed, no release tag created)
- **Phase Boundary:** Phase C-5 ONLY. Phase C-6 (hardening / release prep), Share Cards, Adhkar Audio, external downloads, new bottom tabs, and Prayer/Adhan changes have **NOT** been started.

---

## 1. Files Changed & Architecture Decisions

### New Production Files
- `app/src/main/java/com/ayah/quran/domain/model/companion/DailyReflectionSelector.kt` — Pure deterministic daily index selector using golden-prime modular stride guaranteeing non-repetition across consecutive local days for every `poolSize >= 2`.
- `app/src/main/java/com/ayah/quran/domain/model/companion/ReminderModels.kt` — `CompanionReminderType` (`MORNING_ADHKAR`, `EVENING_ADHKAR`, `DAILY_REFLECTION`) with unique WorkManager names (`companion_reminder_*`), stable notification IDs (`4101`, `4102`, `4103`), default times (`07:00`, `17:30`, `09:00`), and deep-link routes; `CompanionReminderConfig`; `CompanionRemindersPreferences`; and `CompanionReminderTimeCalculator` (`ZonedDateTime` local delay computation handling DST and timezone changes).
- `app/src/main/java/com/ayah/quran/domain/repository/companion/CompanionRemindersRepository.kt` — Domain interfaces `CompanionRemindersRepository` and `CompanionReminderScheduler`.
- `app/src/main/java/com/ayah/quran/data/companion/DataStoreCompanionRemindersRepository.kt` — DataStore-backed persistence for each reminder's enabled state, hour, minute, and `permissionRequestedBefore` flag (all reminders default to disabled; zero Room schema changes).
- `app/src/main/java/com/ayah/quran/data/companion/WorkManagerCompanionReminderScheduler.kt` — WorkManager scheduler using `OneTimeWorkRequest` + `ExistingWorkPolicy.REPLACE` per unique work name so updating time cleanly replaces prior work without duplicates and disabling cancels only that reminder's unique work.
- `app/src/main/java/com/ayah/quran/background/CompanionReminderNotifier.kt` — Isolated silent notification channel (`ayah_companion_reminders`, no automatic audio), runtime `POST_NOTIFICATIONS` / system-toggle guard, `AppLocale.wrap(context)` Arabic/English localization, and `PendingIntent` carrying `EXTRA_COMPANION_ROUTE`.
- `app/src/main/java/com/ayah/quran/background/CompanionReminderWorker.kt` — `@HiltWorker` checking persisted enabled state, posting notification if allowed, and rescheduling the next local day's occurrence.
- `app/src/main/java/com/ayah/quran/background/CompanionReminderSystemReceiver.kt` — Isolated `BroadcastReceiver` reconciling enabled companion reminders across `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `DATE_CHANGED`, and `MY_PACKAGE_REPLACED`.
- `app/src/main/java/com/ayah/quran/ui/settings/CompanionRemindersViewModel.kt` — ViewModel managing independent reminder toggles, time updates, permission status refresh, and scheduler calls.
- `app/src/main/java/com/ayah/quran/ui/settings/CompanionRemindersSection.kt` — Native Direction D `Daily Reminders` section embedded inside the existing `SettingsScreen` (3 independent rows with switches + Material 3 `TimePicker` dialogs, explanation dialog before first runtime permission request on API 33+, non-nagging denied-permission `AyahBannerStrip` with 1-tap Android Notification Settings shortcut).

### Modified Production Files
- `app/src/main/java/com/ayah/quran/core/time/CompanionDateProvider.kt` — Added `todayEpochDay(): Long` using `ZoneId.systemDefault()`.
- `app/src/main/java/com/ayah/quran/data/companion/CompanionRepositoriesImpl.kt` — Wired `DailyReflectionSelector.selectIndex(epochDay, ids.size)` into `HadithRepositoryImpl.dailyHadithId(epochDay)`.
- `app/src/main/java/com/ayah/quran/di/CompanionModule.kt` — Bound `CompanionRemindersRepository` and `CompanionReminderScheduler`.
- `app/src/main/java/com/ayah/quran/ui/home/HomeViewModel.kt` — Extended `HomeUiState` with `DailyReflectionMode` (`AYAH` / `HADITH`), `HomeDailyHadithState`, `dailyAyahReady` / `dailyHadithReady` honest availability flags, `selectReflectionMode(...)`, and `refreshDay()` on local calendar date rollover.
- `app/src/main/java/com/ayah/quran/ui/home/HomeScreen.kt` — Added compact 4-entry `HomeCompanionStrip` (adapts to 2×2 grid when `fontScale >= 1.3f`) and upgraded `DailyAyahCard` into `DailyReflectionCard` (preserving `home_daily_ayah_card` tag for existing regression compatibility, rendering COMPLETE unmodified Ayah / Hadith text, direct Mushaf & Hadith Detail actions, and honest unavailable states).
- `app/src/main/java/com/ayah/quran/ui/settings/SettingsScreen.kt` — Added `CompanionRemindersSection()` into the existing Settings Hub.
- `app/src/main/java/com/ayah/quran/MainActivity.kt` & `app/src/main/java/com/ayah/quran/navigation/AyahApp.kt` — Added notification deep-link route capture (`onCreate` + `onNewIntent`) and wired the 4 Home Companion + Daily Reflection callbacks into `HomeScreen`. Five bottom tabs remain unchanged.
- `app/src/main/java/com/ayah/quran/AyahApplication.kt` — Created `ayah_companion_reminders` notification channel and reconciled already-enabled companion reminders on startup (zero permission prompts on startup).
- `app/src/main/AndroidManifest.xml` — Registered isolated `CompanionReminderSystemReceiver`.
- `app/src/main/res/values/strings.xml` & `app/src/main/res/values-ar/strings.xml` — Added 33 English + 33 Arabic strings.

### New Test Files
- `app/src/test/java/com/ayah/quran/domain/model/companion/DailyReflectionSelectorTest.kt` — 3 JVM unit tests (determinism, 365-day consecutive non-repetition across pool sizes 2..6236, single-item bound).
- `app/src/test/java/com/ayah/quran/ui/settings/CompanionRemindersViewModelTest.kt` — 4 JVM unit tests (same-day/next-day/DST delay calculation, independent enable/disable/time update without duplicates, preference & permission restoration across ViewModel recreation, unique work names/notification IDs/deep-link routes).
- `app/src/androidTest/java/com/ayah/quran/ui/HomeCompanionAndRemindersUiTest.kt` — 3 Compose instrumented UI tests (Home Companion 4 entries & destinations with 5 tabs intact, Daily Reflection mode switching + Mushaf & Hadith Detail navigation, Settings Reminders section & time picker without startup permission prompts).
- `app/src/androidTest/java/com/ayah/quran/data/companion/CompanionReminderSchedulerInstrumentedTest.kt` — 2 Android WorkManager instrumented tests (real WorkManager enqueue, replace-without-duplicates on time change, independent cancel, and exact deep-link intent extras).

---

## 2. Feature Architecture & Compliance Summary

### Home Companion Strip
- Compact 4-tile Direction D strip (`Adhkar` -> `Routes.ADHKAR_HUB`, `Tasbih` -> `Routes.TASBIH`, `Hadith` -> `Routes.HADITH_LIBRARY`, `Duas` -> `Routes.DUAS_HUB`) placed cleanly inside existing `HomeScreen`.
- Automatically switches from a 4-column row to a 2×2 grid at `fontScale >= 1.3f` so zero labels clip or truncate at `1.5x` scaling.
- Existing 5 bottom navigation tabs (`Home`, `Quran`, `Prayer`, `Audio`, `Wallpapers`) remain unchanged.

### Daily Reflection (Ayah / Hadith)
- Compact `SingleChoiceSegmentedButtonRow` switches cleanly between **Daily Ayah** and **Daily Hadith**.
- **Daily Ayah:** Deterministic `AyahSelector.selectDaily(epochDay, pool.size)` over verified Tanzil Uthmani `ayahs` (6,236 verses), displaying complete unmodified Arabic text, Surah name + `surah:ayah`, direct `Open in Mushaf` action, and existing `Create Wallpaper` action.
- **Daily Hadith:** Deterministic `DailyReflectionSelector.selectIndex(epochDay, ids.size)` over C-1's 202 verified Hadiths, displaying complete unmodified Arabic text, licensed English translation only when present, collection/number, exact grade badge, grader/source attribution, and direct `Read Hadith` navigation to `HadithDetailScreen`.
- **Stability:** Uses local `ZoneId.systemDefault()` epoch day refreshed on `ON_RESUME`; stable across recomposition, navigation, and process termination (`Al-Israa · 17:41` verified identical before and after `am force-stop`), changes on next local calendar day without consecutive-day repetition.

### Smart Reminders & Notification Permissions
- 3 optional, independent, off-by-default reminders: **Morning Adhkar** (`07:00`), **Evening Adhkar** (`17:30`), **Daily Reflection** (`09:00`).
- **Zero startup permission prompt:** No runtime permission prompt on install or app launch.
- **API 33+ runtime permission:** Requested ONLY after the user explicitly enables a reminder, preceded by an in-app explanation dialog (`ConfirmDialog`). If denied, never loops prompts; displays a calm warning `AyahBannerStrip` with 1-tap access to Android Notification Settings without blocking any app functionality.
- **WorkManager scheduling:** Self-rescheduling `OneTimeWorkRequest` with `ExistingWorkPolicy.REPLACE` per unique work name + `CompanionReminderSystemReceiver` for reboot/time/timezone/date/package-update reconciliation. Silent notification channel (`setSound(null, null)`, `setSilent(true)` — zero automatic audio).

### Evidence of Strict Prayer/Adhan Isolation
- `git diff -- app/src/main/java/com/ayah/quran/prayer/ app/src/main/java/com/ayah/quran/ui/prayer/ app/src/main/java/com/ayah/quran/data/prayer/` returned **empty (0 files changed, 0 lines changed)**.
- `PrayerTimeManager`, `AlarmPrayerAlarmScheduler`, `PrayerAlarmReceiver`, `PrayerSystemReceiver`, `PrayerNotifications`, `AdhanPlaybackService`, and `PrayerSettingsScreen` were not modified.

---

## 3. Verification & Full Regression Results

### Build & JVM Unit Tests
- Command: `.\gradlew :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest`
- Final Gradle Result: **`BUILD SUCCESSFUL in 3m 24s`** (single uninterrupted run, zero crashes)
- JVM Unit Tests: **207 / 207 passed**, **0 failed**, **0 skipped** (200 pre-existing/C-1..C-4 + 7 new C-5 unit tests).

### Complete Android Instrumented Regression Suite
> **Validation Environment Disclosure:** All instrumented tests and visual captures were executed on the **`ayah_avd` Android Emulator (Android 16 / API 36, 1080×2400)**, NOT a physical Android device.

- Exact XML Counts (`TEST-ayah_avd(AVD) - 16-_app-.xml`):
  - **Total:** 78
  - **Passed:** 77 (including 3/3 new `HomeCompanionAndRemindersUiTest`, 2/2 new `CompanionReminderSchedulerInstrumentedTest`, 2/2 `HadithUiTest`, 2/2 `TasbihUiTest`, 2/2 `AdhkarAndDuasUiTest`, 3/3 `PrayerTimesUiTest`, `AppUiTest`, `MushafPageReaderUiTest`, `AudioTabUiTest`, `CompanionDaoInstrumentedTest`, `AyahDatabaseMigrationTest`, `NotificationAndDownloadInstrumentedTest`)
  - **Failed:** 0
  - **Skipped:** 1 (`com.ayah.quran.ui.PrayerPermissionsInstrumentedTest.notificationDenialDisablesPostingPath` — pre-existing API-gated skip)
  - **Aborted:** 0

### Emulator Visual Validation (`docs/ISLAMIC-COMPANION-EXPANSION/screenshots/c-5/`)
All 7 screenshots were captured on `ayah_avd` (API 36) and visually inspected with zero defects:
1. `docs/ISLAMIC-COMPANION-EXPANSION/screenshots/c-5/01_home_companion_ar_light.png` — Home Companion 4-tile strip + Daily Reflection Ayah mode (AR Light RTL, 5 bottom tabs preserved).
2. `docs/ISLAMIC-COMPANION-EXPANSION/screenshots/c-5/02_home_reflection_hadith_ar_light.png` — Daily Reflection switched to Daily Hadith mode (`#3475`, `صحيح`, complete Arabic + verified English).
3. `docs/ISLAMIC-COMPANION-EXPANSION/screenshots/c-5/03_settings_reminders_ar_light.png` — Settings Hub `التذكيرات اليومية` section (3 independent reminders off by default, clean Direction D cards).
4. `docs/ISLAMIC-COMPANION-EXPANSION/screenshots/c-5/04_home_companion_ar_dark.png` — Home Companion Strip + Daily Reflection in Dark Mode (Obsidian Moss + Antique Gold).
5. `docs/ISLAMIC-COMPANION-EXPANSION/screenshots/c-5/05_home_companion_en_light.png` — Home Companion Strip + Daily Reflection in English LTR (`Al-Israa · 17:41`, `Open in Mushaf`).
6. `docs/ISLAMIC-COMPANION-EXPANSION/screenshots/c-5/06_settings_reminders_en_light.png` — Settings Hub `Daily Reminders` section in English LTR.
7. `docs/ISLAMIC-COMPANION-EXPANSION/screenshots/c-5/07_home_companion_font150.png` — Home Companion adaptive 2×2 grid at `font_scale = 1.5` (zero text clipping or overlap).

---

## 4. Known Limitations & Unresolved Issues
- **Physical Device Pending:** Verified on `ayah_avd` emulator (API 36); physical-device notification delivery and OEM battery behavior testing remains for user verification after review.
- **Inexact WorkManager Delivery:** As designed for gentle companion reminders (strictly separated from exact Prayer/Adhan alarms), WorkManager respects Doze/App Standby windows without requesting exact-alarm privileges.

---

## 5. Explicit Exclusions & Completion Gate Confirmation
- **Untouched / Not Started:** Phase C-6 (hardening / release prep), Share Cards, Adhkar Audio, new bottom tabs, new Hadith/Quran datasets, external downloads, Prayer/Adhan logic, Room schema (`v4`), and app version (`1.0.0-beta.9`).
- **Stopping at C-5 Completion Gate.** Local commits only; not pushed, not released. Awaiting review and explicit C-6 authorization.
