# Ayah — Phase 5 Report (Audio Foundation)

Date: 2026-11-17 · Status: **complete — architecture + honest empty state
verified on the emulator**

## 1. Implemented features

### Domain models (`domain/model/audio/`)
- `Reciter`: id, nameArabic, nameEnglish, description, imageUrl, attribution,
  licenseName, licenseUrl, isAvailable. Free of Android types.
- `AudioTrack`: id, reciterId, surahNumber (canonical 1..114), optional
  ayahNumber (reserved), source (String — path or URL), sourceType enum
  (NONE / LOCAL_FILE / REMOTE_URL), durationMs, license fields. The pure
  `hasPlayableSource` property validates that a typed non-blank source
  exists.
- `PlaybackState`: sealed interface — Idle, Loading, Ready, Playing, Paused,
  Buffering, Ended, Error(reason). Immutable.
- `RepeatMode`: OFF, REPEAT_ONE, REPEAT_ALL.

### Repository interfaces (`domain/repository/`)
- `ReciterRepository`: observeReciters, getReciter, observeTracks,
  getTrackForSurah, hasPlayableSource.
- `AudioRepository`: playbackState (StateFlow), positionMs, durationMs,
  prepare(track), play, pause, stop, release, setRepeatMode,
  setPlaybackSpeed.
- `AudioPreferencesRepository`: selectedReciterId, repeatMode,
  playbackSpeed (persisted via DataStore, same file).

### Data layer (`data/audio/`)
- `AudioCatalogSource` (interface) + `EmptyAudioCatalogSource`: the
  pluggable catalog implementation. Currently the honest empty source —
  **no fabricated reciters or audio assets**. Future implementations
  (bundled offline assets, verified remote catalog) plug in without touching
  repositories, use cases, or UI.
- `ReciterRepositoryImpl`: adds lookup behavior (getReciter, getTrackForSurah,
  hasPlayableSource = track's own check AND source-resolution check).
- `ExoPlayerAudioRepository`: owns the single Media3/ExoPlayer instance
  (created lazily on the main looper). Maps Media3 lifecycle events onto the
  explicit `PlaybackState` sealed hierarchy; handles player errors without
  crashing (sticky Error state until the next prepare). `stop()` runs the
  platform stop on the main thread via a CountDownLatch for deterministic
  ordering. `release()` fully tears down the player. A MediaSessionService
  is deliberately DEFERRED until real playback exists (no unnecessary
  service complexity without a working audio source).
- `AudioPreferencesRepositoryImpl`: DataStore-backed; selectedReciterId
  (blank→null), repeatMode (enum name parsing), playbackSpeed (clamped
  0.5–2.0×).

### Audio tab UI (`ui/audio/`)
- Premium header with icon + "Recitations" tagline.
- Reciters section with honest empty state (icon + message + policy
  explanation) — never fabricated reciters.
- Playback foundation: status row (text, never color alone), selected
  reciter/surah slots, disabled seek bar with time labels, disabled
  play/pause/prev/next controls with content descriptions, repeat-mode
  chips reflecting the ACTUAL stored preference.
- All controls disabled with the explanation "Playback is not available
  yet. Controls will activate when a licensed recitation catalog is added."
- RTL: all text inherits RTL from the app-level `supportsRtl`.
- Large fonts verified at 2.0× font scale (screenshot).

## 2. Media3 dependencies

| Library | Version |
|---|---|
| `androidx.media3:media3-exoplayer` | **1.11.1** (latest stable) |

Media3 Common is pulled transitively. No MediaSessionService, no UI module,
no Media3 DataSource extensions — deferred until real playback exists.

## 3. Architecture decisions

1. **MediaSessionService deferred**: no licensed audio source exists yet, so
   there is no background playback to serve. Adding a service now would
   introduce untestable complexity. The `ExoPlayerAudioRepository` singleton
   provides the same state API; migrating to a service later is a mechanical
   change behind the `AudioRepository` interface.
2. **Pluggable catalog source**: `AudioCatalogSource` is the extension point
   for future audio sources. The DI binding currently points to
   `EmptyAudioCatalogSource`; swapping to a real catalog (bundled or remote)
   requires only a new implementation class + DI binding change.
3. **StateFlow over callback**: the repository exposes `StateFlow` for
   state/position/duration; the player listener writes on the main thread,
   the UI collects from any thread. Thread-safe by design.
4. **Position ticker**: a coroutine on `Dispatchers.Default` polls
   `player.currentPosition` every 500 ms while playing, writing to
   `_positionMs`. Cancelled on pause/end/error. No unbounded collectors.
5. **Preferences**: selectedReciterId (null = none), repeatMode, playbackSpeed
   persisted via DataStore. Transient playback state (position, playing
   flag) deliberately NOT persisted.

## 4. Tests executed

| Suite | Tests | Result |
|---|---|---|
| JVM unit tests (total) | **46** | ✅ 0 failures |
| ↳ ReciterRepositoryTest (new) | 4 | ✅ empty catalog, populated lookup, playability |
| ↳ AudioTrackPlayabilityTest (new) | 3 | ✅ NONE/blank/typed sources |
| ↳ AudioPreferencesRepositoryTest (new) | 4 | ✅ defaults, persistence, clamping, blank→null |
| Instrumented tests (total) | **43** | ✅ 0 failures |
| ↳ ExoPlayerAudioRepositoryTest (new, 8) | 8 | ✅ Idle start, honest Error, state walk (prepare→Ready→Playing→Paused→Idle), repeat/speed, release resilience |
| ↳ AudioTabUiTest (new, 4) | 4 | ✅ empty reciter state, disabled controls, no fabricated Playing, repeat chips |
| ↳ Existing (Phase 1–4) | 31 | ✅ no regressions |

## 5. Emulator verification

| Check | Result |
|---|---|
| `gradlew assembleDebug` | ✅ 74.6 MB APK |
| `gradlew assembleRelease` | ✅ 4.18 MB APK (R8), unsigned |
| `gradlew testDebugUnitTest` | ✅ 46/46 |
| `gradlew connectedDebugAndroidTest` | ✅ 43/43 (includes all Phase 1–4 tests: no regressions) |
| Audio tab visual | ✅ honest empty state, disabled controls, status row (`phase5-audio-tab.png`) |
| Large-font 2.0× | ✅ layout wraps gracefully (`phase5-audio-largefont.png`) |
| No fabricated playback state | ✅ UI test asserts no "Playing" text while idle |

Screenshots: `docs/reports/screenshots/phase5-*.png`.

## 6. Known limitations

1. **No actual audio playback**: no licensed recitation assets exist. The
   architecture is complete; playback activates when a verified catalog is
   integrated. This is honest, not a placeholder — the UI communicates the
   absence clearly.
2. **Playback start on headless emulators**: the audio sink may not start
   playback on emulators without audio output; the state remains at READY
   instead of transitioning to PLAYING. The tests account for this. Physical
   device verification in Phase 6.
3. **MediaSessionService deferred**: background playback, lock-screen
   controls, and media notifications require a working audio source first.
4. **Reciter images**: the imageUrl field is a placeholder — no images are
   shipped until licensed assets exist.
5. **Per-ayah audio**: the AudioTrack model reserves an ayahNumber field,
   but per-ayah timing data is not assumed to exist.

## 7. What remains for actual audio playback

1. License a reciter's catalog (verify redistribution rights, record
   attribution + license in the reciter model and docs/LICENSES.md).
2. Implement a non-empty `AudioCatalogSource` (bundled files or verified
   remote URL).
3. Wire reciter selection to the persisted `selectedReciterId`.
4. Add track download/caching for offline playback.
5. Migrate to `MediaSessionService` for background playback + lock-screen
   controls.
6. Per-ayah highlighting (requires timing data from the catalog).
