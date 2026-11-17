# Ayah — Phase 2 Report (Quran Engine)

Date: 2026-11-17 · Status: **complete — dataset verified, imported, and
verified end-to-end on the emulator**

## 1. Dataset (source, integrity, license)

- **Source:** Tanzil.net Quran Text, **Uthmani, Version 1.1** (Feb 2021),
  downloaded 2026-11-17 directly from tanzil.net, together with the official
  `quran-data.xml` metadata (114 surah entries + 30 juz boundaries).
- **Page/juz layout:** per-ayah Madani mushaf page + juz values from the
  quran.com v4 API (KFGQPC Madani layout), bundled as `ayah-metrics.txt`.
- **SHA-256 checksums** (recorded in docs/QURAN_DATA.md):
  - `quran-uthmani.txt` → `bf4f57b9…73312c8`
  - `quran-data.xml` → `59da2595…683cec03`
  - `ayah-metrics.txt` → `8476c68b…5ab4b77804`
- **License:** Creative Commons Attribution 3.0 (Tanzil). Terms honored by
  shipping the file verbatim **including its embedded copyright block**,
  attributing Tanzil in Settings → Licenses, and never modifying the text.
- **Cross-check:** all 6,236 ayahs compared against quran.com's
  `text_uthmani` (independent KFGQPC-Madani lineage): **content-identical
  6236/6236**; differences are exclusively the three documented encoding
  conventions (tatweel before dagger-alef; denser optional waqf marks;
  bismillah placement incl. the authentic 95/97 shadda variant). KFGQPC's
  own site was unreachable (404) at the time; documented.
- The automated cross-check runs as part of the JVM test suite
  (`QuranDatasetIntegrityTest`), so text integrity is continuously enforced,
  not a one-off script.

## 2. What was implemented

**Data layer**
- `QuranTextParser`: strict three-file parser (text + metadata + metrics)
  that cross-validates sequence, per-surah counts, global ids derived from
  XML start indices, juz/page bounds, revelation types; throws
  `QuranParseException` on ANY anomaly. Test-only constructor allows small
  expectations for line-level unit tests; production uses canonical counts.
- `QuranImporter`: first-launch import on an application-scoped coroutine.
  Validate-before-write; single Room transaction (atomic — DB is empty or
  complete); idempotent (populated DB short-circuits); chunked progress
  reporting; success reported only after post-insert count verification;
  failures surface as `QuranImportState.Failed` with retry. Never blocks
  the main thread.
- Repositories + Hilt bindings: `QuranRepositoryImpl`,
  `BookmarkRepositoryImpl` (toggle semantics via unique-index + IGNORE),
  `ReadingProgressRepositoryImpl` (single-row upsert).
- Room schema v2 (`surahs.nameEnglish` added); destructive migration
  documented as pre-release policy.

**Domain layer**
- `QuranImportState` model; `QuranRepository`, `BookmarkRepository`,
  `ReadingProgressRepository` interfaces; 12 use cases (surah list, search,
  surah/ayah lookup, import state + trigger, bookmark observe/toggle/list,
  reading position observe/save).

**UI layer (Compose)**
- **Surah list:** all 114 surahs in mushaf order, Amiri Arabic names,
  revelation type + ayah count, name search (Arabic/transliterated/English),
  distinct loading (import progress %), failed (+retry), empty/no-match
  states.
- **Reader:** RTL Amiri ayah text (explicit RTL paragraph direction, tall
  line height so diacritics never clip), dataset-derived bismillah header
  (mushaf convention incl. 95/97 variant), Arabic-Indic ayah badges ﴿٢٥٥﴾,
  surah header card with Meccan/Medinan · ayah count · Juz · page range,
  prev/next surah (back-stack-safe replace), per-ayah bookmark toggles with
  live state.
- **Bookmarks screen:** joined verse text + surah names, tap-to-open at the
  ayah.
- **Home:** live "Continue Reading" (surah + ayah, tap to restore).
- **Navigation:** reader (`reader/{surahId}?ayah={n}`) and bookmarks stack
  destinations on top of tab state.

**Tests**
- JVM (22 total, all green): 10 `QuranDatasetIntegrityTest` — run the REAL
  bundled dataset through the parser and enforce every Madani invariant
  (114/6236/30/604, Fatiha 7, Baqarah 286, continuity, juz-boundary match,
  Ayat al-Kursi page 42/juz 3, non-empty text) **plus the full 6236-ayah
  cross-check** against the quran.com reference; 5 malformed-input tests;
  7 pre-existing Phase 1 tests.
- Instrumented (8 total, all green on the emulator): real import into
  in-memory Room from the real assets → verified counts, idempotency, id
  continuity, surah/ayah reference resolution, bookmark uniqueness/delete/
  join query, reading-progress single-row upsert.

## 3. Build & test results

| Check | Result |
|---|---|
| `gradlew assembleDebug` | ✅ 66.8 MB APK (incl. 1.5 MB dataset assets) |
| `gradlew assembleRelease` | ✅ 3.65 MB APK (R8 + shrinkResources), unsigned |
| `gradlew testDebugUnitTest` | ✅ 22 tests, 0 failures |
| `gradlew connectedDebugAndroidTest` (ayah_avd, API 36) | ✅ 8 tests, 0 failures, 0 skipped |
| Emulator run | ✅ see below |

## 4. Emulator verification (manual, ayah_avd API 36)

1. Fresh install → launch → import log: "dataset parsed: 114 surahs, 6236
   ayahs" → "import completed: 114 surahs, 6236 ayahs"; zero fatal
   exceptions.
2. Surah list renders all surahs with Arabic names, counts, revelation
   types (screenshot `phase2-surah-list.png`).
3. Al-Fatiha opens: Uthmani text with full diacritics, no bismillah header
   (correct — it is ayah 1), ornate Arabic-Indic ayah numbers
   (`phase2-reader-fatiha.png`).
4. Al-Baqarah: long ayahs wrap correctly in RTL, no clipping
   (`phase2-reader-baqarah.png`).
5. Bookmark tapped on 2:3 → Bookmarks screen shows "Al-Baqara 2:3" with the
   joined verse text (`phase2-bookmarks.png`); the reader row shows the gold
   filled bookmark state (`phase2-continue-restored.png`).
6. Continue Reading card went live: "Surah Al-Baqara · Ayah 2"
   (`phase2-home-continue-reading.png`).
7. `am force-stop` → relaunch → Home still shows the saved position, zero
   crashes (`phase2-home-after-restart.png`); tapping Continue restores the
   reader scrolled to the saved ayah (`phase2-continue-restored.png`).

Screenshots: `docs/reports/screenshots/phase2-*.png`.

## 5. Known limitations / notes

1. **Bismillah display convention** strips the inline prefix at render time
   for first ayahs (per Tanzil's documented convention). The DATABASE text
   is never modified; verified by the integrity tests.
2. **Whole-mushaf single surah list** — no juz/page browsing indexes yet;
   the reader shows juz/page info per surah. A mushaf-page browsing mode is
   a future enhancement (Phase 4 candidate).
3. **Search** covers surah names (as specified); full-text ayah search is a
   future enhancement.
4. **Destructive migration** is active until first release (documented;
   no older user database exists).
5. **Release APK unsigned** (Phase 6).
6. Emulator rendering uses software GPU (swiftshader); Arabic shaping is
   HarfBuzz-driven and identical on hardware, but physical-device checks
   (Samsung/Xiaomi skins) remain part of the Phase 6 compatibility pass.

## 6. Next phase (Phase 3 — Wallpaper Engine)

1. `WallpaperRenderer`: Bitmap/Canvas rendering of verse + reference over
   template backgrounds; Amiri/Uthmani typography; visual inspection on
   device (infrastructure from Phase 1/2 screenshots is reused).
2. Template engine: 5 premium templates (Emerald Gold, Midnight Black,
   Royal Navy, Desert Sand, Minimal Cream) driven by structured data.
3. `AyahSelector`: random/sequential/daily with recent-verse avoidance and
   surah inclusion/exclusion.
4. `WallpaperManager` integration: FLAG_SYSTEM / FLAG_LOCK / both, with
   error handling and OEM-behavior documentation.
5. WorkManager scheduling: unique periodic work, 30 min–24 h intervals
   (default 1 h), preference-driven rescheduling, battery/Doze notes.
6. Wallpaper history + status surface in the Wallpapers screen.
