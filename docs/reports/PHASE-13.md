# Phase 13 — Built-in Mu'adhin Audio Library & Per-Prayer Voice Selection

**Status: COMPLETE** — Ayah now ships with a built-in adhan audio library of **4 legally verified recordings** (CC0 / Public domain / CC BY-SA ×2 from Wikimedia Commons, 6.23 MB total), a default voice auto-assigned to all five prayers on first install, per-prayer voice selection, and a fully working preview player (progress, pause, one-at-a-time, cleanup). Phase 12 scheduling, modes, and all prior features remain intact.

**Final regression on the final code: 90 JVM + 55 instrumented = 145 tests, 0 failures. Debug + release builds pass.**

---

## 1. Audio library (licensing-first)

Four recordings obtained from Wikimedia Commons with **per-file license verification via the Commons API** (author, license, source URL — retrieved 2026-11-18, documented in [ADHAN-AUDIO-LICENSES.md](ADHAN-AUDIO-LICENSES.md)):

| id | voice | license | duration |
|---|---|---|---|
| `adhan_cc0_beautiful` (**DEFAULT**) | Community recording (CC0) | CC0 1.0 | 2:34 |
| `adhan_pd_sabah_fakhri` | **Sabah Fakhri** (famous reciter) | Public domain | 2:38 |
| `adhan_by_sa_azan` | Community recording | CC BY-SA 4.0 | 3:03 |
| `adhan_by_sa_call_to_worship` | Call to worship — Algeria | CC BY-SA 4.0 | 3:30 |

Rejected honestly: a 1:26 sample (incomplete) and a 31 MB WAV (weight). **No unclear-copyright audio is bundled; no license data is invented.** Full inventory + hashes: [ADHAN-AUDIO-ASSETS.md](ADHAN-AUDIO-ASSETS.md).

## 2. Default voice & selection persistence

- First install: all five prayers resolve to the **CC0 default** (domain constant `AdhanVoiceDefaults.DEFAULT_RECORDING_ID`); the DataStore keys are only written on explicit user choice, so "default" semantics survive updates.
- Per-prayer storage: `prayer_voice_{PRAYER}` DataStore keys via `setVoice(prayer, id)` — changing Fajr never touches the other four (JVM-tested).
- **Safety fallback**: a stored id missing from the catalog resolves to the DEFAULT recording (`AdhanVoiceResolver`) — never silence; the legacy Phase 12 user-file selection remains selectable as a library entry (`adhan_user_file`).
- Selections are locale-independent ids — switching Arabic/English never changes voices; changing calculation method/location never touches voices.

## 3. Preview (critical feature) — `AdhanPreviewController`

Single `@Singleton` controller with **one** lazy Media3 ExoPlayer instance:
- Play → immediate playback of the ACTUAL bundled resource (`android.resource://…/raw/…`).
- Button toggles Play ⇄ Pause; `LinearProgressIndicator` + elapsed/total labels update on a 500 ms ticker (only while playing).
- Starting a second preview stops the first (structural: `stopInternal()` before every start).
- Completion → state returns to idle (button resets).
- Audio focus (USAGE_MEDIA) — loss/transient-loss → clean stop (incoming calls).
- `stop()` releases the player, abandons focus, cancels the ticker; invoked from the screen's `DisposableEffect`/back navigation and VM `onCleared` — no lingering resources.
- Preview NEVER touches selections, alarms, or the foreground service.

## 4. Scheduled playback integration

`AdhanPlaybackService` now resolves the FIRED prayer's voice: `prefs.voiceSelection.voiceOf(prayer)` → `AdhanVoiceResolver` (fallback default) → `android.resource` URI; the legacy user-file entry still wins when explicitly chosen. **All Phase 12 modes (Full adhan / Notification sound / Silent / Disabled) and the entire scheduling stack (single AlarmManager alarm, exact-permission fallback, boot/time receivers) are untouched.** No second Media3 system exists; no Room changes; DataStore only.

## 5. UI

Settings → الصلاة والأذان → **"أصوات الأذان لكل صلاة"** opens the voice screen: five prayer cards (name in Amiri, current voice + check icon, "اختيار الصوت" button) → library dialog: name, description, duration + license badge, Play/Pause preview with progress and elapsed/total, Select button, selected-row highlight (primary container + check). Arabic RTL and English LTR verified on emulator; strings in both locales.

## 6. Tests (exact, final code)

**JVM — 90/90** (new +17):
- `BundledAdhanCatalogTest` (6): ≥4 recordings, unique res-safe ids, default = CC0 entry, completeness (≥120 s — no samples), licenses/attribution/source present, distinct sources.
- `AdhanVoiceResolverTest` (6): null/unknown → default, catalog ids self-resolve, user-file deferral, default detection, all-five resolution.
- `PrayerVoicePreferencesTest` (5): first-launch default everywhere, per-prayer distinct selection, Fajr-independence, restart persistence, `PrayerPreferences` exposure.

**Instrumented — 55/55** (new +3):
- `AdhanAudioInstrumentedTest`: every catalog recording maps to a real, openable, **decodable** raw resource with duration within ±5 s of catalog metadata; unknown id → default resource; per-locale names present.

## 7. Regression

All Phase 1–12 suites green in the same run: Quran integrity (6,236-ayah cross-check), mushaf 604-page mapping + UI, bookmarks/reading progress, wallpapers (incl. Both + rotation), prayer calculations (Aladhan fixtures) + scheduling + honest states, localization. No destructive DB action (voice keys are NEW DataStore keys; nothing reset).

## 8. Build results

`assembleDebug` ✅ · `assembleRelease` ✅. APK impact: **+6.23 MB** (audio inventory above).

## 9. Known limitations

- Preview is in-app only (leaving the screen stops it) — by design; scheduled adhan uses the foreground service.
- CC BY-SA rows require the attribution shown in Settings → Licenses (already rendered).
- Voice switching takes effect on the NEXT scheduled adhan; a currently-playing scheduled adhan is not hot-swapped.
- Physical-device audio-path verification (speaker volume, Bluetooth, OEM quirks) remains on the owner checklist (see PHASE-12-DEVICE-VERIFICATION.md §18); **no physical-device claim is made**.
- OGG/OGA + MP3 formats as published by Commons (no re-encoding); Android plays all natively.

## 10. Emulator-vs-device statement

All results above are from the **emulator (ayah_avd, API 36)** and host JVM. No physical device was used in Phase 13.
