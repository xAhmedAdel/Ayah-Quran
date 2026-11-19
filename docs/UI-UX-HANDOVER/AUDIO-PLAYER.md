# Audio Player — Special Documentation

## Architecture (slice 1 + 2, beta.4→beta.6)

```
UI (AudioScreen / MiniPlayerBar / NowPlayingScreen / Reader)
        │ collects StateFlow
        ▼
ExoPlayerAyahPlaylistPlayer  (@Singleton MediaController CLIENT)
  - builds per-ayah playlist (local file if downloaded, else CDN stream)
  - owns: sleep timer, repeat range, progress persistence (SharedPreferences)
  - publishes AyahPlaybackState (edition, surah, ayahIndex, playing/paused,
    repeatAyah, blockedByAdhan, sleepMode, repeatRange, restorable)
        │ MediaController ↔ session
        ▼
QuranPlaybackService (MediaSessionService — owns ExoPlayer + MediaSession)
  - system media notification + lock-screen controls
  - background/screen-off playback, Media3 audio focus (calls pause/resume)
```

Support: `QuranAudioCatalog` (5 verified reciter editions + Hafs ayah
counts + CDN URL builder), `QuranAudioDownloadRepository` (per-ayah files
`quran-audio/{edition}/{surah}/{ayah}.mp3`, resumable, n/total progress).

Reciters (community-distribution licensing, attributed in-app):
العفاسي 128 · الحصري 128 · المنشاوي 128 · الحذيفي 128 · بصفر 64.
CDN: `https://cdn.islamic.network/quran/audio/{bitrate}/{edition}/{ayahGlobal}.mp3`.

## UI surfaces

1. **Audio tab** (`AudioScreen.kt`): reciter chips + attribution note +
   114 surah rows (download n/total / متاح دون اتصال / delete) + per-row
   play/pause + now-playing bar (ayah counter + repeat-ayah chip) +
   blocked-by-adhan banner + legacy Phase-5 playback panel.
2. **MiniPlayerBar**: global (hidden on Audio tab/idle); play/pause;
   restore-hint variant after process death.
3. **NowPlayingScreen** (`audio/now-playing`): reciter/surah cards, ayah
   "X من Y", prev/pause-resume/next/stop, repeat-ayah chip, sleep chips
   (off/after-ayah/after-surah/10/20/30min), speed chips (persisted).
4. **Reader integration**: tap-to-play-from-ayah, recited-ayah highlight,
   auto-scroll (continuous), auto page-follow (page mode).

## State flows the UI MUST keep consuming

- `ExoPlayerAyahPlaylistPlayer.state` (AyahPlaybackState) — the single
  playback truth. ⚠️ Two past bugs: UI collected a fresh local
  StateFlow instead of this one, and a combine used
  `flowOf(emptyMap())` instead of `downloadRepository.states`. When
  restructuring screens, preserve flow identity.
- `QuranAudioDownloadRepository.states` (Map<id, QuranAudioState>).
- `AudioPreferencesRepository.preferences` (selectedReciterId,
  repeatMode, playbackSpeed — persisted).

## Playback-state handling matrix (verified slice 2/3)

Play/pause/resume/next/prev/seek/reciter-switch/repeat-one/repeat-range/
sleep (after-ayah wins over range wrap)/restore-resume/surah switch all
flow through AyahPlaybackState transitions; the reader follows the ayah
key. Restore NEVER auto-plays; resume restarts the saved ayah (ayah-level
sync contract — no fabricated timestamps).

## Conflict policy (ADHAN WINS)

`AdhanPlaybackService.isAdhanActive`: adhan start → Quran service receives
`ACTION_STOP_QURAN`; Quran `play()` during adhan refuses with
`blockedByAdhan=true` (banner in Audio tab + mini player/Now Playing).
Prayer scheduling untouched. Verified live via `dumpsys audio`
(USAGE_ALARM vs USAGE_MEDIA).

## Files

`data/audio/QuranAudioCatalog.kt`, `QuranAudioDownloadRepository.kt`,
`ExoPlayerAyahPlaylistPlayer.kt`, `QuranPlaybackService.kt`,
`QuranPlaybackPolicy` (JVM-tested) · `di/AudioPlaybackEntryPoint.kt` ·
`ui/audio/*` · reader hooks in `ui/reader/ReaderViewModel.kt`,
`MushafPageViewModel.kt`.

## Redesign guidance

You may restyle every screen/bar/chip. You may NOT: change
`AyahPlaybackState` consumers to fabricated sources, drop the restore/
blocked states, remove honest download-state rows, or bypass
`QuranAudioDownloadRepository` for offline logic. Keep
content-descs used by tests (`تشغيل السورة`, `تحميل السورة`, …) or update
tests in the same change.
