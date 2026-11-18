# Ayah — Third-party licenses

## Fonts

- **Amiri** (`app/src/main/res/font/amiri_regular.ttf`, `amiri_bold.ttf`)
  — Copyright (c) 2010 The Amiri Project, licensed under the **SIL Open Font
  License 1.1** (with font-exception wording). The full OFL text ships with
  the release bundle; source: https://github.com/google/fonts/tree/main/ofl/amiri
  License text: https://openfontlicense.org

## Quran text (to be completed at Phase 2 import)

- Dataset source, revision, checksum, and license will be recorded in
  `docs/QURAN_DATA.md` and surfaced in Settings → Licenses.

## Open-source libraries

All dependencies are listed in `gradle/libs.versions.toml`:
AndroidX (Apache 2.0), Jetpack Compose (Apache 2.0), Hilt/Dagger
(Apache 2.0), Room (Apache 2.0), DataStore (Apache 2.0),
kotlinx.coroutines (Apache 2.0), JUnit (EPL 1.0), MockK (Apache 2.0),
Turbine (Apache 2.0).

The Settings → Licenses screen will present the full OSS notice list
(generated at release phase).

## Phase 12 — Prayer times & adhan

- **adhan-java 1.2.1** (`com.batoulapps.adhan:adhan`) — prayer-time calculation engine. MIT license (Batoul Apps, github.com/BatoulApps/adhan-java). Used locally, fully offline; no network calls for calculations.
- **GeoNames** — the bundled city catalog (`assets/prayer/cities.json`, 107 curated cities) is derived from GeoNames data, licensed **Creative Commons Attribution 4.0** (geonames.org). Attribution: "City data from GeoNames under CC-BY 4.0."
- **Aladhan API** (api.aladhan.com) — used ONLY at development time to generate independent reference fixtures for calculation tests (`src/test/resources/prayer/aladhan-reference.json`). Not called at runtime; the app performs no prayer-time network requests.
- **Adhan audio**: no recordings are bundled. Redistribution rights could not be verified for any catalog, so the app ships an honest empty catalog and lets users select their own local audio file for full-adhan playback.

## Phase 13 — built-in adhan voice library

Four adhan recordings bundled in `res/raw/`, each license-verified on Wikimedia Commons before download (full decision log: docs/ADHAN-AUDIO-LICENSES.md; inventory: docs/ADHAN-AUDIO-ASSETS.md):

- **adhan_cc0_beautiful.ogg** — CC0 1.0 Universal, recording by Adam-synagda (DEFAULT voice).
- **adhan_pd_sabah_fakhri.mp3** — Public domain, performance by Sabah Fakhri.
- **adhan_by_sa_azan.ogg** — CC BY-SA 4.0, recording by Andrewler. Attribution required and provided.
- **adhan_by_sa_call_to_worship.oga** — CC BY-SA 4.0, recording by Mahfoudou. Attribution required and provided.

Per-prayer voice ids persist in DataStore; unresolvable ids fall back to the CC0 default.
