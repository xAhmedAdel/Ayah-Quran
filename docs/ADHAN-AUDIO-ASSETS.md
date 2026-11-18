# Adhan Audio Assets (Phase 13)

All recordings live in `app/src/main/res/raw/` (stable names, R8-kept via the explicit `when` map in `AdhanRawResources`). Offline from first launch; no runtime downloads.

## Inventory

| res/raw name | id | format | duration | size | SHA-256 (first 16) |
|---|---|---|---|---|---|
| `adhan_cc0_beautiful.ogg` | `adhan_cc0_beautiful` (DEFAULT) | OGG Vorbis | 2:34 | 1,229,032 B | `35fe06b08fe80505` |
| `adhan_pd_sabah_fakhri.mp3` | `adhan_pd_sabah_fakhri` | MP3 | 2:38 | 2,530,742 B | `94a2fe12f2e9998b` |
| `adhan_by_sa_azan.ogg` | `adhan_by_sa_azan` | OGG Vorbis | 3:03 | 1,473,809 B | `2163ceafb8a05064` |
| `adhan_by_sa_call_to_worship.oga` | `adhan_by_sa_call_to_worship` | OGG Vorbis | 3:30 | 1,295,539 B | `c30a43af28d391b4` |

**Total: 6,529,122 bytes = 6.23 MB** (APK size impact; release builds use the same files uncompressed-compressed by AAPT as normal for res/raw).

Full SHA-256 values: `docs/reports/audio-hashes/*.sha256`.

## Provenance

All four are genuine adhan recordings (mosque callers / a known reciter), sourced from Wikimedia Commons with per-file license verification — see [ADHAN-AUDIO-LICENSES.md](ADHAN-AUDIO-LICENSES.md).

## Size notes

- 6.23 MB added to the APK (the Phase 12 debug APK was ~85.7 MB; Phase 13 debug ≈ 92 MB).
- Original Commons files kept verbatim (no re-encoding): bitrates already sensible (Vorbis ~64–96 kbps / MP3 128 kbps), all mono/stereo speech.
- The rejected 31 MB WAV candidate would have tripled this; the rejected 1:26 MP3 sample violated the "complete adhan" rule.

## Architecture mapping

```
res/raw (audio)  ←→  AdhanRawResources (id → @RawRes, explicit when-map)
domain AdhanRecording metadata  ←→  BundledAdhanCatalog (4 entries + default)
stored voice ids (DataStore)    ←→  AdhanVoiceResolver (fallback → default)
preview playback                ←→  AdhanPreviewController (single ExoPlayer)
scheduled playback              ←→  AdhanPlaybackService (per-prayer resolution)
```
