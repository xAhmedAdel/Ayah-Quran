# Adhan Audio Licenses (Phase 13)

Every recording bundled in `app/src/main/res/raw/` was verified on its Wikimedia Commons file page (author, license, source) BEFORE download. Licenses are also embedded in the app's catalog metadata (`BundledAdhanCatalog`) and shown in Settings → Licenses.

| id | File (Commons) | Author | License | Attribution required | Duration | Size |
|---|---|---|---|---|---|---|
| `adhan_cc0_beautiful` (DEFAULT) | [Beautiful adhan.ogg](https://commons.wikimedia.org/wiki/File:Beautiful_adhan.ogg) | Adam-synagda (own work) | **CC0 1.0 Universal** (public-domain dedication) | None required (given anyway) | 2:34 | 1,229,032 B |
| `adhan_pd_sabah_fakhri` | [Call to prayer by Sabah Fakhry.mp3](https://commons.wikimedia.org/wiki/File:Call_to_prayer_by_Sabah_Fakhry.mp3) | Performance by **Sabah Fakhri** (famous Syrian reciter, 1929–2022); archived from YouTube to Commons | **Public domain** | Given anyway (artist credit) | 2:38 | 2,530,742 B |
| `adhan_by_sa_azan` | [Azan.ogg](https://commons.wikimedia.org/wiki/File:Azan.ogg) | Andrewler (own work) | **CC BY-SA 4.0** | Yes — "Recording by Andrewler (Wikimedia Commons), CC BY-SA 4.0" | 3:03 | 1,473,809 B |
| `adhan_by_sa_call_to_worship` | [Islamic call to worship.oga](https://commons.wikimedia.org/wiki/File:Islamic_call_to_worship.oga) | Mahfoudou (own work) | **CC BY-SA 4.0** | Yes — "Recording by Mahfoudou (Wikimedia Commons), CC BY-SA 4.0" | 3:30 | 1,295,539 B |

## Licensing decision evidence

- License, author, and source URLs were retrieved programmatically from the Commons API (`prop=imageinfo&iiprop=extmetadata`) on 2026-11-18 and re-verified against each file page.
- **CC0** and **Public domain**: unrestricted redistribution, including commercial apps. Attribution still provided in-app.
- **CC BY-SA 4.0**: redistribution permitted with attribution (provided in Settings → Licenses and in the voice library metadata). The recordings are bundled UNMODIFIED, so the share-alike clause applies to the audio itself, not the application.
- Two candidates were **rejected** and NOT bundled:
  - `The Adhan - Muslim Call to Prayer - Aaqib Azeez.mp3` (CC BY-SA) — 1:26 duration = incomplete sample (Phase 13 requires complete recordings).
  - `Llamada a oración Mezquita Hassan II.wav` (CC BY-SA) — 31 MB WAV, excessive APK weight (4 sufficient recordings existed).
- **No recordings with unclear copyright status are bundled.** No license information is invented: every field above comes from the Commons metadata.

## In-app attribution

- Settings → Licenses: static summary names all four attributions.
- Voice library: each row shows its license short name.
- `BundledAdhanCatalog`: each recording carries `license`, `attribution`, and `sourceUrl` — available to any future attribution screen.

## SHA-256 (as bundled)

- `adhan_cc0_beautiful.ogg` — `35fe06b08fe80505c550c33fed8a783fa9901ddc81ac884958b4be048f5b2a79`
- `adhan_pd_sabah_fakhri.mp3` — `94a2fe12f2e9998b64d5c77469f530a4e75a9e3514837ce71f6937fc6c1ae9a5`
- `adhan_by_sa_azan.ogg` — `2163ceafb8a05064a5ea5e1e0a7fac5fff958a21fcf830844d557d496b9b23c3`
- `adhan_by_sa_call_to_worship.oga` — `c30a43af28d391b401ccd36f038f6abcd5c56f79aaaa0703caaae9063710fe77`
