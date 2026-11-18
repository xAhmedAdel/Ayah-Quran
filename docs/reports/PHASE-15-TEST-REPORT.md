# Phase 15 — Test Report

**Final code, all suites executed on emulator (ayah_avd API 36) + host JVM.**

## JVM (testDebugUnitTest)

**101 tests + 6 downloader tests = 107 tests, 0 failures.**

| Suite | Count | Key coverage |
|---|---|---|
| Quran dataset integrity + parser + reference | 12 | 6,236-ayah cross-check, bismillah reversibility, page mapping (604) |
| Mushaf page assembly + resolver | 7 | segment splitting, bismillah conventions, verbatim text |
| Next-prayer state machine | 6 | midnight rollover, boundary semantics |
| Settings (theme + language + voices) | 10 | prefs chains, voice defaults, per-prayer independence |
| **AdhanFileDownloader** (NEW) | **6** | success, SHA-256 verify, size-mismatch fail, corrupted-audio reject, wrong-expected-SHA fail, HTTP 404 |
| **AdhanVoiceSelection persistence** (NEW) | **5** | default-to-all-prayers, per-prayer distinct, Fajr independence, restart persistence, PrayerPreferences exposure |
| **BundledAdhanCatalog** (NEW) | **6** | ≥4 recordings, unique res-safe ids, default = CC0 entry, completeness ≥120 s, byId, distinct sources |
| **AdhanVoiceResolver** (NEW) | **6** | null→default, unknown→default, catalog ids, user-file deferral, default detection, all-five resolution |
| Wallpaper (apply/render/schedule/history) | 10 | — |
| Audio foundation | 5 | — |
| Top-level destinations | 3 | — |
| Theme mode | 1 | — |
| Other (import, selector, templates) | ~15 | — |

## Instrumented (connectedDebugAndroidTest)

**55 tests + 4 new audio/download tests = 59 tests, 0 failures.**

| Suite | Count | Key coverage |
|---|---|---|
| AppUiTest | 6 | Home/nav/mushaf/continue-reading/localization |
| MushafPageReaderUiTest | 6 | page navigation, swipe, pinch zoom, page jump, per-prayer voices |
| PrayerTimesUiTest | 3 | prayer tab, manual city, settings sections |
| PrayerPermissionsInstrumentedTest | 3 | test notification on real channel, exact-alarm grant reflection, notification deny→grant round-trip |
| **AdhanAudioInstrumentedTest** (NEW) | **4** | bundled recordings decodable (±5s duration), remote-id→default-resource fallback, unknown-id fallback, per-locale names |
| AudioTabUiTest | 4 | honest empty state, no fabricated playing state, repeat chips |
| QuranImportInstrumentedTest | 4 | import lifecycle |
| WallpaperApply + Renderer + Scheduling | 17 | apply/Both/render/scheduling |
| BookmarkReadingProgressDaoTest | 4 | bookmarks + reading progress |
| ExoPlayerAudioRepositoryTest | 8 | audio playback states |

## Builds

`assembleDebug` ✅ · `assembleRelease` ✅ · APK size: ~85.7 MB debug (audio +6.23 MB vs Phase 12).
