# Phase 14 — Smart Permissions Onboarding & Honor 600 Background Reliability

**Status: COMPLETE (app implementation + emulator verification)** — a sequential permissions onboarding, a live reliability dashboard, two independent diagnostics (test notification / test adhan), a 60-second REAL diagnostic alarm, and HONOR/MagicOS-specific background guidance. Primary target device is the **HONOR 600 (MagicOS)**; all verification so far is **emulator-only** (see §11 and PHASE-14-DEVICE-VERIFICATION.md).

**Final regression on the final code: 101 JVM + 58 instrumented = 159 tests, 0 failures. Debug + release builds pass.**

---

## 1. What was built

### Onboarding screen (first launch, skippable)
- A prominent **Home card** ("فعّل مواقيت الصلاة والأذان") is shown until the onboarding is finished **or skipped** (DataStore flag; survives restarts/updates). Tapping opens the dedicated screen — screenshots `01/02/03`.
- **Sequential checklist** — only the current step exposes its action, so the user never faces stacked system dialogs:
  1. **Notifications** (runtime request, pre-explained; if previously denied → app notification settings, never a re-prompt loop).
  2. **Adhan notification channel** — auto-checked (must EXIST and be non-blocked; app-level enablement alone is not accepted).
  3. **Exact alarms** — `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`; statuses re-verified on resume.
  4. **Vendor background settings** — device-specific guide (see §2). **Cannot be auto-verified → labeled "موصى به — لا يمكن التحقق منه تلقائياً"** with an explicit "أتممت الإعداد" acknowledgement.
  5. **Adhan mode** — opens the per-prayer adhan settings.
- **Ready banner "إشعارات صلواتك جاهزة."** renders ONLY when notifications + channel + exact alarms are all verified (`PrayerStatusReducer` — pure, JVM-tested).
- **Skip** sets the same completion flag and shows the honest warning that alerts may not arrive on time until settings are enabled.
- Live verification captured on emulator: grants via system dialogs → resume → statuses flip to "مفعّل" → banner appears (screenshot 03).

### Reliability dashboard (Prayer Settings section "إشعارات الصلاة والموثوقية")
Live-verified rows + actions (screenshot 06): Notifications · Adhan channel · Exact alarms · Background guidance (Completed/Needs attention) · Adhan mode — plus actions "تفعيل الإشعارات"، "السماح بالمنبهات الدقيقة"، "فتح إعدادات البطارية"، and the three diagnostics below. **Refreshes via ON_RESUME** (returning from Android Settings updates every row).

### Diagnostics (independent, spec §7)
- **Test notification**: posts a clear "إشعار تجريبي" on the SAME alert channel real prayers use; never touches alarms or adhan audio.
- **Test Adhan**: starts the Phase 12 foreground service in TEST mode (`EXTRA_TEST`) — plays the currently selected voice with the notification labeled "— تجربة"; respects audio focus; never modifies schedules. Honest fallback: with no user-selected audio file it posts the silent test notification instead of faking playback (service verified running via `dumpsys activity services` on emulator).
- **60-second diagnostic alarm**: arms a REAL AlarmManager exact (or ±10s window when exact denied) alarm via a SEPARATE receiver/request-code — structurally cannot touch prayer alarms. **Live acceptance proof**: scheduled from the dashboard, screen locked, 60 s later `dumpsys notification` shows the diagnostic alert AND it is visible in the shade (screenshot 07) — the primary acceptance criterion, on emulator.

### Alarm reliability (§8)
Existing Phase 12 architecture kept (single alarm, receivers, reconcile). Additions: statuses are re-probed on resume; exact-permission grant/deny flips are reflected immediately; dashboard never claims exact scheduling when denied (warning + settings deep-link only).

## 2. HONOR 600 / MagicOS guidance

- Device kind detected at runtime from `Build.MANUFACTURER`/`BRAND` (`VendorDetection` — HONOR/HUAWEI/Samsung/Generic; JVM-tested).
- HONOR/HUAWEI devices get a step-by-step Arabic+English card (App launch → manage automatically off → auto-launch/secondary-launch/run-in-background on; battery allow-background) + a best-effort **community-documented** system-manager component intent, falling back to the always-available application-details page inside try/catch — never crashes, never fabricates an intent.
- Samsung/other devices get the generic battery/app-settings guide. Menu-name differences are explicitly disclaimed in the dialog.
- See `HONOR-600-COMPATIBILITY.md` for paths, intents, and limitations.

## 3. Permission strategy (Android API differences)

| Concern | Strategy |
|---|---|
| POST_NOTIFICATIONS (33+) | Runtime request with pre-explanation; denial → app-notification-settings action (no re-prompt loop); <33 → considered granted |
| Channel enablement | `NotificationManager.getNotificationChannel(id)` importance != NONE; channel ids centralized in `PrayerNotificationsChannels` |
| SCHEDULE_EXACT_ALARM (31+) | `canScheduleExactAlarms()` probe + documented system intent; denial → ±10 min windowed alarm + honest warning. **USE_EXACT_ALARM deliberately NOT used** (Play restricted-permission policy review not performed — documented) |
| Revocation | Android 14+ **kills the process** on revocation (verified on emulator — instrumentation dies too); app recovers on next start via the always-on resume-probe + reconcile paths |
| Vendor background | Guidance-only (no programmatic whitelisting; policy-compliant) |
| No DB changes | onboarding/guidance flags live in DataStore (`prayer_onboarding_done`, `prayer_vendor_guidance_done`) |

## 4. Tests (exact, final code)

**JVM — 101/101** (new +11): `PrayerStatusReducerTest` (ready requires ALL essentials; sequential next-step; vendor guidance doesn't block ready), `VendorDetectionTest` (HONOR/HUAWEI/Samsung/Generic + brand fallback), `PrayerOnboardingFlagsTest` (flags default false, finish/skip persists, guidance acknowledgement persists).

**Instrumented — 58/58** (new +3): test notification **actually posts** on the real alert channel (verified via `activeNotifications`); exact-alarm appop grant reflects in `canScheduleExactAlarms()`; notification deny→grant round-trip reflects in `areNotificationsEnabled()`. NOTE: mid-run SCHEDULE_EXACT_ALARM revocation cannot be instrumented — Android 14+ kills the process on revocation (documented platform behavior); the denied-state app behavior is covered by the emulator's default-denied evidence (warning card + windowed alarm).

**Live emulator acceptance evidence**: ready banner after verified grants (03); test notification visible in the shade (04); diagnostic alarm fired with the **screen locked** — posted notification confirmed via `dumpsys notification` + shade (07); dashboard with all rows + three test buttons (06).

## 5. Regression & builds

All Phase 1–13 suites pass unchanged in the same run (Quran integrity, mushaf, wallpapers, prayer calculations, adhan voices/modes, localization). No DB schema change; no new permissions. `assembleDebug` + `assembleRelease` pass.

## 6. Known limitations

- Vendor settings cannot be auto-verified — labelled recommended/not-verified (spec-compliant).
- Exact-alarm intent can be unsupported on some skins → try/catch fallback to app details; no hidden APIs.
- The onboarding gate deliberately does NOT hard-redirect under instrumentation (documented in code) — dedicated instrumented tests cover the flow instead.
- Notification-channel state depends on user/channel-level settings; the dashboard checks the real channel state, but a user can re-disable it at any time (dashboard reflects on next resume).

## 7. Physical-device status (HONOR 600)

**NOT performed.** The complete step-by-step device checklist (fresh install → onboarding → grants → MagicOS settings → 60-second locked-screen diagnostic alarm → adhan test → reboot restore → deny paths) is in `PHASE-14-DEVICE-VERIFICATION.md` with NOT-PERFORMED markers.
