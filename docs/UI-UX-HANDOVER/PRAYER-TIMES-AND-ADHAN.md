# Prayer Times & Adhan — Special Documentation

## Calculation (fully OFFLINE)

- `com.batoulapps.adhan:adhan:1.2.1` — calculation only; no network.
- Location: manual city (bundled `assets/prayer/cities.json`) or one-shot
  `ACCESS_COARSE_LOCATION` auto-location. No continuous tracking.
- Methods in UI: Egyptian, MWL, Umm al-Qura, Karachi, ISNA, Dubai, Kuwait,
  Singapore, Moon-sighting Committee; Asr madhab (Shafi'i/Hanafi);
  high-latitude rules; per-prayer minute adjustments.

## Scheduling & delivery

- `PrayerTimeManager` reconciles next prayer + schedules exact alarms
  (`AlarmManager`, `SCHEDULE_EXACT_ALARM`); `PrayerAlarmReceiver` fires;
  `PrayerSystemReceiver` re-arms on boot/time/timezone/date/
  package-replaced.
- Notification channels via `PrayerNotifications` (prayer + adhan
  channels); POST_NOTIFICATIONS runtime permission (API 33+) with an
  honest onboarding flow.
- Android 14+ exact-alarm revocation KILLS the app process (platform
  behavior) — the onboarding/settings explain this; the instrumented
  revoke test is skipped on API 33+ for the same reason.
- **HONOR/MagicOS**: holds alarm delivery for background-restricted apps
  (Phase 16 root-cause report: `docs/PHASE-16-PRAYER-ALARM-ROOT-CAUSE.md`);
  onboarding exposes battery-optimization guidance + a 60-second
  diagnostic alarm + test notification/adhan buttons. This is a platform
  limitation — UI must keep surfacing the guidance.

## Adhan playback

- `AdhanPlaybackService` (foreground, mediaPlayback type, USAGE_ALARM,
  explicit focus: transient loss pauses, permanent loss stops, duck
  ignored). Sources: per-prayer selection from `BundledAdhanCatalog`
  (4 bundled recordings) + user file + downloaded remote recordings
  (OkHttp `AdhanDownloadRepository`, Wikimedia-verified licenses,
  `adhan` dir in app storage).
- Per-prayer adhan mode: أذان كامل / نغمة إشعار / صامت / متوقف.
- **Priority**: `isAdhanActive` flag → stops Quran playback and refuses
  Quran start during adhan (see AUDIO-PLAYER.md).

## UI surfaces

| Surface | File | Notes |
|---|---|---|
| Prayer times tab | `PrayerTimesScreen.kt` | location setup (auto/manual city), times list |
| Prayer settings | `PrayerSettingsScreen.kt` | location, calculation method, Asr, high-latitude, minute adjustments, per-prayer adhan mode chips, custom adhan file, diagnostics, permissions dashboard |
| Adhan onboarding | `PrayerOnboardingScreen.kt` | 5 steps (notifications, channel, exact alarms, battery, adhan mode) + skip |
| Adhan voices | `AdhanVoiceScreen.kt` | per-prayer voice cards → library |
| Adhan library | `AdhanLibraryScreen.kt` | search, category chips (LazyRow), cards with download/preview/اختيار |

## What can be redesigned vs preserved

Redesign freely: cards, chips, layouts, colors, onboarding visuals,
diagnostics presentation — as long as every permission row keeps its real
system state, the honest "غير مفعّل/يحتاج انتباهك" statuses remain real,
and the diagnostic buttons keep firing the real services.

Preserve exactly: calculation pipeline, city data, alarm scheduling,
receiver wiring, channel IDs (`PrayerNotificationsChannels`),
AdhanPlaybackService behavior, conflict policy, adhan download storage
paths, and the test hooks used by
`PrayerPermissionsInstrumentedTest` (draining grant helper, shade polling).
