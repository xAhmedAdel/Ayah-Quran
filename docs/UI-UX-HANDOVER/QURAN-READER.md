# Quran Reader — Special Documentation (⚠️ highest-risk area)

## Files that render the mushaf (do NOT casually replace)

| File | Role |
|---|---|
| `ui/reader/MushafPageReaderScreen.kt` | page-mode screen: HorizontalPager over 604 pages, top bar (back/bookmark/play/continuous-switch/auto…), zoom, swipe, jump dialog |
| `ui/reader/MushafPageViewModel.kt` | page loading (`ensureLoaded` + 2-page neighborhood), settled-page persistence, highlight merge, play-from-ayah, recitation follow evaluator |
| `ui/reader/MushafText.kt` | builds the page/surah `AnnotatedString` with per-ayah tap character-ranges + highlight spans (the ONLY place text spans are built) |
| `ui/reader/ReaderScreen.kt` | continuous reader (vertical scroll, single AnnotatedString per surah, selection, play-from-ayah bar, auto-scroll toggle) |
| `ui/reader/ReaderViewModel.kt` | continuous state (ayahs, selection, playingAyah, auto-scroll, font scale, reading position) |
| `core/quran/*` + `data/quran/QuranImporter.kt`, `QuranTextParser.kt` | import + parse of `assets/quran/quran-uthmani.txt` (Tanzil Uthmani), `quran-data.xml`, `ayah-metrics.txt` into Room |
| `domain/usecase/GetMushafPageUseCase.kt` | authentic page content (FIRST_PAGE=1..LAST_PAGE=604) |
| `domain/usecase/PageForAyahUseCase.kt` | ayah→page mapping (Room) — THE source of truth |
| `assets/quran/` | the datasets themselves |

## Data model facts

- Ayah numbering: Hafs global 1..6236; per-surah counts table in
  `data/audio/QuranAudioCatalog.AYAH_COUNTS` (sums 6236, JVM-tested).
- Pages: 604, loaded on demand (current ±2 neighbors) into an in-memory
  map; settled page persisted as reading position (first ayah of page).
- Highlight: character-range spans inside one AnnotatedString — taps are
  resolved by character range → ayah (`onAyahTap(range)`).

## Behavior inventory (slice 1–3 — all must keep working)

1. **Page mode**: HorizontalPager, RTL swipe, pinch-zoom (disables paging
   while zoomed), double-tap reset, page jump dialog (validates 1..604),
   page indicator "N من 604", prev/next buttons.
2. **Ayah tap** = select/highlight (page mode) or select + play bar
   (continuous).
3. **Play from ayah**: page mode ▶ in top bar (from highlight); continuous
   "تشغيل التلاوة من الآية X" button under the text.
4. **Recitation highlight**: effective highlight = user selection ?: the
   recited ayah (`AyahPlaybackState` → `playingAyahFor(surahId)`).
5. **Auto page-follow** (slice 3): `MushafFollowPolicy` — one navigation
   per recited ayah; manual browsing respected (no yank for the same
   ayah); smooth `animateScrollToPage` after `ensureLoaded`.
6. **Auto-scroll** (continuous): toggle in top bar; follows recited ayah.
7. **Reading position**: saved on page settle / visible-ayah change;
   drives Home "متابعة القراءة".
8. **Bookmarks**: per-ayah, toggle from reader actions + bookmarks screen.
9. **Mode switch**: continuous ↔ mushaf preserving (surahId, ayah).

## Safe vs unsafe to redesign

**Safe**: top bars, action buttons placement/appearance, page indicator
styling, dialogs styling, font-size control UI, bookmark flow UI, list
screens, mini player, colors/typography of chrome, reader backgrounds.

**NOT safe to change**: `MushafText`'s AnnotatedString construction and
character-range tap mapping; the 604-page data; `PageForAyahUseCase`;
Room entities for pages/ayahs; ayah numbering; the follow-policy
semantics (`MushafFollowPolicy`); reading-position persistence; the
`AyahPlaybackState` contract consumed here.

**Test coverage to keep green**: `MushafPageReaderUiTest` (6 tests:
open-on-page-one, prev/next, swipe direction, pinch-zoom boundaries,
double-tap reset, jump dialog validation), `AppUiTest` (continue-reading),
plus policy tests in JVM.
