# HONOR 600 Compatibility Guide (MagicOS)

**Device:** HONOR 600 · **OS:** MagicOS (exact Android API level must be read at runtime — Ayah detects manufacturer/brand via `Build.MANUFACTURER`/`Build.BRAND` and never assumes a fixed version).

## 1. What Ayah does automatically for HONOR devices

- A dedicated **"إعدادات الخلفية لأجهزة HONOR"** guidance card appears in the permissions onboarding and in the reliability dashboard (Prayer Settings).
- Steps are written in Arabic and English, numbering the exact vendor settings to touch.
- A button attempts the closest system screen (see §3); if the vendor page does not exist on that MagicOS build, Ayah silently falls back to the application-details page and shows the manual steps. It never crashes and never uses hidden APIs.

## 2. Recommended HONOR settings (manual path)

Typical path — **names may differ per MagicOS version**:

1. Settings (الإعدادات) → Apps (التطبيقات) → App launch (تشغيل التطبيقات) → **Ayah (آية)**.
2. Turn OFF "Manage automatically" (الإدارة التلقائية).
3. Enable all three:
   - Auto-launch (التشغيل التلقائي) — allows the boot receiver + AlarmManager alarms.
   - Secondary launch (التشغيل الثانوي) — allows starting from related components.
   - Run in background (العمل في الخلفية) — keeps the adhan playback service alive.
4. Battery (البطارية) → Ayah → Unrestricted / allow background activity.
5. Notifications (الإشعارات) → allow, and keep the "Prayer-time notifications" channel enabled.

## 3. Intents Ayah may use (documented only)

| Target | Strategy |
|---|---|
| HONOR/Huawei App-launch manager | Community-documented component `com.huawei.systemmanager/.startupmgr.ui.StartupNormalAppActivityActivity` then `…appcontrol.activity.StartupAppControlActivity` — attempted inside try/catch (ActivityNotFoundException → next candidate). These are NOT officially documented APIs; they are best-effort conveniences with a guaranteed fallback. |
| App details page | `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` — always available, used as the universal fallback. |
| Exact alarms | `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` (documented system intent). |
| App notifications | `Settings.ACTION_APP_NOTIFICATION_SETTINGS` (documented). |
| Adhan alert channel | `Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS` + channel id (documented). |

Ayah does NOT request battery-optimization whitelisting programmatically (policy-sensitive, per spec §5) — it guides the user through the vendor screens instead.

## 4. Known limitations on HONOR/MagicOS

- Vendor screens may not exist (component name changes between MagicOS versions) → Ayah falls back to app details + manual steps; nothing crashes.
- MagicOS may still delay alarms or kill the foreground service under aggressive power saving; when exact alarms are also denied, alerts can be **up to 10 minutes late**. The reliability dashboard always shows the true exact-alarm state.
- Some MagicOS builds reset app-launch settings after app updates — re-check after updates.
- Ayah never promises that Adhan will play under every combination of DND, battery saver, and vendor restrictions.

## 5. Verification status

Emulator: guidance card + intents + fallbacks verified in code and exercised via the onboarding flow. **HONOR 600 physical verification: NOT PERFORMED** — the step-by-step device checklist is in `PHASE-14-DEVICE-VERIFICATION.md`.
