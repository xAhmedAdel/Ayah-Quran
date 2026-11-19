# Permissions & OS Integrations (from AndroidManifest.xml, verified)

| Permission | Feature | Requested where | If denied | Redesign risk |
|---|---|---|---|---|
| INTERNET | adhan downloads (Wikimedia), Quran audio downloads (AlQuran Cloud), update check/download | implicit on feature use | feature-specific failure states | none (keep failure states honest) |
| SET_WALLPAPER | wallpaper engine | apply action | apply fails | none |
| ACCESS_COARSE_LOCATION | one-shot auto-location for prayer times | prayers tab "استخدام موقعي" | manual city picker remains (honest note shown) | keep the manual fallback visible |
| SCHEDULE_EXACT_ALARM | prayer-time accuracy | onboarding step 3 + settings "السماح بالمنبهات الدقيقة" | ±10min windowed alarms + warning card; **Android 14+ revocation kills the app process** (documented platform behavior) | keep guidance copy; never fake granted state |
| POST_NOTIFICATIONS (API 33+) | prayer/adhan notifications | onboarding step 1 + settings | notifications silently absent; dashboard shows real state | keep real state rows |
| RECEIVE_BOOT_COMPLETED | re-arm alarms after reboot | manifest | alarms lost until app opened (receiver re-arms) | none |
| WAKE_LOCK | adhan playback wake | manifest | playback may be killed by Doze | none |
| FOREGROUND_SERVICE + FOREGROUND_SERVICE_MEDIA_PLAYBACK | QuranPlaybackService + AdhanPlaybackService | manifest | background playback/adhan broken | none |
| REQUEST_INSTALL_PACKAGES | in-app updater | updater install step | deep-link to "install unknown apps" for the app; user returns and retries | keep the two-step flow and messaging |

## OS integrations

- **MediaSessionService** ×2 (Quran + Adhan): notifications/lock-screen
  come from Media3 — any UI redesign of "notification" is actually just
  Media3 defaults; do not fake custom notifications.
- **AlarmManager** exact alarms + boot receiver.
- **WallpaperManager** (SET_WALLPAPER + flag combos).
- **WorkManager** (on-demand init with Hilt; wallpaper rotation).
- **FileProvider** (`${applicationId}.fileprovider`, `@xml/file_paths`)
  for the update APK.
- **DataStore + Room + SharedPreferences** (restore snapshot).
- **SplashScreen API** (`Theme.Ayah.Starting`).

## HONOR 600 / MagicOS (from project history — docs/ in repo)

- MagicOS holds AlarmManager delivery for background-restricted apps
  (Phase 16 root cause — external platform limitation). The app counters
  with: permissions onboarding, battery-optimization guidance, a
  60-second diagnostic alarm, test notification/adhan buttons, and
  honest status rows (`docs/PHASE-16-PRAYER-ALARM-ROOT-CAUSE.md`,
  `docs/HONOR-600-ALARM-TEST-RESULTS.md`).
- System DownloadManager is UNRELIABLE on MagicOS — all downloads use
  direct OkHttp with a browser UA (adhan + Quran audio + updates).
- Release APKs are installed by hand (no Play Store); the in-app updater
  is the distribution channel.
