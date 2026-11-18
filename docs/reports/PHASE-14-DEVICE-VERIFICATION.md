# Phase 14 — Device Verification (Smart Permissions & Reliability)

**Primary target: HONOR 600 (MagicOS).** All automated tests ran on the emulator (ayah_avd, API 36); **no physical device has been used — every physical item below is marked ☐ NOT PERFORMED.**

Build under test: `app-debug.apk` (Phase 14), installable via `adb install` or by opening the file.

## Environment separation

| Kind | What ran |
|---|---|
| **[AUTO]** Automated suite (final code) | 101 JVM + 58 instrumented = **159 tests, 0 failures**; includes permission grant/deny round-trips, notification posting on the real channel, dashboard/status rules |
| **[EMU]** This pass, emulator | Ready-banner flow, dashboard rows, test notification, test adhan service, 60-second locked-screen diagnostic alarm |
| **[DEVICE]** HONOR 600 | ☐ Nothing performed |

## Automated results [AUTO] (exact)

- JVM: **101 tests, 0 failures** — new: `PrayerStatusReducerTest` (4), `VendorDetectionTest` (5), `PrayerOnboardingFlagsTest` (3).
- Instrumented: **58 tests, 0 failures** — new: test-notification posting (real alert channel), exact-alarm grant reflection, notification deny→grant round-trip.
- `assembleDebug` + `assembleRelease`: pass.

## Emulator evidence [EMU] (executed this pass)

1. Fresh install → **Home card** "فعّل مواقيت الصلاة والأذان" visible (01).
2. Onboarding screen with the **sequential 5-step checklist** — step 1 active ("تفعيل"), later steps' statuses live (02).
3. After grants + resume: statuses flip to "مفعّل" and the **ready banner "إشعارات صلواتك جاهزة."** appears (03) — it never appears without verified statuses (JVM-tested).
4. Dashboard "إشعارات الصلاة والموثوقية" renders all rows + three test buttons (06).
5. **Test notification** → "إشعار تجريبي" appears in the shade on the real alert channel (04).
6. **Test Adhan** → `AdhanPlaybackService` runs (dumpsys-verified); honest fallback confirmed (no bundled/user audio → silent test notification, no fake playback) (05).

## Physical-device checklist [DEVICE] — HONOR 600

Prepare: install the test APK; Arabic UI.

1. ☐ Fresh installation → Home shows the onboarding card (or the onboarding screen on first open).
2. ☐ Open onboarding → steps show "غير مفعّل" with explanations; only step 1 has an action.
3. ☐ Tap Enable → system dialog explains → **Allow** → step 1 flips to "مفعّل".
4. ☐ Step 3 → **Allow** exact alarms in the system page → resume → step flips; prayer timetable (if city set) refreshes.
5. ☐ Step 4 → open the vendor guide → follow the HONOR App-launch steps → check "أتممت الإعداد" → row becomes "مكتمل".
6. ☐ Dashboard → **"اختبار إنذار بعد 60 ثانية"** → lock the screen → the diagnostic notification arrives within ~60s (allow up to +10 min if exact alarms were denied).
7. ☐ Dashboard → **"اختبار تشغيل الأذان"** (with a selected audio file) → adhan plays while locked; Stop works.
8. ☐ Reboot the device → **without opening the app** → prayer alarm re-armed (verify by waiting for the next prayer, or `adb shell dumpsys alarm | grep PRAYER_ALARM`).
9. ☐ Deny notifications (system) → dashboard shows the warning state; restore → flips back.
10. ☐ Revoke exact alarms → dashboard shows the ±10-minute warning; restore → warning clears and the next alarm is exact.
11. ☐ Change calculation method/location → timetable + next alarm update.
12. ☐ English UI → same flows, LTR, English strings.
13. ☐ Battery: enable MagicOS power saving → repeat 6–7 and record any delay.
14. ☐ Update-path check: change settings → update the APK (`install -r`) → selections and flags survive.

## Notes

- Exact-alarm **revocation kills the app process on Android 14+** (verified on emulator). This is platform behavior; Ayah recovers on next start and the dashboard then honestly shows the denied state.
- Notification denial suppresses posting only; full-adhan playback still attempts (audio ≠ notification).
- Do not enable "Unrestricted battery" blindly if policy-sensitive: prefer the MagicOS app-launch settings path (as implemented).
