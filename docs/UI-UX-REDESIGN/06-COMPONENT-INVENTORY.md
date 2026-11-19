# 06 — Component Inventory & Migration Map

Maps every existing component in `core/designsystem/` and `ui/**` to the redesigned **Al-Mushaf Al-Hadith** component library. Goal: maximize reuse, eliminate per-screen one-off styling, and keep every screen visually unified.

---

## 1. Existing Component Audit & Disposition

| Current Component | Current File | Disposition | Action Plan |
|---|---|---|---|
| `AyahLightColors` / `AyahDarkColors` | `core/designsystem/theme/Color.kt` | **Replace (Parallel -> Swap)** | Introduce `AyahV2LightColors`, `AyahV2DarkColors`, and `AyahExtendedColors` (`LocalAyahColors`) with full `surfaceContainer*` ramp, Hero tokens, Mushaf canvas/highlight, and semantic status tokens. |
| `AyahTypography`, `QuranTextFontFamily`, `AyahWordmarkStyle` | `core/designsystem/theme/Type.kt` | **Improve** | Keep `QuranTextFontFamily` (Amiri); add optional `AyahUiFontFamily` (IBM Plex Sans Arabic or tuned system sans), tabular numeral helper styles, and Arabic-friendly line heights. |
| `AyahSpacing`, `AyahRadii` | `core/designsystem/theme/Spacing.kt` | **Improve** | Expand `AyahSpacing` (`xxs`, `sm`, `ml`) and actually apply `AyahRadii` (`xs..pill`) consistently across all components. |
| `AyahTheme` | `core/designsystem/theme/Theme.kt` | **Improve** | Provide `LocalAyahColors` via `CompositionLocalProvider` alongside `MaterialTheme`. |
| `AyahMotion`, `rememberMotionEnabled` | `core/designsystem/motion/AyahMotion.kt` | **Improve** | Keep `rememberMotionEnabled()`; add semantic easing curves (`AyahMotionSpecs`) and spring tokens for sheets, pills, and audio bars. |
| `AyahNumbering` | `core/designsystem/AyahNumbering.kt` | **Keep & Extend** | Keep `toArabicIndic(Int)`; add `isolateLtr(String)` (`\u2066...\u2069`) and locale-aware digit helper. |
| `AyahCard` | `core/designsystem/component/AyahCard.kt` | **Replace** | Replace drop-shadow `ElevatedCard` (`12dp`) with tonal `AyahSurfaceCard` (`0dp` shadow, `16dp` radius, `1dp` `outlineVariant` border, optional click support). |
| `SectionHeader` | `core/designsystem/component/AyahCard.kt` | **Improve** | Move into `AyahSectionHeader.kt` with semantic `heading()`, optional trailing action text/icon, and `titleSmall`/`titleMedium` hierarchy. |
| `SectionCard` | `core/designsystem/component/SectionCard.kt` | **Consolidate** | Evolve into `AyahGroupCard` (titled tonal container with automatic hairline dividers between child rows, eliminating nested card clutter). |
| `EmptyState` | `core/designsystem/component/EmptyState.kt` | **Improve** | Upgrade to `AyahEmptyState`: soft tonal icon medallion (`64dp`), clear title/message, and optional primary CTA button slot (e.g. "Clear search", "Choose city"). |
| `ConfirmDialog` | `core/designsystem/component/ConfirmDialog.kt` | **Improve** | Apply `AyahRadii.xl` (`28dp`), tonal surface, optional leading warning icon, and explicit destructive button styling. |
| `MiniPlayerBar` | `ui/audio/MiniPlayerBar.kt` | **Replace** | Redesign as `AyahMiniPlayerBar`: floating docked pill above Bottom Navigation Bar (`64dp`, `16dp` radius, `2dp` top ayah-progress bar, dynamic Play/Pause CD, distinct restore & Adhan-blocked visual badges, localized reciter/surah names). |
| `PlaybackControls`, `SeekBarRow` | `ui/audio/component/PlaybackControls.kt`, `SeekBarRow.kt` | **Retire / Replace** | Legacy Phase-5 controls are dead UI on `AudioScreen`. Replace with unified `AyahAudioTransportRow` and `AyahAudioSeekBar` used directly by `NowPlayingScreen`. |
| `MushafText` | `ui/reader/MushafText.kt` | **PROTECT (Style-Only)** | **DO NOT modify** `buildMushafString` or `findAyahAtOffset` logic. Only update `highlightColor` and `markerColor` to read from `LocalAyahColors.current.mushafAyahHighlight` and `mushafMarker`. |
| `SurahHeader` / `SurahStartHeader` | `ui/reader/ReaderScreen.kt`, `MushafPageReaderScreen.kt` | **Consolidate & Replace** | Unify into one shared `MushafSurahBanner` (inline parchment-tinted band with top/bottom antique gold hairlines — removes floating drop-shadow box and duplicate surah title). |
| `FontSizeControls` | `ui/reader/ReaderScreen.kt` | **Replace** | Fix broken no-op close button and fold into shared `ReaderSettingsSheet` (Modal Bottom Sheet with Font Scale slider, Auto-Scroll switch, Mode switch). |
| `PageJumpDialog` | `ui/reader/MushafPageReaderScreen.kt` | **Improve** | Restyle with `28dp` corners, numeric `KeyboardType.Number`, proper Cancel label (`R.string.action_cancel`), quick Juz shortcuts, and live page validation `1..604`. |
| `PermissionsDashboardSection` | `ui/prayer/PermissionsDashboardSection.kt` | **Improve** | Upgrade status rows with `AyahStatusBadge` + inline trailing action pills; collapse diagnostics into expandable `DiagnosticsAccordion`; eliminate the 2 duplicate cards in `PrayerSettingsScreen`. |
| `ButtonLike`, `DropdownSetting` | `ui/prayer/PrayerSettingsScreen.kt` | **Remove / Replace** | Remove `ButtonLike` wrapper; replace vertical-chip `DropdownSetting` with `AyahSettingRow` + `AyahSelectionSheet`. |
| `VoiceCard` | `ui/prayer/AdhanVoiceScreen.kt` | **Consolidate** | Replace 5 separate cards with `AyahGroupCard` containing 5 `AyahSettingRow`s + inline preview `IconButton`. |
| `RecordingCard` | `ui/prayer/AdhanLibraryScreen.kt` | **Replace** | Streamline into clean 1-primary-action audio row (Play circle + metadata + single state-aware trailing button; fix Delete-on-undownloaded bug). |
| `SurahRow` / `SurahAudioRowView` | `ui/quran/QuranScreen.kt`, `ui/audio/AudioScreen.kt` | **Consolidate** | Share `SurahNumberEmblem` (octagonal geometric badge) and consistent list row rhythm across Quran Hub and Audio Studio. |

---

## 2. New Shared Design System Components (`core/designsystem/component/`)

| New Component | Purpose | Used On |
|---|---|---|
| `AyahSurfaceCard` | Standard `0dp`-shadow tonal card (`16dp` radius, `1dp` `outlineVariant`, optional `onClick` with ripple) | All screens |
| `AyahHeroCard` | Deep Emerald Sanctuary container (`22dp` radius, `heroSurface`, subtle gold hairline frame) | Home (Continue Reading), Prayer Times (Next Prayer) |
| `AyahGroupCard` | Grouped inset container for lists of setting/status rows with automatic inset dividers | Settings Hub, Prayer Settings, Adhan Voices, Prayer Timetable |
| `AyahSectionHeader` | Accessible `heading()` section title with optional trailing action button | Home, Audio, Prayer Settings, Settings |
| `AyahSearchBar` | Pill search input (`48dp`, `surfaceContainer`) with search icon, `ImeAction.Search`, and Clear (`X`) button | Quran Hub, Audio Studio, Adhan Library, City Picker |
| `AyahStatusBadge` | Icon + concise label pill (`Success`, `Warning`, `Error`, `Neutral`, `Offline`) — enforces never-color-alone | Permissions, Onboarding, Audio downloads, Wallpapers, Adhan Library |
| `AyahSettingRow` | Standardized `56dp+` preference row (leading tonal icon, title, subtitle/current value, trailing chevron/switch/badge) | Settings Hub, Prayer Settings, Reader Settings Sheet |
| `AyahSelectionSheet` | Reusable `ModalBottomSheet` for single-choice lists (Calculation method, High-lat, Surah picker, City picker, Sleep timer) | Prayer Settings, Wallpapers, Now Playing, Prayer Times |
| `AyahStepProgress` | Segmented/linear progress header (`Step X of Y`) for multi-step setup | Prayer Onboarding |
| `SurahNumberEmblem` | Custom Compose `Canvas` 8-point geometric rosette (`38dp`) displaying localized Surah number (no external SVG dependency) | Quran Hub, Audio Studio, Bookmarks |
| `MushafSurahBanner` | Shared inline ornamental Surah header with antique-gold rules + Amiri title + optional Bismillah | Mushaf Page Reader, Continuous Reader |
| `ReaderAyahActionPill` | Floating contextual pill (`Play · Bookmark · Wallpaper · Dismiss`) triggered on Ayah tap | Mushaf Page Reader, Continuous Reader |
| `AyahBannerStrip` | Compact dismissible/actionable alert strip (`Warning` / `Error` / `Info`) | Home (Prayer setup), Prayer Times (Exact alarm alert), Audio (Blocked by Adhan) |

---

## 3. Component Count Discipline

- **Before**: 4 basic shared components (`AyahCard`, `SectionCard`, `EmptyState`, `ConfirmDialog`) + 16 ad-hoc private composables with inconsistent paddings, shapes, and icons.
- **After**: **14 cohesive, single-purpose shared components** in `core/designsystem/component/` covering 90% of all screens with zero third-party UI dependencies.
