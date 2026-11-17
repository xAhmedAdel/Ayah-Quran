# Phase 11 — Authentic Madinah Mushaf Pagination & Page-Based Reader

**Status: COMPLETE** — the Quran reader now offers an **authentic 604-page Madinah mushaf mode** whose page boundaries come from the verified Madani page mapping (never invented), alongside the preserved adjustable-text continuous reader. Full regression green on the final code: **64 JVM + 48 instrumented = 112 tests, 0 failures; debug and signed release builds pass.**

---

## 1. Honest accuracy statement (read this first)

What this implementation **is**:

- A **604-page mushaf reader with authentic page boundaries**: every page begins and ends exactly where the printed Madinah mushaf begins and ends (verified per-ayah mapping), with correct surah-start headers, bismillah placement, and page numbering (صفحة N من 604 / Page N of 604).
- What this implementation is **not**: a line-for-line reproduction of the printed pages. The original line breaks, word/glyph positions, and the King Fahd Complex mushaf typeface are **not** part of the verified bundled data, and no adequately licensed line-layout source could be verified (details below). Each page is rendered as continuous justified Uthmani text inside its authentic boundaries. **The app never claims exact print reproduction**, and the reader's docstrings say so explicitly.

## 2. Source investigation results

### Already in the project (used by this phase)

| Data | Origin | License | Verification |
|---|---|---|---|
| Per-ayah Madinah page mapping (`surahId\|ayahNumber\|juz\|page`) | quran.com v4 API, KFGQPC Madani layout (bundled since Phase 2 as `ayah-metrics.txt`) | Documented in `docs/QURAN_DATA.md`; layout data from quran.com public API | Re-validated this phase: 604/604 pages covered, zero non-monotonic transitions, landmarks correct (p1 = Al-Fatiha 1–7; p2 starts Al-Baqara 2:1; p604 = Al-Ikhlas+Al-Falaq+An-Nas), all 6,236 ayahs exactly once |
| Uthmani text | Tanzil v1.1 | CC-BY 3.0 | Unchanged since Phase 2 (SHA-256 verified); **not one character was modified** |

The mapping was already imported into Room (`ayahs.page`, indexed) in Phase 2 — this phase added queries, not data.

### Line-layout sources evaluated (NOT bundled)

| Source | Finding |
|---|---|
| **QUL / Tarteel** ([qul.tarteel.ai](https://qul.tarteel.ai), [GitHub](https://github.com/TarteelAI/quranic-universal-library)) | Provides Madani mushaf line data (~9,046 lines). The repo **code** is MIT, but the **dataset license** for mushaf layouts is not clearly stated on the accessible resource pages; third parties attribute it inconsistently (CC-BY 4.0 / public domain). Per this phase's rule — "do not bundle assets whose license cannot be verified" — **not bundled**. |
| **quran_android** ([GitHub](https://github.com/quran/quran_android)) | Code Apache 2.0; Madani data files carry "the various licenses of the data's sources" — not verifiable in detail here. **Not bundled.** |
| **KFGQPC QCF fonts** | Could technically reproduce print layout (glyph-encoded pages) but require a specialized glyph-rendering architecture — out of scope for this phase. |

**Conclusion:** the technically honest path was the prompt's sanctioned fallback: authentic page boundaries + clearly-labeled responsive rendering. If a clearly licensed line dataset is later verified (e.g., QUL publishing explicit terms), line-aware rendering can be added inside the same page architecture without changing navigation or persistence.

## 3. Rendering architecture

- **MUSHAF mode (default)** — `MushafPageReaderScreen`: `HorizontalPager` over pages 1..604 (lazy: only current ±2 loaded, `Map<Int, MushafPage>` in-VM cache; the whole mushaf is never held in memory). Each page is built by `GetMushafPageUseCase` from the DB mapping:
  - ayahs grouped into per-surah **segments** (51 pages span two surahs; each transition renders end-of-surah text → surah header card → bismillah → next surah's text);
  - header + bismillah only when the surah **starts** on that page; bismillah is lifted out of the first ayah per the Tanzil convention (surah 1 = inline first ayah; surah 9 = none) — identical conventions to the continuous reader, asserted by unit tests;
  - **pinch-zoom 1×–4×** + pan, double-tap reset — boundaries never change; at rest scale single-finger drags pass through to the pager (gesture detector consumes only pinch/zoomed pan);
  - navigation: swipe, prev/next buttons (RTL-aware arrows), tap indicator → direct page-jump dialog (validates 1..604), surah entry from the Quran list, ayah entry via bookmarks/continue-reading — all resolve through the same page mapping;
  - position persistence: the settled page's first ayah is saved through the existing `SaveReadingPosition` chain; reopening (or Continue Reading on Home) re-resolves (surah, ayah) → page. Bookmarks stay stable-ID based and open the page containing the ayah with highlight.
- **CONTINUOUS mode** — the Phase 8 surah reader, untouched, still the only place with adjustable font size (mushaf mode has fixed text size + zoom by design; the mode toggle is one tap away).
- **Mode preference** persisted in DataStore (`ReaderSettings.readingMode`, default MUSHAF). Entry points (Quran list, Continue Reading, Bookmarks) route by the saved mode.
- **No database schema change**: "last page" derives from the stored (surah, ayah); reading mode lives in DataStore. Additionally, `fallbackToDestructiveMigration()` was **removed** — with a public beta in the wild, any future schema change now requires an explicit `Migration` instead of silently wiping user data.

## 4. Files created / modified

**New:**
- `domain/model/MushafPage.kt`, `domain/model/ReadingMode.kt`
- `domain/usecase/GetMushafPageUseCase.kt`, `PageForAyahUseCase.kt`, `PageForSurahStartUseCase.kt`
- `ui/reader/MushafPageReaderScreen.kt`, `ui/reader/MushafPageViewModel.kt`
- `test/.../MushafPageMappingIntegrityTest.kt`, `test/.../GetMushafPageUseCaseTest.kt`
- `androidTest/.../MushafPageReaderUiTest.kt`
- `docs/reports/PHASE-11.md`, `docs/screenshots/phase11/`

**Modified:**
- `AyahDao.kt` — page queries (`ayahsOnPage`, `pageForAyah`, `firstPageOfSurah`, `pageStats`)
- `QuranRepository.kt` / `QuranRepositoryImpl.kt` — page lookups
- `ReaderPreferencesRepository.kt` / `ReaderPreferencesRepositoryImpl.kt` — `readingMode`
- `MushafText.kt` — ranges carry `surahId` (multi-surah pages); tap reports the full reference
- `ReaderScreen.kt` — switch-to-mushaf action; localized icon descriptions
- `AyahApp.kt` — `mushaf` route + mode-aware entry points; `MainViewModel.kt` — reading-mode state
- `DatabaseModule.kt` — destructive fallback removed
- `values/strings.xml`, `values-ar/strings.xml` — mushaf strings, both locales

## 5. Verification against reference landmarks (prompt §13)

| Check | Result |
|---|---|
| Page 1 | Al-Fatiha only, bismillah inline as ayah 1, "صفحة 1 من 604" ✅ (screenshot 01) |
| Page 2 (surah start) | Header "البقرة" + bismillah under it; ayah 1 renders "الٓمٓ" with no duplicated bismillah ✅ (02) |
| Page 300 (mid-surah) | Al-Kahf 54+, no header ✅ (04) |
| Page 106 (surah end + start) | An-Nisa 176 ends ﴿١٧٦﴾ → Al-Ma'idah header + bismillah → 5:1 ✅ (08) |
| Page 604 | ends with An-Nas; next disabled ✅ (07) |
| Direct jump dialog | invalid input rejected (button disabled + error), 300 navigates ✅ (03, instrumented) |
| English UI | "Page 106 of 604" — same pages, localized indicator ✅ (09) |
| Position restore | app restart → Continue Reading opens the saved page ✅ (05, 06) |

## 6. Automated test results (final code)

- **JVM: 64 tests, 0 failures** — includes 8 new `MushafPageMappingIntegrityTest` (all 604 pages present; every ayah exactly once; monotonic order; landmarks) and 7 `GetMushafPageUseCaseTest` (segment splitting, bismillah conventions for surahs 1/9/embedded-prefix, verbatim text, out-of-range rejection).
- **Instrumented: 48 tests, 0 failures** — 5 new mushaf tests (open+indicator, prev/next navigation, swipe, pinch-zoom keeps boundaries, jump dialog valid/invalid) plus the updated continue-reading flow test.
- `assembleDebug` + `assembleRelease`: pass.

## 7. Bugs found by testing and fixed during this phase

1. `detectTransformGestures` consumed all touches, blocking pager swipes → replaced with a selective `awaitEachGesture` that consumes only pinch/zoomed pan.
2. Position save raced initial page load (settledPage stable while content was still loading) → save re-runs when page content arrives, deduped in the VM.
3. The initial page jump was consumed by the StateFlow placeholder before the resolved page (e.g. 106) arrived → unresolved pages are now 0 and only real pages trigger the jump.

## 8. Known limitations

- **Not a print reproduction** — line breaks differ from the printed mushaf (see §1). Within-page rendering is continuous justified Uthmani text.
- Zoom is pixel-scaling (`graphicsLayer`), not re-typesetting: at high zoom the effective text becomes a scaled bitmap of the composed page. Boundaries and text remain exact.
- No dedicated in-reader surah/ayah picker dialog yet: surah jumps go through the Quran tab list, ayah jumps through bookmarks/Continue Reading (all mapped to authentic pages). The page-jump dialog covers numeric page selection.
- Application-context language vs. per-activity: unchanged Phase 10 behavior.
- APK size unchanged (+~40 KB of code): page rendering reuses the bundled dataset — no images, no fonts added.

## 9. Remaining manual testing

- Real-device pinch-zoom feel and mid-range device pager smoothness (emulator automation covers logic, not feel).
- Physical-device review of page composition aesthetics vs. a printed mushaf (boundaries verified; line aesthetics are responsive by design).
- RTL arrow ergonomics preference check (prev/next arrow directions chosen for RTL page flow).

## 10. Rules compliance

- Quranic text: **zero modifications** — the same SHA-256-verified dataset; integrity tests still pass.
- No page boundaries were invented; every mapping comes from the bundled verified data.
- No unlicensed assets bundled; license blockers reported honestly (§2).
- No DB schema change; destructive-migration footgun removed instead.
- All prior features preserved (full 48-test instrumented regression covers wallpapers, audio honesty, bookmarks, history, localization).
