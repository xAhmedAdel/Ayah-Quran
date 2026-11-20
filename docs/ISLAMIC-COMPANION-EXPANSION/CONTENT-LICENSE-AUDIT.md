# AYAH — Phase C-6 Content & Asset License Audit

- **Audit Date**: 2026-11-20
- **Branch / Baseline Version**: `main` (`v1.0.0-beta.9`, `versionCode = 10`)
- **Distribution Profile**: Free, non-commercial, ad-free, open-source Android application (`https://github.com/xAhmedAdel/Ayah-Quran`)

---

## 1. Master Content & Asset License Matrix

| # | Bundled Asset / Dataset | Repository Path(s) | Primary Source & Provenance | License / Redistribution Terms | Commercial Use Permitted? | Modification Permitted? | Ad / Monetization Restrictions? | Unresolved Legal Uncertainty? |
|---|---|---|---|---|---|---|---|---|
| 1 | **Quran Text (Uthmani Script, 6,236 Ayahs & 114 Surahs)** | `app/src/main/assets/quran/quran-uthmani.txt`, `app/src/main/assets/quran/quran-data.xml` | Tanzil Project (`https://tanzil.net`) | **CC-BY 3.0** (with Tanzil Quran Text Integrity Terms: verbatim text without alteration) | **Yes** (subject to verbatim text integrity and attribution) | **No** (Quran text must be distributed verbatim without character alteration) | None specified beyond attribution and verbatim integrity | **None** — verified against independent `quran.com` Uthmani reference in `QuranDatasetIntegrityTest` |
| 2 | **Madani Mushaf Page & Juz Metrics (604 Pages, 30 Juz)** | `app/src/main/assets/quran/ayah-metrics.txt` | King Fahd Glorious Quran Printing Complex (KFGQPC) Madani Mushaf layout metadata via `quran.com` | **Factual Page/Juz Boundary Metadata / Open Redistribution** | **Yes** | **Yes** (structural index mapping `(surahId, ayahNumber)` to `(page, juz)`) | None | **None** |
| 3 | **Quranic Duas Pointer Catalog (40 Rabbana & Prophetic Supplications)** | `app/src/main/assets/companion/quranic_duas.json` | Curated Ayah coordinate index (`surahId`, `startAyah`, `endAyah`) resolving at runtime against bundled Tanzil `ayahs` table | **First-Party Pointer Index (`CC0-1.0` / MIT)**; underlying Quran text governed by **Tanzil CC-BY 3.0** | **Yes** | **Yes** (pointer index only; zero duplicated Quran text stored in JSON) | None | **None** — zero Quran text duplication; all 40 ranges verified against `quran-uthmani.txt` |
| 4 | **Morning & Evening Adhkar Dataset (50 Authenticated Items with Arabic + English Translation & Transliteration)** | `app/src/main/assets/companion/adhkar_hisn.json` (`categoryId` in `morning`, `evening`) | `Seen-Arabic/Morning-And-Evening-Adhkar-DB` (curated from Sheikh Sa'id ibn Ali ibn Wahf al-Qahtani's *Hisn al-Muslim*) | **MIT License** (`Copyright (c) 2024 Seen Arabic`) + classical *Hisn al-Muslim* attribution | **Yes** under MIT (subject to *Hisn al-Muslim* author condition below if combined) | **Yes** under MIT (Arabic matn preserved verbatim; zero truncated ellipses) | None under MIT; *Hisn al-Muslim* author condition requires free distribution | **None** for Ayah's free, non-commercial distribution |
| 5 | **Fortress of the Muslim (*Hisn al-Muslim*) Core Daily Chapters (14 Chapters, 77 Items — Arabic-First)** | `app/src/main/assets/companion/adhkar_hisn.json` (`after_prayer`, `sleep`, `waking`, `home_enter_exit`, `mosque`, `wudu`, `food`, `travel`, `distress`, `istikhara`, `illness`, `weather`, `forgiveness_praise`, `clothing`) | Compiled by Sheikh Sa'id ibn Ali ibn Wahf al-Qahtani (d. 1440 AH); structured Arabic JSON extracted from `asellam/HisnElMuslim` | **Author Redistribution Permission** (free, non-commercial, unaltered text) + **MIT License** on JSON structure (`Copyright (c) 2021 Abdellah SELLAM`) | **No** (Sheikh al-Qahtani's print/digital redistribution permission specifies free, non-commercial charity/waqf distribution) | **No** (classical Arabic matn and Hadith attributions must remain unaltered; verified zero synthetic English translations shipped) | **Yes** — must remain free of paywalls, commercial resale, or intrusive monetization | **Low / Scoped** — fully compliant while Ayah remains free, ad-free, and non-commercial. If Ayah ever introduces paid tiers, *Hisn al-Muslim* chapters must remain 100% free and unpaywalled |
| 6 | **Digital Tasbih Standard Presets (7 Core Dhikr Formulas)** | Seeded via `CompanionContentImporter` (`CURRENT_CONTENT_VERSION = 2`) into `tasbih_presets` table | Classical Prophetic Dhikr formulas (*SubhanAllah*, *Alhamdulillah*, *Allahu Akbar*, *La ilaha illa Allah*, *Astaghfirullah*, *SubhanAllahi wa bihamdihi*, *La hawla wa la quwwata illa billah*) | **Public Domain** (classical Islamic liturgical phrases) | **Yes** | **Yes** (plus user-created custom Dhikr rows stored locally on device) | None | **None** |
| 7 | **Forty Hadith of Imam An-Nawawi (`nawawi40`, 42 Hadiths)** | `app/src/main/assets/companion/hadith_starter.json` (`collectionId = "nawawi40"`) | Imam Yahya ibn Sharaf An-Nawawi (631–676 AH / 1233–1277 CE) + Ibn Rajab completion (Hadiths 1–42); digitized Arabic dataset via `fawazahmed0/hadith-api` (`ara-nawawi`) | **The Unlicense / Public Domain** (7th-century AH classical Arabic text + Unlicense digitization) | **Yes** | **Yes** (classical Arabic Hadith matn must be preserved verbatim for scholarly integrity) | None | **None** — shipped Arabic-first without unverified third-party English translation |
| 8 | **Thematic Prophetic Hadith Collection (`hadeethenc_*`, 5 Collections, 160 Hadiths)** | `app/src/main/assets/companion/hadith_starter.json` (`hadeethenc_faith_worship`, `hadeethenc_character_mercy`, `hadeethenc_dhikr_dua`, `hadeethenc_quran_knowledge`, `hadeethenc_family_dealings`) | **HadeethEnc.com** (Encyclopedia of Translated Prophetic Hadiths, Public API v1 snapshot `hadeethenc-api-v1-2026-11-20`) | **HadeethEnc Free Unmodified Attribution Terms** (content may be shared/redistributed free of charge provided text, grading, and explanations are unmodified and attributed to `HadeethEnc.com` with dataset version) | **No** (HadeethEnc content is provided as a free da'wah waqf service and may not be sold) | **No** (Arabic matn, English translation, scholarly explanation `sharh`, and grade must be distributed unmodified) | **Yes** — must not be placed behind paywalls, subscriptions, or commercial ad networks | **Low / Scoped** — HadeethEnc provides explicit permission for free unmodified publication with attribution and version tracking, though it does not use an SPDX OSI license identifier |
| 9 | **World Cities Geolocation Catalog (Offline Prayer Location Search)** | `app/src/main/assets/prayer/cities.json` | GeoNames Gazetteer (`https://www.geonames.org`) | **CC-BY 4.0** | **Yes** | **Yes** | None beyond CC-BY 4.0 attribution | **None** |
| 10 | **Bundled Adhan Audio Clips (2 Bundled MP3 Assets + Optional User-Initiated Wikimedia Downloads)** | `app/src/main/res/raw/adhan_clip_synagda.mp3`, `app/src/main/res/raw/adhan_full_fakhri.mp3` (plus optional downloadable voices from `commons.wikimedia.org`) | Wikimedia Commons: `Adhan.ogg` by `Adam-synagda`; historical recording by Sabah Fakhri; optional downloadable voices by `Andrewler` and `Mahfoudou` | **CC0 1.0** (`Adam-synagda`), **Public Domain** (`Sabah Fakhri`), **CC BY-SA 4.0** (optional downloads `Andrewler`, `Mahfoudou`) | **Yes** | **Yes** (under CC BY-SA 4.0 share-alike terms for optional modified tracks) | None beyond CC BY-SA 4.0 attribution on optional downloadable tracks | **None** |
| 11 | **Bundled Arabic & Latin Typefaces (Amiri & IBM Plex Sans Arabic)** | `app/src/main/res/font/amiri_regular.ttf`, `amiri_bold.ttf`, `ibm_plex_sans_arabic_regular.ttf`, `ibm_plex_sans_arabic_medium.ttf`, `ibm_plex_sans_arabic_semibold.ttf`, `ibm_plex_sans_arabic_bold.ttf` | Amiri Project (`https://www.amirifont.org`, Khaled Hosny); IBM Corp. (`IBM Plex Sans Arabic`) | **SIL Open Font License 1.1 (OFL-1.1)** | **Yes** (when bundled inside an application; font files cannot be sold standalone by themselves) | **Yes** (subject to Reserved Font Name provisions of SIL OFL 1.1) | None | **None** |

---

## 2. Excluded Sources (Explicitly Rejected During Phase 0 & C-1)

To eliminate copyright taint, commercial API Terms of Service violations, or unverified theological content, the following sources from the reference project (`Null-Spectre00/Hisn_AlMuslim_App`) and third-party ecosystems were **explicitly excluded** and have **zero presence** in Ayah:

1. **Sunnah.com Scraped HTML / Undocumented API Exports**: Excluded because Sunnah.com does not grant an open bulk-redistribution license for its proprietary English translations (e.g., Darussalam copyright).
2. **AlQuran.cloud / Islamic Network Quran Text JSON**: Excluded in favor of Ayah's existing locally verified **Tanzil Project Uthmani** asset (`quran-uthmani.txt`).
3. **Unattributed `azkar.json` / `zekr.json` Blobs**: Excluded from `Null-Spectre00/Hisn_AlMuslim_App` because those files lacked source manifests, mixed unattributed translations, and contained truncated texts (e.g., *Sayyid al-Istighfar* ending in `...`).
4. **LLM-Generated or Machine-Translated Religious Texts**: Strictly prohibited across all phases. Chapters of *Hisn al-Muslim* without an open, permissively licensed English translation (`after_prayer`, `sleep`, `waking`, etc.) ship **Arabic-first** (`translationEn = null`), verified by `CompanionDatasetIntegrityTest.hisnAlMuslimChaptersNeverShipUnverifiedEnglishTranslations`.

---

## 3. Required In-App Attribution Verification

All required attributions are surfaced both in repository documentation (`docs/LICENSES.md`, `docs/QURAN_DATA.md`, and this audit) and directly inside the application UI:

1. **Settings -> Licenses Modal Bottom Sheet (`R.string.settings_licenses_summary` in `values/strings.xml` and `values-ar/strings.xml`)**:
   - **Tanzil Project** (`CC-BY 3.0 — tanzil.net`)
   - **Madani Mushaf page/juz data** (`quran.com`)
   - **Amiri and IBM Plex Sans Arabic** (`SIL Open Font License 1.1`)
   - **Wikimedia Commons Adhan recordings** (`CC0 by Adam-synagda; Sabah Fakhri public domain; CC BY-SA 4.0 by Andrewler and Mahfoudou`)
   - **GeoNames City Data** (`CC-BY 4.0`)
   - **Morning & Evening Adhkar** (`© 2024 Seen Arabic, Seen-Arabic/Morning-And-Evening-Adhkar-DB, MIT License`)
   - **Fortress of the Muslim (*Hisn al-Muslim*)** (`Sheikh Sa'id ibn Ali ibn Wahf al-Qahtani, free non-commercial unaltered redistribution; structured Arabic JSON via asellam/HisnElMuslim, MIT License © 2021 Abdellah SELLAM`)
   - **Forty Hadith of Imam An-Nawawi** (`fawazahmed0/hadith-api, The Unlicense / Public Domain`)
   - **HadeethEnc.com Thematic Hadiths** (`© HadeethEnc.com — Encyclopedia of Translated Prophetic Hadiths, dataset version hadeethenc-api-v1-2026-11-20, distributed unmodified with source attribution`)
2. **Hadith Detail Screen (`HadithDetailScreen.kt`)**:
   - Displays collection title, primary book & number reference (`referenceAr` / `referenceEn`), scholarly authenticity grade badge (`gradeAr` / `gradeEn`), grader attribution (`graderAttribution`), and dataset version (`sourceVersion`, e.g. `hadeethenc-api-v1-2026-11-20` or `fawazahmed0-ara-nawawi-unlicense-2026-11-20`) on every Hadith card.
3. **Adhkar Session & Duas Hub Cards (`AdhkarSessionScreen.kt`, `DuasHubScreen.kt`)**:
   - Displays primary Hadith/Quranic source reference (`sourceAr` / `sourceEn`) on every Dhikr and Dua card.

---

## 4. Summary of Legal Posture for Public Release

- **Free, Non-Commercial, Ad-Free Release (Current Ayah Distribution Model)**: **100% Compliant**. Every bundled dataset, typeface, and audio asset satisfies its redistribution and attribution requirements.
- **Constraint for Future Monetization**: Because *Hisn al-Muslim* (Sheikh Sa'id al-Qahtani's redistribution condition) and **HadeethEnc.com** prohibit commercial resale or paywalling of their religious datasets, Ayah must remain free and ad-free, or keep all Quran, Adhkar, Dua, and Hadith content permanently unpaywalled and ad-free.
