# Phase 12 — Real Device Adhan Verification

**Purpose:** a controlled verification pass for the Phase 12 adhan system on a **physical device**, with every scenario's automated/emulator evidence documented first and clear separation between what the app guarantees and what depends on Android/manufacturer behavior.

**Test build:** `app/build/outputs/apk/debug/app-debug.apk`
SHA-256: `10a4fc8ae36647dec0b11082bb5df435f0919538c456db61c8a7ed4c86b363ee` (85.7 MB, debug-signed — installable via `adb install` or direct file open).

---

## 0. Evidence legend

| Tag | Meaning |
|---|---|
| **[AUTO]** | Covered by the committed automated test suite (executed: 73 JVM + 52 instrumented, 0 failures, Phase 12 final code). |
| **[EMU]** | Executed NOW on the emulator (Pixel 6 / API 36 / WHPX) during this verification pass — real system-level evidence, but **not** a physical device. |
| **[DEVICE]** | **Manual, on the physical device. NOT YET PERFORMED.** No pass/fail may be claimed until executed against the checklist below. |

## 1. Emulator evidence captured during this pass (recap)

1. **Armed alarm equals the computed prayer**: `dumpsys alarm` shows `*walarm*:com.ayah.quran.PRAYER_ALARM` at **2026-11-18 11:41:00** = Cairo Dhuhr shown on screen (Fajr 04:52 had passed → correct next-prayer state machine). [EMU]
2. **Process termination**: `am force-stop` → alarm survives (system-owned, not process-owned); app relaunch re-arms the same minute. [EMU]
3. **No duplicates**: after relaunch exactly **one** live `Alarm{…}` object exists (the second `dumpsys` hit is the historical snapshot section). Plus the in-receiver same-minute guard and the single-fixed-PendingIntent design. [EMU + AUTO]
4. **Reboot restoration**: reboot **without** force-stop → `BOOT_COMPLETED` receiver re-arms the alarm at the same prayer minute **without launching the app**. [EMU]
   - ⚠️ Documented nuance discovered during this pass: if the app was **force-stopped** before reboot, Android's stopped-state **blocks BOOT_COMPLETED delivery** (standard platform behavior) — the alarm then re-arms at the next app open via `AyahApplication.onCreate → reconcile()`. Manual check 5.c below covers this.
5. **Exact-alarm denial**: emulator denies `SCHEDULE_EXACT_ALARM` by default → alarm is armed as a **±10-minute window** (`window=+10m0s0ms` in dumpsys) and the UI shows the honest warning card ("الإنذارات الدقيقة غير ممنوحة… حتى 10 دقائق") + battery guidance. Screenshot `08_exact_alarm_denied_warning.png`. [EMU]
6. **Notification denial**: `pm revoke …POST_NOTIFICATIONS` → `granted=false`; the code path (`canNotify()`) suppresses posting; app continues to schedule/play per its audio mode. [EMU]
7. **Persistence**: location (Cairo) and computed times intact across reboot + relaunch. [EMU]

---

## 2. The 14-scenario device checklist

Prepare first:
- Install the test APK; open the app once; Prayer tab → select your city (or enable auto-location); Settings → choose a local audio file for **Full adhan** (put any small MP3/OGG on the device first); set one prayer (e.g. the soonest) to Full adhan, others to Notification sound.
- Note your device's Android version and manufacturer (battery-optimization behavior differs — see scenario 8).

### 1. Adhan playback while the app is open [DEVICE]
Wait for (or temporarily set a very near prayer via a tiny manual offset — Settings → Calculation → Manual adjustments, +N minutes on the soonest prayer).
**Pass:** adhan audio plays over the current screen; hero card unaffected; no duplicate restarts; notification "Adhan playing — Stop" appears; **Stop** ends audio.
**Guarantee:** app guarantees firing at the scheduled (exact or windowed) time and clean playback/focus handling. **Not guaranteed:** timing under denied exact alarms (±10 min window).

### 2. Playback with the app in the background [DEVICE]
Press Home, wait for the prayer.
**Pass:** audio plays; foreground notification visible with Stop action.
**Depends on:** notification permission granted (otherwise notification is hidden — audio still attempts) and no aggressive OEM background manager.

### 3. Playback with the screen locked [DEVICE]
Lock the screen, wait for the prayer.
**Pass:** audio plays; wake notification appears in the lock screenshade.
**Depends on:** OEM lock-screen notification rules; DND settings.

### 4. Playback after the process was terminated [DEVICE]
Settings → Apps → Ayah → Force stop; then wait for the prayer.
**Pass:** AlarmManager is system-level: the alarm fires and the service starts even though no process was alive.
**[EMU] evidence:** alarm survives `force-stop` (§1.2). **[DEVICE]** confirm the full audible path.
**Depends on:** OEM "kill everything" managers (see 8).

### 5. Alarm restoration after reboot [DEVICE]
Reboot the device **normally** (do NOT force-stop first), unlock, and just wait — the app must NOT be opened.
**Pass:** alarm re-armed at the correct next prayer (`adb shell dumpsys alarm | grep PRAYER_ALARM` if debugging) and notification/adhan fires on time without opening the app.
**[EMU] evidence:** re-armed at the same prayer minute post-reboot without app launch (§1.4).
**Nuance:** if the app was force-stopped before reboot, Android blocks BOOT delivery; opening the app once re-arms (by-design recovery path). 5.c: force-stop → reboot → open app → verify re-arm.

### 6. Exact-alarm permission denied [DEVICE]
Settings → Apps → Ayah → Alarms & reminders → **Deny**; wait for a prayer.
**Pass:** notification/adhan arrives within **≤10 minutes** of the prayer; the in-app warning card is shown; "Exact alarms: not granted" appears in Advanced.
**[EMU] evidence:** windowed fallback `window=+10m` + warning card verified (§1.5).
**Depends on:** Android 12+ only; older versions are exact by default.

### 7. Notification permission denied [DEVICE]
Settings → revoke Notifications; wait for a prayer set to **Notification sound**.
**Pass:** no notification is posted (no crash); a prayer set to **Full adhan** still plays (playback is audio, not notification) with its service notification hidden.
**[EMU] evidence:** `granted=false` after revoke; `canNotify()` suppresses posting.
**Depends on:** Android 13+ permission; hidden-notification behavior of FGS on some OEM skins.

### 8. Battery optimization / manufacturer restrictions [DEVICE]
Enable the vendor's aggressive mode (e.g. MIUI autostart-off, Samsung "Put unused apps to sleep", Huawei power-saving) OR let the device doze overnight; wait for prayers.
**Expected honest behavior:** alarms may be delayed/dropped — **the app cannot override vendor policy**. Guidance text is shown in-app; accept the delay as platform behavior, then re-test with Ayah "unrestricted".
**Not guaranteed by any app** — vendor-dependent.

### 9. Audio focus during an incoming call [DEVICE]
Start full adhan (temporary adjustment trick from 1), then receive a call.
**Pass:** adhan stops (or ducks) cleanly at call start; no overlap with ringtone; app notification/state stays consistent; no crash.
**Guarantee:** app requests ALARM focus and stops on `AUDIOFOCUS_LOSS*` — code-verified; real-call behavior is **[DEVICE]**.

### 10. Audio interruption and recovery [DEVICE]
While adhan plays, start media (music/video) in another app, then stop it.
**Pass:** adhan does not fight for focus; on focus loss it stops cleanly (by design the adhan yields to other media). Re-trigger to confirm playback starts again normally.
**Depends on:** the other app's focus usage.

### 11. Custom audio file persistence [DEVICE]
Select a file → force-stop the app → wait for a prayer → confirm the SAME file plays. Then reboot the device and repeat.
**[AUTO] evidence:** URI stored in DataStore + `takePersistableUriPermission(READ)`; restart survival via persisted grant. **[DEVICE]** confirms the end-to-end audible path and that the file lives in shared storage (files on removable SD or cloud-only locations may lose the grant — if playback falls back to silent notification, re-pick the file).

### 12. Scheduling after location / method / timezone changes [DEVICE]
Change city (e.g. Cairo → Makkah), change method (Egyptian → Umm Al-Qura), or travel across timezones; each time open the Prayer screen and check the timetable + next alarm.
**Pass:** timetable and the armed alarm update immediately (dumpsys time changes accordingly; values match a trusted timetable for the new location/method).
**[AUTO] evidence:** every change calls `reconcile()` (instrumented settings flow) and the JVM reference tests cover the method matrix. **[EMU] evidence:** location change re-arms (Cairo Dhuhr armed after selection).
**Depends on:** device timezone-change broadcast delivery (protected system broadcast).

### 13. Duplicate-adhan prevention [DEVICE]
Let a prayer fire normally; then force-stop → relaunch → let the SAME prayer minute window pass if still pending; also reboot right after a prayer fired.
**Pass:** exactly one adhan per prayer event.
**[EMU] evidence:** exactly one live alarm object at any time + same-minute receiver guard (§1.2–1.3). **[AUTO]**: single-PendingIntent design is structural.

### 14. Per-prayer disable [DEVICE]
Set **only one** prayer to Full adhan, all others **Off**; wait through an "Off" prayer.
**Pass:** no audio and no notification for the disabled prayer; the enabled prayer behaves per its mode; timetable still shows the time.
**[AUTO] evidence:** per-prayer modes persisted (`PrayerAdhanConfig`) and dispatched by mode in the receiver. **[DEVICE]** end-to-end silence confirmation.

---

## 3. Guarantees: app vs platform

**Guaranteed by the application (verified in code/tests/emulator):**
- Correct calculation (±2 min vs independent reference, 150-case verification) for the selected location/method.
- Honest no-location state — times are never invented.
- Exactly one armed alarm at any time; every change path re-arms from persisted state.
- Persistence of location, methods, adjustments, per-prayer modes, custom audio URI — across restart and reboot.
- Exact-alarm fallback (±10 min window) + truthful status UI; notification suppression when permission denied; clean audio-focus stop; no fabricated playback when no audio file exists.

**Depends on Android / manufacturer (cannot be promised by any app):**
- On-time delivery when exact alarms are denied (±10 min window) or under OEM battery managers/doze.
- BOOT delivery when the app is in force-stopped state (recovers at next app open).
- Notification visibility under DND/lock-screen vendor rules; hidden FGS notifications when notifications are denied.
- Audio routing quirks over Bluetooth/car mode.
- Whether vendor "battery savers" allow the foreground service to keep playing.

## 4. Results log

| Scenario | AUTO | Emulator (this pass) | Physical device |
|---|---|---|---|
| 1 App open | AUTO (service/focus code) | — | ☐ NOT PERFORMED |
| 2 Background | — | — | ☐ NOT PERFORMED |
| 3 Locked screen | — | — | ☐ NOT PERFORMED |
| 4 After termination | — | ✅ alarm survives force-stop | ☐ audio path NOT PERFORMED |
| 5 Reboot | — | ✅ re-armed without launch (+ force-stop nuance) | ☐ NOT PERFORMED |
| 6 Exact denied | — | ✅ ±10m window + warning UI | ☐ NOT PERFORMED |
| 7 Notifications denied | — | ✅ revoke → suppressed path | ☐ NOT PERFORMED |
| 8 Battery/OEM | — | — | ☐ NOT PERFORMED |
| 9 Call focus | AUTO (focus-loss stop code) | — | ☐ NOT PERFORMED |
| 10 Interruption | AUTO | — | ☐ NOT PERFORMED |
| 11 File persistence | AUTO (URI + grant persisted) | — | ☐ NOT PERFORMED |
| 12 Schedule changes | AUTO (reconcile paths) | ✅ location change re-armed | ☐ tz travel NOT PERFORMED |
| 13 Duplicates | AUTO (design) | ✅ exactly 1 live alarm | ☐ NOT PERFORMED |
| 14 Per-prayer disable | AUTO (mode dispatch) | — | ☐ NOT PERFORMED |

**Conclusion:** automated + emulator evidence is complete and consistent. **No physical-device verification has been performed yet** — every ☐ above must be executed manually on a real device before any on-device claim is made.
