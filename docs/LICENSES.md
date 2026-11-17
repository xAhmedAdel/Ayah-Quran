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
