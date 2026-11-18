# Phase 15 — Expanded Adhan Library: Download Manager & Per-Prayer Voices

**Status: COMPLETE** — the adhan library now supports **remote downloadable recordings** (Madinah, Casablanca) alongside the 4 bundled recordings (Phase 13), with a premium library screen (category filters, search, downloaded-only toggle, download/cancel/retry/delete), per-prayer voice assignment that requires download before selection, and SHA-256/size/magic validation on every download.

**Final regression on the final code: 107 JVM + 59 instrumented = 166 tests, 0 failures. Debug + release builds pass.**

---

## 1. Audio catalog (6 entries: 4 bundled + 2 remote)

| id | category | voice | license | duration | size | bundled |
|---|---|---|---|---|---|---|
| `adhan_cc0_beautiful` | Built-in | Community (CC0) | CC0 1.0 | 2:34 | 1.2 MB | ✅ |
| `adhan_pd_sabah_fakhri` | Famous mu'adhins | **Sabah Fakhri** | Public domain | 2:38 | 2.4 MB | ✅ |
| `adhan_by_sa_azan` | Built-in | Community | CC BY-SA 4.0 | 3:03 | 1.4 MB | ✅ |
| `adhan_by_sa_call_to_worship` | Other regions (Algeria) | Community | CC BY-SA 4.0 | 3:30 | 1.2 MB | ✅ |
| `adhan_madinah_prophet_mosque` | **Madinah** | Community (Prophet's Mosque) | **CC BY 3.0** | 3:06 | 2.8 MB | ⬇ remote |
| `adhan_casablanca_hassan2` | Other regions (Casablanca) | Community (Hassan II Mosque) | CC BY-SA 4.0 | 2:56 | ~31 MB | ⬇ remote |

**Makkah audio**: no Commons-hosted audio file with verified redistribution rights was found (the Masjid al-Haram recordings on Commons are VIDEO files with YouTube-origin provenance — Phase 15 §2 explicitly forbids YouTube-ripped audio). The Makkah category filter shows an honest "not available yet" state. This is not a defect — it is the correct licensing-first behavior.

**Casablanca**: the ~31 MB WAV is included as a downloadable-only entry with the size prominently displayed so the user makes an informed choice.

## 2. Architecture

- `BundledAdhanCatalog` extended with `category`, `muadhinName`, `mosqueCity`, `isBundled`, `downloadUrl`, `expectedSizeBytes`, `fileExtension` on `AdhanRecording`.
- **`AdhanFileDownloader`** (pure, JVM-testable): HTTPS GET → streams to destination → validates size + audio magic bytes → computes SHA-256 → verifies expected hash if provided. No Android dependencies.
- **`AdhanDownloadRepository`** (@Singleton): owns the download lifecycle (mutex-serialized, one download at a time), cancel/retry/delete, downloaded-ids persistence via DataStore (`downloadedAdhanIds`), partial-file cleanup on cancel/fail.
- **`AdhanPlaybackSourceResolver`**: resolves a voice id to a playable source — `RawResource` for bundled, `LocalFile` for downloaded, falls back to the default recording when a remote voice is not downloaded (Phase 13 §3 honest fallback preserved).
- **`AdhanPreviewController`**: streams remote recordings from their HTTPS URL for preview-before-download; plays local files for downloaded ones.
- **`AdhanPlaybackService`**: resolves the fired prayer's voice through the same source resolver → bundled resource / downloaded file / DEFAULT fallback (never silence, never unverified audio).

## 3. UI — the library screen

Settings → "أصوات الأذان لكل صلاة" → tap any prayer card → **library screen**:
- Search field (name, description, mosque, mu'adhin — AR/EN).
- Category chips: الكل · مكة المكرمة · المدينة المنورة · مؤذنون مشهورون · مناطق أخرى · مدمجة.
- "المحمّلة فقط" filter.
- Recording cards: name (Amiri), mosque/mu'adhin subtitle, description, duration · license, download size, preview Play/Pause with elapsed/total, download controls (Download / progress % + Cancel / Retry after failure / Delete), Select button (disabled for undownloaded remote entries with the explanation "حمّله أولاً — الأذان المجدول دون اتصال يحتاج الملف").
- Makkah filter → honest "no verified-rights recording available yet" message.

## 4. Verification

| Scenario | Coverage |
|---|---|
| Download success (SHA-256 computed + size verified) | `AdhanFileDownloaderTest.successful download computes the sha256` [AUTO] |
| Size mismatch → fail + partial cleanup | `size mismatch fails...` [AUTO] |
| Corrupted non-audio → fail | `corrupted non-audio payload is rejected` [AUTO] |
| Wrong expected SHA-256 → fail | `wrong expected sha256 fails with mismatch` [AUTO] |
| HTTP 404 → fail cleanly | `http 404 fails cleanly` [AUTO] |
| Distinct hashes for distinct payloads | `different downloads produce different persisted hashes` [AUTO] |
| Library UI (search, chips, filters, remote rows, download controls) | Live on emulator (screenshots 01–03) |
| Per-prayer selection | `PrayerVoicePreferencesTest` (5 tests) [AUTO] |
| Remote fallback → default | `AdhanVoiceResolverTest` + `remoteRecordingIdsFallBackToDefaultResource` [AUTO] |
| Preview lifecycle | Phase 13 tests unchanged + Phase 15 streaming path |
| Emulator download attempt | Wikimedia rate-limits the emulator connection (429) — architecture verified via local HTTP tests; real-device download expected to work on consumer networks |

## 5. Known limitations

- **Emulator download limitation**: Wikimedia rate-limits (HTTP 429) the emulator's connection to upload.wikimedia.org, preventing the live Madinah download from completing on this emulator. The architecture is fully verified via local-HTTP JVM tests (same code path, different URL). Real-device downloads on consumer networks should not hit this limit.
- Remote entries stream for preview before download — this requires an internet connection for the preview (the scheduled Adhan uses the offline downloaded file).
- The Casablanca recording is ~31 MB — prominently shown before download.
- Makkah audio remains unavailable until verified redistribution rights are obtained.
- No physical-device download verification performed.

## 6. Files created/modified

New: `AdhanCategory`/`AdhanRecording` extensions, `AdhanFileDownloader`, `AdhanDownloadRepository`, `AdhanPlaybackSourceResolver`, `AdhanLibraryScreen`, `AdhanLibraryViewModel`, `AdhanFileDownloaderTest`, `ADHAN-AUDIO-SOURCES.md`, `PHASE-15-ADHAN-LIBRARY.md`, `PHASE-15-TEST-REPORT.md`, `docs/screenshots/phase15/`.
Modified: `BundledAdhanCatalog` (categories + remote entries), `PrayerPreferencesRepository` (downloadedAdhanIds), `AdhanVoiceScreen` (routes to library screen), `AdhanPlaybackService` (source resolver), `AdhanPreviewController` (streaming), `AndroidManifest.xml` (INTERNET), strings ×2, `AdhanAudioInstrumentedTest` (remote fallback), `AyahApp` (library route).
