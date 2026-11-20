# 06 — Content Sources, Authenticity & Licensing Matrix

## 1. Non-Negotiable Religious & Legal Standards

1. **Zero Fabrication**: Never generate, paraphrase, or infer Hadith texts, authenticity grades, narrator chains, or Adhkar wordings using AI.
2. **Zero Unlicensed Scraping**: Never copy datasets from repositories lacking an explicit license (`BelalYehiaSwilam0/quran-app`, `rn0x/Adhkar-json`, `AhmedBaset/hadith-json` all have `licenseInfo: null` on GitHub).
3. **Preserve Integrity**: Religious texts must remain verbatim with verified diacritics (`tashkeel`), complete wordings (never truncated like the reference repo's `m5`), and intact scholarly attribution.
4. **Separate Classical Public Domain vs. Modern Copyright**:
   - Classical Arabic primary texts authored before ~1400 CE (e.g., Sahih al-Bukhari, Sahih Muslim, Imam An-Nawawi's 40 Hadith) are in the **Public Domain** worldwide.
   - Modern English translations, modern digital compilations, modern scholarly explanations (`Sharh`), and audio recordings carry **active copyright** unless explicitly licensed or dedicated.

---

## 2. Verified Source & Licensing Matrix

| Content Domain | Candidate Source | Verified License / Terms | Arabic Text | English Text | References & Grading | Offline Bundling Verdict | Mandatory Conditions / Risks |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Quranic Duas** | **Ayah's existing Tanzil Uthmani DB** (`assets/quran/quran-uthmani.txt`) | Already licensed & verified in Ayah (CC-BY 3.0 Tanzil) | Full verified Uthmani | Existing localized Surah name + ref (English verse translation optional/deferred) | Exact `Surah:Ayah` pointer into `ayahs` table | **APPROVED (Tier 1)** | Zero new copyright surface; store curated `(surahId, ayahNumber, topic)` index only. |
| **Hadith (Bilingual + Grading + Sharh)** | **HadeethEnc.com** (Encyclopedia of Translated Prophetic Hadiths) | **Free Educational / Digital Redistribution Terms** (verified via official HadeethEnc docs) | Scholar-verified Arabic with tashkeel | Scholar-verified English (+50 languages) | Authentic books, explicit grade (`Sahih`/`Hasan`), scholar attribution, concise `Sharh` & word meanings | **APPROVED (Tier 1 — Primary)** | **4 strict conditions**: (1) **Zero modification, addition, or deletion** of texts/translations/explanations; (2) Explicit attribution to `HadeethEnc.com`; (3) **Store & display translation version number** intact; (4) Report discovered errors to HadeethEnc. |
| **40 Hadith An-Nawawi & Classical Arabic Books** | **fawazahmed0/hadith-api** (`ara-nawawi`, `ara-bukhari`, `ara-muslim`, etc.) | **The Unlicense** (verified via GitHub API `licenseInfo.key = "unlicense"`) | Classical Arabic Matn (Public Domain + Unlicense JSON) | **CAUTION** — English editions (`eng-*`) include modern 20th-century translations (e.g., Khan, Siddiqi, Darussalam) whose upstream copyright waivers are **not formally documented** | Book, section, hadith number, multi-scholar grades in `info.json` | **APPROVED FOR ARABIC ONLY**; **HOLD ON ENGLISH EDITIONS** until upstream translator license confirmed | Bundle `ara-nawawi` cleanly (Public Domain text + Unlicense JSON); for bilingual English Hadith, use **HadeethEnc.com** instead of `fawazahmed0` English editions unless translator clearance is verified. |
| **Morning & Evening Adhkar (AR + EN)** | **Seen-Arabic/Morning-And-Evening-Adhkar-DB** | **MIT License** (verified via GitHub API `licenseInfo.key = "mit"`, actively maintained Nov 2026) | Full Arabic text with tashkeel | English translation + transliteration | `order`, `count`, `fadl` (virtue), `source` | **APPROVED (Tier 1)** (cross-verify every entry verbatim against Hisn al-Muslim) | Include MIT copyright notice in Ayah's Licenses sheet (`SettingsScreen`); audit every text entry against authoritative Hisn al-Muslim text before freezing asset. |
| **Complete Hisn al-Muslim (Adhkar & Prophetic Duas)** | **Hisn al-Muslim** (Sheikh Sa'id ibn Ali ibn Wahf al-Qahtani) via MIT-licensed structured repos (`asellam/HisnElMuslim`, `YousefAsalya/Islamic-Pro-azkar-API` JSON **text only**) | **Author Waqf / Free Non-Commercial Condition** + **MIT License** on JSON packaging (`licenseInfo.key = "mit"`) | Full 132 chapters / 185+ adhkar | `YousefAsalya` provides English JSON (`en.json`, MIT) — **must audit English translator provenance** | Original Hisn al-Muslim chapter citations & repeat counts | **APPROVED FOR FREE APP (Text Only)** — with mandatory text & English provenance audit | **Critical condition from author**: Free of charge, **zero deletion/addition/abridgment** of any dhikr text, no paid paywall or ads tied to the book. **DO NOT use bundled MP3 audio files** from `YousefAsalya` (audio recording licenses unverified). |
| **Reference Repo (`BelalYehiaSwilam0/quran-app`)** | GitHub repo `js/adhkar.js`, `data/*.js` | **NO LICENSE (`null`)** — All Rights Reserved | Truncated & incomplete | None | Incomplete/inconsistent | **REJECTED** | Do not copy any file or text. |
| **rn0x/Adhkar-json** | GitHub repo | **NO LICENSE (`null`)** — All Rights Reserved | Arabic + unverified MP3s | None | Partial | **REJECTED** | No license file; unverified audio copyright. |
| **AhmedBaset/hadith-json** | GitHub repo | **NO LICENSE (`null`)** — All Rights Reserved | 50,884 hadiths | English included (unverified upstream copyright) | Numbers & chapters | **REJECTED** | No license file; upstream English rights unclear. |
| **Sunnah.com API** | Official Sunnah.com API (`sunnah-com/api`) | Requires API key; restricts bulk offline republishing/scraping without explicit permission | Yes | Yes | Comprehensive | **NOT SUITABLE FOR OFFLINE BUNDLING** | Terms discourage bulk offline redistribution without explicit written approval; use HadeethEnc.com for offline-first architecture instead. |
| **Adhkar Audio Recordings** | Various community MP3 packs (`Hisn al-Muslim` audio) | **Unverified per-reciter recording copyright** | N/A | N/A | N/A | **DEFERRED (Requires Clearance)** | Never ship MP3s without explicit CC0/CC-BY/public-domain or written reciter license (matching Ayah's strict Phase 15 Adhan Wikimedia Commons standard). |

---

## 3. Recommended Content Architecture & APK Size Budget

To keep Ayah **offline-first, authentic, and lean** (current signed release APK is `13.0 MB`):

1. **Bundled Offline Core (Ships inside APK `assets/companion/`, estimated `+1.2 MB` to `+2.0 MB` compressed)**:
   - `assets/companion/adhkar_hisn.json` (~250 KB): Complete, unabridged Morning, Evening, After-Prayer, Sleep, Waking, Wudu, Mosque, Food, Home, Travel, Healing, Anxiety, and Situational Adhkar & Prophetic Duas, cross-checked verbatim against *Hisn al-Muslim* with full references and repeat counts.
   - `assets/companion/quranic_duas.json` (~15 KB): Curated index of 40–60 authentic Quranic Duas storing ONLY `(id, surahId, startAyah, endAyah, category, titleAr, titleEn)` — zero duplicated Quran text (joined at runtime with `AyahEntity`).
   - `assets/companion/hadith_starter.json` (~900 KB – 1.5 MB):
     - **40 Hadith An-Nawawi** (42 complete hadiths, Arabic + HadeethEnc verified English + explanation + grading + version metadata).
     - **HadeethEnc Curated Essential Thematic Pack** (~300–500 high-value authentic hadiths across Faith, Worship, Dhikr & Dua, Character, Family, Repentance, Patience — Arabic + verified English + grading + grader + concise `Sharh` + HadeethEnc version tag).
2. **Optional On-Demand Offline Hadith Packs (Phase C-4b / Post-Core, Zero APK Bloat)**:
   - Larger classical books (`Sahih al-Bukhari` ~7,500 hadiths, `Sahih Muslim` ~7,500 hadiths, `Riyad as-Salihin` ~1,900 hadiths) downloadable per book over HTTPS into `filesDir/companion_packs/` with SHA-256 verification and per-book delete — mirroring Ayah's existing `QuranAudioDownloadRepository` pattern!
3. **Update Mechanism & Integrity Verification**:
   - Every bundled JSON asset carries a top-level manifest header: `{ "datasetId", "sourceName", "sourceUrl", "license", "version", "recordCount", "sha256" }`.
   - Automated JVM integrity tests (mirroring `QuranDatasetIntegrityTest`) verify non-empty Arabic text, zero truncation markers, valid repeat counts, valid `(surahId, ayahNumber)` pointers for Quranic Duas, non-empty attribution & version fields on every Hadith, and exact count assertions.

---

## 4. Attribution & Licenses Screen Integration

Ayah's `SettingsScreen` already includes a dedicated **Licenses & Attribution** modal bottom sheet (`showLicensesSheet`). When Companion datasets are added, register explicit entries for:
- **HadeethEnc.com** — Encyclopedia of Translated Prophetic Hadiths (with dataset version string and link).
- **Hisn al-Muslim** — Sheikh Sa'id ibn Ali ibn Wahf al-Qahtani (non-commercial free distribution acknowledgment).
- **Seen-Arabic / MIT JSON Contributors** — Exact MIT license text and copyright notices.
- **fawazahmed0/hadith-api** — The Unlicense notice (if Arabic Nawawi JSON is used).

---

## 5. Open Licensing Items Requiring Confirmation Before Import

1. **HadeethEnc.com Snapshot Capture**: Download an official unmodified snapshot via HadeethEnc API/export, record its exact `version` number per language (`ar`, `en`), and ensure Ayah's importer performs **zero text rewriting** to honor HadeethEnc Condition #1 strictly.
2. **English Translation Provenance for Hisn al-Muslim**: Before bundling `en.json` from `YousefAsalya/Islamic-Pro-azkar-API`, verify whether the English translation is the public-domain/waqf translation by Darussalam/Wahiduddin or requires separate attribution. `Seen-Arabic/Morning-And-Evening-Adhkar-DB` (MIT) covers Morning/Evening AR+EN cleanly; other chapters can start Arabic-first with verified English added only once translator licensing is confirmed.
3. **Adhkar Audio**: Confirmed deferred until CC-licensed or explicitly permitted recordings are identified.
