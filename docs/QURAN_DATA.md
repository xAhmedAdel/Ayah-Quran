# Ayah — Quran Data Contract

Status: **Phase 2 — dataset acquired, verified, and imported.**

## Non-negotiable rules (enforced in code)

1. **No Quranic text is ever generated, paraphrased, or "corrected" by AI.**
   The database is populated exclusively from the bundled verified dataset.
2. The Uthmani text is stored **verbatim** — diacritics, small Quranic marks,
   alef variants, waqf signs are preserved byte-for-byte. No normalization,
   no trimming inside ayah text, no reordering. (Reader display conventions —
   e.g. showing the bismillah as a header — are presentation-only; the stored
   text is never modified.)
3. Every verse row carries its `(surahId, ayahNumber)` reference and a global
   id (1..6236) derived from the dataset's own start indices; integrity is
   validated before and after import (see tests).

## Bundled dataset (the imported source)

| File (app assets, `quran/`) | Content | SHA-256 |
|---|---|---|
| `quran-uthmani.txt` | Tanzil Quran Text, **Uthmani, Version 1.1** — full verbatim copy including its embedded copyright block | `bf4f57b968d03f4131c070b1e285da9be0e0a108a21c910e872801ca273312c8` |
| `quran-data.xml` | Tanzil Quran metadata (114 surah names/types/counts, 30 juz boundaries) | `59da25958547318e524793563ea6a233144ce04513862563bc604d8f683cec03` |
| `ayah-metrics.txt` | Per-ayah juz + Madani mushaf page (`surah\|ayah\|juz\|page`), derived from the quran.com v4 API (KFGQPC Madani layout) | `8476c68b7cc7fcb44b94f29ef17be0246c695251b670c7580ce4825ab4b77804` |
| `rub-el-hizb.txt` | 240 Rub' al-Hizb (Quarter-Hizb) starting boundaries (`rub\|surah\|ayah`), derived from the quran.com v4 API (`/api/v4/rub_el_hizbs`, KFGQPC Madani layout) | `840104f1947e3585f2067f5a70bd5790fdcc85575b2f50889d4d9a3eaab78cf4` |

Downloaded from **tanzil.net** (text generated via `tanzil.net/pub/download/`,
metadata at `tanzil.net/pub/sample/quran-data.xml`), release **Version 1.1
(February 2021)**, on 2026-11-17.

## License & attribution (Tanzil, CC-BY 3.0)

The embedded copyright block in `quran-uthmani.txt` states:

- Tanzil Quran Text (Uthmani, Version 1.1), Copyright (C) 2007–2026 Tanzil
  Project. **License: Creative Commons Attribution 3.0.**
- Terms: verbatim copying allowed; **changing the text is not allowed**; the
  source (Tanzil Project) must be clearly indicated with a link to
  tanzil.net; the copyright notice must accompany all copies and derived
  files.

Ayah complies by: shipping the file verbatim **including the copyright
block**; displaying the attribution in Settings → Licenses; never modifying
the text.

Page/juz/rub layout data from quran.com (public API, Madani mushaf layout) is
factual structural division data, attributed here and in the app. Note that
quran.com does not publish a separate formal license identifier for its API v4
`/rub_el_hizbs` structural endpoint; Ayah stores only the factual numeric
boundaries (`rub|surah|ayah`) with clear source attribution and cross-checks
against Tanzil full metadata (`tanzil.net/res/text/metadata/quran-data.xml`,
CC-BY).

## Rub' al-Hizb (Quarter-Hizb) Metadata Verification (2026-11-21)

- **Selected source:** `quran.com` API v4 `/api/v4/rub_el_hizbs` (KFGQPC Madani
  Mushaf layout), stored as `app/src/main/assets/quran/rub-el-hizb.txt`
  (240 sequential entries `1..240`, mapping to 60 Hizbs of 4 quarters each and
  30 Juz of 8 quarters each; Rub' 1 = `1:1`, Rub' 240 = `100:9`).
- **Cross-check against Tanzil full metadata (`quran-data.xml` v1.0, CC-BY):**
  239 of 240 starting points are byte-for-byte identical between `quran.com`
  and Tanzil.
- **Documented discrepancy — Rub' 106 (Hizb 27, Juz 14, Surah Al-Hijr):**
  - Tanzil full metadata (`v1.0`) places Rub' 106 at **`15:50`**.
  - `quran.com` API v4 (`/rub_el_hizbs`) places Rub' 106 at **`15:49`**.
  - **Cross-check against printed ۞ (`U+06DE`) quarter markers:** In the
    bundled `quran-com-verses.tsv` reference dataset, `U+06DE` appears on 199
    mid-surah verses (the remaining 41 quarters begin at Surah openings where
    printed Madani Mushafs omit the inline `۞` glyph). For Rub' 106, the
    printed `۞` marker sits directly on **`15:49`**
    (`۞ نَبِّئْ عِبَادِىٓ أَنِّىٓ أَنَا ٱلْغَفُورُ ٱلرَّحِيمُ`), not `15:50`.
  - **Selection rationale:** **`15:49`** was selected so Rub' navigation aligns
    consistently with the Madani Mushaf layout (`ayah-metrics.txt`) and printed
    quarter markers already used throughout Ayah.
- **Validation methodology:** Automated JVM tests (`RubElHizbCatalogIntegrityTest`)
  verify all 240 entries against `quran-uthmani.txt`, `quran-data.xml`,
  `ayah-metrics.txt`, and `quran-com-verses.tsv` (exact count 240, sequential
  1..240, strict chronological Quran order, no duplicates/gaps, valid surah and
  ayah existence, exact Juz alignment `ceil(rub/8)`, Juz start alignment at
  quarters `1, 9, 17, ..., 233`, and 100% agreement with the 199 mid-surah
  `U+06DE` markers).
- **Verification limitation:** Direct machine-readable downloads from
  `qurancomplex.gov.sa` remained unavailable (HTTP 404 at prior verification),
  so KFGQPC Madani Mushaf alignment is verified through `quran.com`'s
  KFGQPC-aligned structural API and embedded `U+06DE` marks rather than a
  direct KFGQPC official metadata feed.

## Verification performed (2026-11-17)

1. **Structural invariants (automated, both in JVM tests and in the
   post-import verification):**
   114 surahs · 6,236 ayahs · 30 juz · 604 pages · Al-Fatiha 7 ·
   Al-Baqarah 286 · sequential surah ids · continuous global ids 1..6236 ·
   per-surah counts match metadata · juz boundaries match ayah juz values ·
   no empty text · pages ⊆ 1..604.
2. **Independent cross-check:** all 6,236 ayahs of the Tanzil text were
   compared against **quran.com's `text_uthmani`** (a second, independently
   maintained dataset following the KFGQPC Madani mushaf).
   Result: **content identical for all 6,236 ayahs**; the only differences
   are three documented encoding conventions:
   - quran.com inserts tatweel (U+0640) before the dagger alef (U+0670);
   - quran.com carries additional optional waqf/section demonstration marks
     (U+06D6..U+06DC variants, U+06DE, U+06E9) — Tanzil contains no codepoint
     that quran.com lacks;
   - bismillah placement: Tanzil prefixes it to the first ayah (including the
     authentic shadda variant used in surahs 95/97), quran.com excludes it.
3. **Spot checks:** Ayat al-Kursi (2:255) = page 42, juz 3 (matches the
   published Madani mushaf). Standard bismillah and its 95/97 variant
   verified against the physical mushaf conventions.
4. **KFGQPC direct download:** attempted (qurancomplex.gov.sa); the site
   returned 404 at the time of work. The quran.com text — which follows the
   KFGQPC Madani mushaf — served as the independent cross-check lineage.

The automated cross-check lives in
`app/src/test/.../QuranDatasetIntegrityTest.kt` with the reference copy at
`app/src/test/resources/quran/quran-com-verses.tsv`
(SHA-256 `51c2962b1ec2b43f0808bf1089e712ed1fcb19f880bd84456938f717a583508c`).

## Import behavior

- Runs automatically at first app launch (background scope, never blocks
  the main thread); `QuranViewModel` also offers an explicit retry.
- The dataset is parsed and validated BEFORE any database write; all inserts
  run in a single Room transaction — the database is either empty or fully
  imported, never partial.
- Idempotent: a fully populated database short-circuits.
- Success is reported only after post-insert count verification; failures
  surface as `QuranImportState.Failed` and the UI shows an honest error
  state with retry.
