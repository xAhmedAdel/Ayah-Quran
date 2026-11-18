# Adhan Audio Sources (Phase 15)

Per-recording source verification. Every remote download URL points to a Wikimedia Commons file whose license, author, and provenance were verified via the Commons API before inclusion (see ADHAN-AUDIO-LICENSES.md for the full rights-verification table).

## Verified downloadable recordings

### `adhan_madinah_prophet_mosque` — Madinah
- **Source**: [File:33937 ejaz215 call-to-prayer-from-the-prophet-s-mo.ogg](https://commons.wikimedia.org/wiki/File:33937_ejaz215_call-to-prayer-from-the-prophet-s-mo.ogg)
- **Author**: ejaz215 (own work)
- **License**: CC BY 3.0
- **Description**: "Islamic call to prayer from the prophet mosque" — genuine adhan recorded at the Prophet's Mosque, Madinah
- **Duration**: 3:06 · **Size**: 2,974,689 bytes · **Format**: OGG Vorbis
- **Download URL**: `https://upload.wikimedia.org/wikipedia/commons/7/7c/33937_ejaz215_call-to-prayer-from-the-prophet-s-mo.ogg` (direct URL, no redirect)

### `adhan_casablanca_hassan2` — Casablanca (Other regions)
- **Source**: [File:Llamada a oración Mezquita Hassan II.wav](https://commons.wikimedia.org/wiki/File:Llamada_a_oraci%C3%B3n_Mezquita_Hassan_II.wav)
- **Author**: Fraguando (own work)
- **License**: CC BY-SA 4.0
- **Description**: "Sonido ambiental registrado al pie de la Mezquita Hassan II de Casablanca, Marruecos" — adhan recorded at Hassan II Mosque, Casablanca
- **Duration**: 2:56 · **Size**: 30,990,030 bytes · **Format**: WAV PCM
- **Download URL**: `https://upload.wikimedia.org/wikipedia/commons/9/9c/Llamada_a_oraci%C3%B3n_Mezquita_Hassan_II.wav` (direct URL, no redirect)

## Verified bundled recordings (Phase 13)

| id | Source | License |
|---|---|---|
| `adhan_cc0_beautiful` | [Beautiful adhan.ogg](https://commons.wikimedia.org/wiki/File:Beautiful_adhan.ogg) | CC0 1.0 |
| `adhan_pd_sabah_fakhri` | [Call to prayer by Sabah Fakhry.mp3](https://commons.wikimedia.org/wiki/File:Call_to_prayer_by_Sabah_Fakhry.mp3) | Public domain |
| `adhan_by_sa_azan` | [Azan.ogg](https://commons.wikimedia.org/wiki/File:Azan.ogg) | CC BY-SA 4.0 |
| `adhan_by_sa_call_to_worship` | [Islamic call to worship.oga](https://commons.wikimedia.org/wiki/File:Islamic_call_to_worship.oga) | CC BY-SA 4.0 |

## Excluded recordings (insufficient rights or quality)

| Source | Reason for exclusion |
|---|---|
| `File:Adhan, Great Mosque of Mecca – Jan 21, 2013.webm` | VIDEO file (YouTube-origin); extracting audio = YouTube rip (forbidden by Phase 15 §2) |
| `File:Maghrib Adhan at the Masjid al Haram, Mecca - 25 Feb, 2012.webm` | Same — video file, YouTube origin |
| `File:The Adhan - Muslim Call to Prayer - Aaqib Azeez.mp3` | 1:26 duration = incomplete sample (spec requires complete adhan ≥2 min) |
| `File:Llamada a oración Mezquita Hassan II.wav` | 31 MB WAV — excessive APK weight for bundled; included as remote download instead |
| KFGQPC / official Haram recordings | Official distribution does not permit third-party app bundling; no verified licensing was found |

## Makkah status

**No Makkah adhan audio with verified redistribution rights is currently available.** The Makkah category filter in the library shows an honest "not available yet" state. If official or CC-licensed Makkah recordings become available, they should be added to the catalog with the same verification pipeline.
