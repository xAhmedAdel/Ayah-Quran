# Phase 12 — Prayer Times & Adhan System

**Status: COMPLETE** — a fully integrated, offline prayer-times and adhan system inside Ayah: authentic calculation engine (independently cross-verified), manual + automatic location, exact-alarm scheduling with honest fallback, per-prayer adhan modes, background playback via a foreground service, localized notifications, and Hijri + Gregorian dates. Full regression green on the final code: **73 JVM + 52 instrumented = 125 tests, 0 failures; debug and release builds pass.**

---

## 1. Implementation summary

A new **الصلوات / Prayer times** top-level tab (6 tabs now) hosts the daily timetable; a dedicated settings screen (`prayer/settings` route via the gear icon) exposes location, calculation, adhan, notification and advanced sections. The system computes locally, schedules through AlarmManager, plays through a foreground Media3 service, and persists everything in DataStore — **zero Room schema changes, zero migrations**.

## 2. Calculation library & source

- **[adhan-java 1.2.1](https://github.com/BatoulApps/adhan-java)** (`com.batoulapps.adhan:adhan`, Maven Central), **MIT** (license verified on the repo; attribution added to `docs/LICENSES.md`). Fully offline — no network requests for calculations (no INTERNET permission exists in this app).
- Wrapped behind the domain interface `PrayerTimesCalculator` (`data/prayer/AdhanPrayerTimesCalculator.kt`) — the library can be swapped without touching UI/scheduling.

## 3. Supported calculation methods

Egyptian General Authority of Survey (**default**, per spec), Muslim World League, Umm Al-Qura, Karachi, ISNA (→ adhan NORTH_AMERICA), Dubai, Kuwait, Qatar, Singapore, Moonsighting Committee. Plus: Shafi'i/Hanafi Asr, three high-latitude rules, per-prayer minute adjustments (±120), timezone/DST handling (via java.time + the location's tz).

**Accuracy verification:** 25 fixtures (5 cities × 5 dates × 5 methods) captured from the independent **Aladhan API** and stored as a test fixture. `PrayerTimesCalculatorReferenceTest` asserts every prayer within **±2 minutes** — 150 comparisons, worst observed delta ≤ 2 min. Fixture-only network use (dev-time); the app itself never calls it.

## 4. Audio sources & licensing

**No adhan recordings are bundled** — redistribution rights could not be verified for any candidate catalog (same honest pattern as the Phase 5 Quran-audio foundation). Instead:
- The verified-catalog section shows a **truthful empty state** explaining exactly this.
- **Full adhan** works with a **user-selected local audio file** (SAF picker, persistable URI permission) — selection and preview exist without any licensing risk.
- **Notification sound / Silent** modes are fully functional without any audio asset (platform channel sound).
- Service fallback: if FULL_ADHAN is selected with no file chosen, the service posts the silent notification and exits — it never fakes playback.

## 5. Location & permission behavior

- **Manual**: 107-city bundled catalog (`assets/prayer/cities.json`, GeoNames-derived, **CC-BY 4.0**, attributed in LICENSES.md) with EN+AR names, search, and country; persisted fully. Works with **zero permissions**.
- **Automatic**: framework `LocationManager` one-shot **coarse** fix on explicit user action only (no continuous tracking), cached; refresh button; graceful permission-denied handling (`prayer_location_permission_needed`), and an honest "could not get location → choose a city" path — times are never fabricated.

## 6. Scheduling architecture

`PrayerTimeManager` (app-scoped) computes today+tomorrow and arms **exactly one** AlarmManager alarm for the next prayer (single fixed PendingIntent → duplicate alarms structurally impossible; reconcile mutex against races). Paths that re-arm: app start, any preference change, alarm firing, `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `DATE_CHANGED`, `MY_PACKAGE_REPLACED`.
- **Exact alarms**: `setExactAndAllowWhileIdle` + `SCHEDULE_EXACT_ALARM`; `canScheduleExactAlarms()` checked, and when denied (emulators default!) the scheduler falls back to a **±10-minute window** and the UI says so honestly ("Exact alarms are not granted… up to 10 minutes"), with a tap-through to the system setting. **Live proof on emulator**: `dumpsys alarm` shows `*walarm*:com.ayah.quran.PRAYER_ALARM` armed at 2026-11-18 **04:52:00** = the calculated Cairo Fajr, window +10m.
- WorkManager is not used for prayer firing (per spec).

## 7. Background playback architecture

`AdhanPlaybackService` — foreground service (`mediaPlayback` type, Q+ API typed start) with Media3 ExoPlayer, `USAGE_ALARM` audio attributes, explicit `AudioFocusRequest` (loss → clean stop), partial wake mode, completion/focus-loss cleanup, and a notification with **Stop** action. Started from the alarm receiver; never duplicates (single guard per prayer/minute + single alarm source).

## 8. Notification architecture

Three channels: **adhan playback** (service-owned, silent channel), **prayer-time alert** (platform notification sound — kept separate from adhan audio per spec), **silent**. Localized texts: "حان الآن وقت صلاة الفجر." / "It is now time for Fajr prayer." POST_NOTIFICATIONS runtime permission respected (API 33+); status surfaced in settings.

## 9. Files created / modified

**New (domain/data/prayer/…):** `PrayerModels.kt`, `PrayerTimesCalculator.kt`, `NextPrayerCalculator.kt`, `PrayerAlarmScheduler.kt`, `PrayerPreferencesRepository.kt`, `AdhanPrayerTimesCalculator.kt`, `PrayerPreferencesRepositoryImpl.kt`, `CityCatalog.kt`, `DeviceLocationProvider.kt`, `AlarmPrayerAlarmScheduler.kt`, `prayer/PrayerTimeManager.kt`, `prayer/PrayerNotifications.kt`, `prayer/PrayerAlarmReceiver.kt`, `prayer/PrayerSystemReceiver.kt`, `prayer/AdhanPlaybackService.kt`, `di/PrayerModule.kt`, `ui/prayer/PrayerTimesScreen.kt`, `ui/prayer/PrayerTimesViewModel.kt`, `ui/prayer/PrayerSettingsScreen.kt`, assets (`cities.json`), fixture (`aladhan-reference.json`), tests (3 classes), `ic_prayer_notification.xml`, `docs/screenshots/phase12/`.
**Modified:** `app/build.gradle.kts` (adhan dep + org.json test dep), `AndroidManifest.xml` (7 permissions, 2 receivers, service), `AyahApplication.kt` (reconcile on start), `TopLevelDestination.kt` + `AyahApp.kt` (6th tab + settings route), `MainViewModel` untouched, strings ×2, `docs/LICENSES.md`.

## 10. Database migrations

**None.** All prayer state lives in DataStore; bookmarks/reading progress/wallpaper history untouched.

## 11–13. Test results (final code)

- **JVM: 73/73** — new: `PrayerTimesCalculatorReferenceTest` (150-reference-comparison + Hanafi + adjustment tests), `NextPrayerCalculatorTest` (6 state-machine tests incl. midnight rollover and exact-boundary semantics).
- **Instrumented: 52/52** — new `PrayerTimesUiTest`: honest no-location state on fresh install, manual Cairo selection → full timetable (5 prayers + sunrise + note + next), settings exposes all sections; full existing regression (Quran/mushaf/wallpapers/audio/localization) still green.
- `assembleDebug` + `assembleRelease`: pass.

## 14–16. Builds & screenshots

Debug APK + release builds pass. Screenshots (`docs/screenshots/phase12/`): honest no-location state (01), Arabic timetable with hero card — location, Hijri "8 جمادى الآخرة 1448", Gregorian, countdown to Fajr 04:52, adhan status (02–04), Arabic settings with localized method list (05–06), English prayer screen (07).

## 17. Known limitations (honest)

1. **No bundled adhan audio** (licensing, §4) — full adhan requires a user-selected file; notification/silent modes work out of the box.
2. **Exact alarms can be denied** (Android 12+) → ±10 min window + in-app warning + settings deep-link. Emulators typically deny; real devices grant via Settings.
3. **OEM battery managers** may delay alarms/services; guidance text provided, no guarantees promised.
4. **Auto-location shows coordinates only** (no reverse geocoding — it would require network); manual city selection gives named locations.
5. **Hijri date is algorithmic** (Umm al-Qura via java.time) with manual ±2-day adjustment and an explicit non-official disclaimer.
6. **Emulator-verified only.** No physical-device claim is made for alarms, background playback, or notifications (see §18).

## 18. Physical-device testing checklist (owner)

1. Grant exact-alarm permission (Settings → Apps → Ayah → Alarms & reminders) and verify on-time firing vs. a trusted timetable.
2. Test adhan playback with a selected audio file: screen off, app killed, another app playing music (focus loss), incoming call.
3. POST_NOTIFICATIONS grant/deny paths on Android 13+.
4. Reboot → alarm still armed at the right time.
5. Battery-optimizer behavior (especially Xiaomi/Huawei/Samsung) + "allow unrestricted" guidance.
6. Timezone/DST change → schedule re-arms correctly.

## Rules compliance

- No Quranic or prayer-time fabrication: engine verified against an independent reference; no-location → ask the user.
- No unlicensed audio; truthful empty catalog; attribution updated.
- Existing features intact (125-test regression); no destructive DB actions.
