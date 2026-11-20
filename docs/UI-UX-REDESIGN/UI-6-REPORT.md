# AYAH — UI-6 Report: Audio Studio & Now Playing

## 1. Scope Completed

Phase UI-6 redesigns **Audio Studio** (`AudioScreen` / `AudioViewModel`) and **Now Playing** (`NowPlayingScreen`) into Direction D (**Al-Mushaf Al-Hadith**), replacing the eager 114-row `Column` composition with a virtualized `LazyColumn` and exposing live seek, repeat range, Open-in-Mushaf, and responsive sleep/speed controls.

### Audio Studio (`AudioScreen` + `AudioViewModel`)
- **Virtualized `LazyColumn`**: Eliminates the eager 114-row composition bottleneck (`audio_lazy_list`).
- **Horizontal Visual Reciter Cards (`LazyRow`)**: 5 refined `ReciterCard`s (`172dp`) showing localized reciter names (`nameAr` / `nameEn`), bitrate badge (`128 kbps` / `64 kbps` LTR-isolated), and active check/border state, with honest `audio_attribution_note` below.
- **Search & Offline Filter**: `AyahSearchBar` (`quran_search_hint`) + `All (N)` / `Downloaded (N)` filter chips.
- **Surah Rows (`SurahAudioRowView`)**: `SurahNumberEmblem`, localized Surah names (`nameArabic` / `nameTransliterated`), pluralized ayah counts (`ayah_count_plurals`, fixing hardcoded Arabic `"آية "` in English), inline slim `3dp` `LinearProgressIndicator` during download, dynamic Play/Pause `contentDescription`, and `ConfirmDialog` confirmation before deleting offline recitations.
- **Adhan Priority Banner**: Conditional `AyahBannerStrip(WARNING)` when `blockedByAdhan` is true.

### Now Playing (`NowPlayingScreen` + `AyahApp`)
- **Open in Mushaf Action**: Trailing tonal pill (`now_playing_open_mushaf`) jumping directly to the currently recited Surah and Ayah in the active reader.
- **Sanctuary Hero Medallion (`AyahHeroCard`)**: Amiri Surah title (plus transliterated name in English) and localized Reciter subtitle.
- **Ayah Counter, Interactive Slider & Transport Cluster**: Tabular ayah counter, interactive ayah `Slider`, live `positionMs`/`durationMs` elapsed timer, `64dp` circular Play/Pause primary button with dynamic `contentDescription`, Previous/Next Ayah, and Stop.
- **Repeat Range, Sleep Timer & Speed**: `FlowRow` chips for Repeat Ayah, Repeat Range (opens `RepeatRangeDialog` wired to `MiniPlayerViewModel.setRepeatRange`), Sleep Timer (`Off`, `After ayah`, `After surah`, `10m`, `20m`, `30m`), and LTR-isolated Speed (`0.75x`..`1.5x`).

## 2. Files Changed

- `app/src/main/java/com/ayah/quran/navigation/AyahApp.kt`
- `app/src/main/java/com/ayah/quran/ui/audio/AudioScreen.kt`
- `app/src/main/java/com/ayah/quran/ui/audio/AudioViewModel.kt`
- `app/src/main/java/com/ayah/quran/ui/audio/NowPlayingScreen.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-ar/strings.xml`
- `app/src/androidTest/java/com/ayah/quran/ui/AudioTabUiTest.kt`

## 3. Verification

- `:app:assembleDebug` — PASS
- `:app:testDebugUnitTest` — **163/163 passed**
- `:app:connectedDebugAndroidTest` (`AudioTabUiTest` on `ayah_avd`, API 36) — **3/3 passed**

## 4. Visual Verification Screenshots (`docs/UI-UX-REDESIGN/screenshots/ui-6/`)

1. `01_audio_studio_ar_light.png` — Audio Studio AR Light (`LazyColumn`, visual Reciter cards, search bar, filter chips, octagonal Surah emblems, plural ayah counts).
2. `02_audio_studio_ar_dark.png` — Audio Studio AR Dark (Obsidian Moss dark mode).
3. `03_audio_studio_en_light.png` — Audio Studio EN Light (LTR, localized reciter & Surah names, English plurals).
