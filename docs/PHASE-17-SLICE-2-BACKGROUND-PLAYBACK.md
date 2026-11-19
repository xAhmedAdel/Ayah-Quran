# Phase 17 — Slice 2: Background Playback, Reader Integration, Sleep Timer

**Status**: implemented, emulator-verified, physical-device testing pending
**Build**: v1.0.0-beta.5 (versionCode 6)
**Preserves Slice 1**: the verified reciter catalog, per-ayah downloads
(`QuranAudioDownloadRepository`), and the `AyahPlaybackState` API are all
unchanged — slice 2 moves the PLAYER into a service and adds layers on top.

---

## 1. Background playback

- **`QuranPlaybackService`** (`MediaSessionService`, manifest
  `foregroundServiceType="mediaPlayback"` + MediaSessionService intent
  filter). Deliberately minimal: owns the ExoPlayer + MediaSession.
  - Media3 provides the **system media notification** (play/pause,
    previous/next, seekbar) and **lock-screen controls** from the session.
  - Playback continues when the app is **backgrounded or the screen is
    locked** (foreground service).
  - **Audio focus** is Media3-handled (`handleAudioFocus = true`): phone
    calls and navigation pause with automatic resume; permanent loss stops.
  - `onTaskRemoved`: swiping the app away stops the service only when NOT
    reciting (the notification is the stop gesture while playing).
- **`ExoPlayerAyahPlaylistPlayer`** (slice-1 class, slice-2 internals):
  now an in-process **MediaController client**. Public API preserved —
  `state / play / pause / resume / stop / seekToAyah / toggleRepeatAyah`.
  It builds the per-ayah playlist (local file when downloaded, else CDN
  stream), owns the sleep timer, repeat range and progress persistence —
  all valid in background because the foreground service holds the process.
- **Process-recreation restore**: progress (edition, surah, ayah index) is
  persisted on every ayah transition and pause. After a process kill the
  mini player shows the last surah with a resume affordance ("اضغط للتشغيل
  للمتابعة"); resuming restarts FROM THE SAVED AYAH. No fabricated
  positions — in-ayah seeking is intentionally not restored because the
  synchronization contract is ayah-level.

## 2. Quran reader integration

- **Continuous reader**: tapping an ayah while reciting THIS surah seeks
  recitation to that ayah (synchronized navigation). When idle, tapping
  selects the ayah and a "تشغيل التلاوة من الآية X" button appears — one
  tap to start from there.
- **Recitation highlight**: the currently recited ayah is highlighted
  (same verified character-range mechanism as tap-selection; selection
  wins while the user has an active selection).
- **Auto-scroll** (optional, toggle in the reader top bar): the reader
  follows the recited ayah.
- **Mushaf page mode**: a play action (top bar) starts recitation from the
  highlighted ayah; the highlight follows the recited ayah while it is on
  the displayed page.
- **Quran integrity**: no reader text, page mapping, or ayah numbering was
  touched. Ayah-level synchronization is REAL (each playlist item IS one
  ayah file) — no timestamps are fabricated anywhere.

## 3. Sleep timer & playback settings

- `QuranPlaybackPolicy` (pure, JVM-tested): sleep modes **after current
  ayah** (stop on transition; takes precedence over range wrap), **after
  current surah** (stop at ENDED), **fixed minutes** (10/20/30, coroutine
  timer in the long-lived client — reliable during background playback
  because the foreground service keeps the process alive).
- **Repeat**: ayah (`REPEAT_MODE_ONE`), closed **range** (wraps past both
  ends), surah (natural playlist end → REPEAT_MODE_ALL not needed; range
  covers selective repetition). Repeat-ayah and speed persist through
  DataStore (`AudioPreferences.playbackSpeed`, `selectedReciterId`).

## 4. Mini player & Now Playing

- **MiniPlayerBar**: persistent across top-level screens (hidden on the
  Audio tab and while idle). Shows reciter — surah, ayah counter, play/
  pause; tap opens Now Playing.
- **NowPlayingScreen**: reciter, surah (Quran font), ayah "X من Y",
  previous/next ayah, pause/resume/stop, repeat-ayah, sleep chips
  (off/after-ayah/after-surah/10/20/30 min), playback speed chips
  (0.75x/1x/1.25x/1.5x, persisted). Route `audio/now-playing`.
- Arabic-first RTL and English LTR via the existing locale system; the
  premium design system (AyahCard/SectionCard/AyahSpacing) throughout.

## 5. Audio conflict policy — ADHAN WINS

- `AdhanPlaybackService.isAdhanActive` (volatile flag, set when adhan
  audio starts, cleared in cleanup).
- Adhan starts → it sends `ACTION_STOP_QURAN`: Quran recitation stops
  immediately (verified: USAGE_MEDIA track disappears, USAGE_ALARM runs).
- Quran start attempt during adhan → **refused** (`blockedByAdhan`), an
  honest banner explains it; no overlap is possible.
- Prayer scheduling and the entire adhan pipeline are untouched.

## 6. Testing

### Emulator (performed — ayah_avd, API 36)

| Scenario | Result |
|---|---|
| Playback starts from Audio tab | ✅ `state:started`, USAGE_MEDIA |
| Background: HOME pressed mid-recitation | ✅ audio continues |
| Media session registered (lock-screen controls) | ✅ media button session owned by the app |
| Mini player across screens + ayah counter | ✅ on HOME and PRAYERS tabs |
| Adhan starts while reciting | ✅ Quran stops (USAGE_ALARM only) |
| Quran start during adhan | ✅ refused, no overlap (USAGE_ALARM only) |
| Reader: play from tapped ayah (page mode) | ✅ mini player shows the tapped ayah |
| Process kill mid-recitation → relaunch | ✅ resume hint; resume plays saved surah/ayah |
| Sleep/repeat-range policy | ✅ 9 JVM tests on the pure policy |

### Physical device (PENDING — owner, HONOR 600 / MagicOS)

- [ ] Background playback with the screen OFF for a full surah
- [ ] Notification controls (play/pause/next/prev/dismiss) from the shade
- [ ] Lock-screen controls appearance
- [ ] Real incoming call → pause → resume after the call
- [ ] Battery-optimizer interference (MagicOS background restrictions)
- [ ] Sleep timer fires with the screen off
- [ ] Adhan from the scheduler stops Quran recitation on-device

### Automated suites

- JVM: **129 tests, 0 failures** (adds 9 `QuranPlaybackPolicy` tests)
- Instrumented: run at commit time (see commit message for the count)
- Adhan system: untouched by slice 2 except the additive conflict flag.

## Known limitations (honest)

- Now Playing seek bar is per-ayah position (Media3), ayah-level sync
  remains the contract; no in-ayah ayah-position restoration after process
  death (restarts the saved ayah).
- Page mode does not auto-turn pages during recitation (continuous reader
  auto-scroll only) — follow-up if wanted.
- Notification is Media3's default provider (correct controls; custom
  styling is cosmetic and deferred).
