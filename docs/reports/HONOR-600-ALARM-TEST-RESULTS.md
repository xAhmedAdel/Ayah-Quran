# HONOR 600 Alarm Test Results

**Status: NOT PERFORMED on physical device.** All evidence below is from the emulator (ayah_avd, API 36). The HONOR 600 physical-device verification has NOT been performed.

---

## Emulator verification (executed)

| Test | Result | Evidence |
|---|---|---|
| Alarm armed at computed prayer time | ✅ | `dumpsys alarm` shows PRAYER_ALARM at Dhuhr 11:41:00 |
| Alarm survives force-stop | ✅ | Alarm persists in system state after `am force-stop` |
| Exactly 1 live alarm (no duplicates) | ✅ | Single `Alarm{…}` after relaunch |
| Reboot re-arms without app launch | ✅ | BOOT_COMPLETED receiver fires → reconcile() → alarm re-armed |
| Exact-alarm denial → ±10 min window | ✅ | `window=+10m0s0ms` in dumpsys + warning card |
| Notification deny→grant round-trip | ✅ | `PrayerPermissionsInstrumentedTest` |
| Test notification on real channel | ✅ | Active notification verified in shade |
| 60-second diagnostic alarm fires with screen locked | ✅ | `dumpsys notification` + shade screenshot |

## HONOR 600 physical-device checklist ☐ NOT PERFORMED

### Setup
1. ☐ Install the test APK (adb install or file transfer)
2. ☐ Open Ayah → complete permissions onboarding (notifications + exact alarms)
3. ☐ Configure MagicOS App launch settings (Auto-launch + Secondary launch + Run in background)
4. ☐ Select a city (Prayer tab → اختر مدينة → القاهرة)
5. ☐ Set one prayer to Full Adhan mode (Settings → Adhan → test prayer → أذان كامل)

### Alarm delivery tests
6. ☐ Note the next prayer time from the countdown
7. ☐ Close Ayah normally (Home button, NOT force-stop)
8. ☐ Lock the screen
9. ☐ Wait for the prayer time
10. ☐ Verify: notification appears within 60 seconds of the prayer time (or within 10 minutes if exact alarms denied)
11. ☐ Verify: Adhan audio plays (if Full Adhan mode selected)
12. ☐ Verify: notification text matches the prayer name

### Reboot test
13. ☐ Reboot the HONOR 600
14. ☐ WITHOUT opening Ayah, wait for the next prayer time
15. ☐ Verify: notification appears (BOOT_COMPLETED receiver restored the alarm)

### Denial tests
16. ☐ Revoke notifications (Settings → Apps → Ayah → Notifications → Off)
17. ☐ Verify: dashboard shows "not granted" warning
18. ☐ Revoke exact alarms (Settings → Apps → Ayah → Alarms & reminders → Off)
19. ☐ Verify: dashboard shows the ±10-minute warning

### Diagnostic test alarm
20. ☐ Dashboard → "اختبار إنذار بعد 60 ثانية" → lock screen → verify notification within 60 seconds
21. ☐ Check `adb logcat -s PrayerAlarm` for the full pipeline trace (SCHEDULE → RECEIVER_ENTER → DELIVERY_DELAY → NOTIFICATION_POSTED)

### adb commands for diagnostics

```bash
# Verify alarm is registered
adb shell dumpsys alarm | grep -A5 "PRAYER_ALARM"

# Check exact alarm permission
adb shell dumpsys deviceidle | grep -i "ayah\|exact"

# View structured logs
adb logcat -s PrayerAlarm

# Force-deliver a test broadcast (dev only)
adb shell am broadcast -a com.ayah.quran.DIAGNOSTIC_ALARM -n com.ayah.quran.debug/com.ayah.quran.prayer.DiagnosticAlarmReceiver
```

### Expected logcat output (successful delivery)

```
PrayerAlarm | ASR-2026-11-18-14:37 | SCHEDULE | trigger=1794994620000 | exact=true
PrayerAlarm | ASR-2026-11-18-14:37 | RECEIVER_ENTER | prayer=ASR | processAlive=12345
PrayerAlarm | ASR-2026-11-18-14:37 | DELIVERY_DELAY | 1234ms (0min)
PrayerAlarm | ASR-2026-11-18-14:37 | NOTIFICATION_POSTED | channel=prayer_time_alert
PrayerAlarm | ASR-2026-11-18-14:37 | NEXT_ARMED | next=MAGHRIB at=1794998280000
```

### Expected logcat output (MagicOS delayed delivery)

```
PrayerAlarm | ASR-2026-11-18-14:37 | SCHEDULE | trigger=1794994620000 | exact=true
... (user opens app at 15:15) ...
PrayerAlarm | ASR-2026-11-18-14:37 | RECEIVER_ENTER | prayer=ASR | processAlive=12346
PrayerAlarm | ASR-2026-11-18-14:37 | DELIVERY_DELAY | 2280000ms (38min)  ← PROOF OF MagicOS RESTRICTION
PrayerAlarm | ASR-2026-11-18-14:37 | NOTIFICATION_POSTED | channel=prayer_time_alert
```
