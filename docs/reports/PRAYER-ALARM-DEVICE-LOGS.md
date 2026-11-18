# Prayer Alarm Device Logs — Phase 16

**Status: NOT PERFORMED on physical device.** The structured logging infrastructure is implemented and verified on the emulator. Physical-device logs require the HONOR 600 test in `HONOR-600-ALARM-TEST-RESULTS.md`.

---

## How to collect physical-device logs

1. Connect the HONOR 600 via USB with USB debugging enabled.
2. Before the prayer time, clear existing logs: `adb logcat -c`
3. Start the log capture: `adb logcat -s PrayerAlarm > prayer_alarm_log.txt &`
4. At the prayer time, lock the screen and wait.
5. After the notification appears (or doesn't), stop capture and save the file.

## Expected log format

```
PrayerAlarm | <correlation-id> | <stage> | <detail>
```

## Stages and their meaning

| Stage | Meaning | Expected timing |
|---|---|---|
| `SCHEDULE` | AlarmManager.setExactAndAllowWhileIdle() called | At app start, reconcile, or next-prayer re-arm |
| `SCHEDULE_FAILED` | AlarmManager rejected the request (exact denied) | Same |
| `RECEIVER_ENTER` | PrayerAlarmReceiver.onReceive() entered | At prayer time (normal) or delayed (OEM restriction) |
| `DELIVERY_DELAY` | Difference between scheduled and actual delivery | Should be < 5s on unrestricted devices |
| `NOTIFICATION_POSTED` | Prayer notification posted to the shade | Immediately after RECEIVER_ENTER |
| `NOTIFICATION_SUPPRESSED` | Notification skipped (DISABLED mode or permission denied) | Same |
| `PLAYBACK_STARTED` | AdhanPlaybackService started (FULL_ADHAN mode) | Same |
| `PLAYBACK_FALLBACK` | Service failed to start → silent notification fallback | Same |
| `NEXT_ARMED` | Next prayer alarm successfully re-armed | After RECEIVER_ENTER |
| `RECONCILE` | reconcile() called (app start, boot, timezone, etc.) | On any state change |
| `RECONCILE | overdue prayers detected` | A prayer time has passed since the last reconcile | Diagnostic only |

## Diagnostic scenarios (A–F from Phase 16 §2)

| Scenario | How to identify from logs |
|---|---|
| A. Alarm never registered | No `SCHEDULE` log for the prayer; `dumpsys alarm` shows no PRAYER_ALARM |
| B. Registered but not delivered | `SCHEDULE` logged; no `RECEIVER_ENTER` at prayer time; alarm visible in `dumpsys alarm` |
| C. Receiver delivered late | `RECEIVER_ENTER` logged but `DELIVERY_DELAY` shows minutes of delay |
| D. Receiver ran but notification failed | `RECEIVER_ENTER` logged; `NOTIFICATION_SUPPRESSED` or `NOTIFICATION_POSTED` missing |
| E. App startup generated overdue notification | `RECEIVER_ENTER` logged with large `DELIVERY_DELAY` + app-launch timestamp matches |
| F. Notification worked but playback failed | `NOTIFICATION_POSTED` + `PLAYBACK_FALLBACK` logged |
