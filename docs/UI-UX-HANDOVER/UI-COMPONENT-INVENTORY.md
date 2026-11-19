# UI Component Inventory

## Shared design-system components (`core/designsystem/component/`)

| Component | File | Purpose | Notes |
|---|---|---|---|
| `AyahCard` | AyahCard.kt | elevated-surface card used everywhere | M3 ElevatedCard wrapper; internal `padding(AyahSpacing.m)` |
| `SectionCard` | SectionCard.kt | titled section container (title + content slot) | used on Settings, Audio, Prayer settings |
| `EmptyState` | EmptyState.kt | icon + title + message honest empty state | used on Audio tab, wallpapers |
| `ConfirmDialog` | ConfirmDialog.kt | M3 AlertDialog confirm/cancel | destructive confirmations |

## Theme tokens (`core/designsystem/theme/`)

| Token | File | Purpose |
|---|---|---|
| `AyahSpacing` | Spacing.kt | spacing/corner scale (xs…pill) |
| `AyahLightColors` / `AyahDarkColors` | Color.kt | full M3 color schemes |
| `AyahTypography`, `QuranTextFontFamily`, `AyahWordmarkStyle` | Type.kt | text styles + Amiri |
| `AyahTheme` | Theme.kt | theme assembly (system/in-app dark mode) |

## Screen-level reusable composables (private, inside screens)

| Composable | Screen | Notes |
|---|---|---|
| `MiniPlayerBar` | `ui/audio/MiniPlayerBar.kt` (public) | global bar in AyahApp; pause/resume; restore-hint variant |
| `NowPlayingScreen` sections | `ui/audio/NowPlayingScreen.kt` | identity card, ayah navigator, sleep chips, speed chips |
| `SurahAudioRowView` | `ui/audio/AudioScreen.kt` | surah row: number+name+ayahCount, play/pause, download/delete states |
| `PlaybackControls`, `SeekBarRow` | `ui/audio/component/` | Phase-5 legacy panel controls (still rendered) |
| `VoiceCard` | `ui/prayer/AdhanVoiceScreen.kt` | per-prayer voice card (name + اختيار الصوت) |
| recording cards | `ui/prayer/AdhanLibraryScreen.kt` | reciter/library cards with download states + preview |
| `MushafText` | `ui/reader/MushafText.kt` | ⚠️ core: builds AnnotatedString, per-ayah tap ranges, highlight |
| `SurahHeader` | `ui/reader/ReaderScreen.kt` | surah name card (Amiri 34sp) |
| `FontSizeControls` | `ui/reader/ReaderScreen.kt` | font scale +/- |
| `PageJumpDialog` | `ui/reader/MushafPageReaderScreen.kt` | go-to-page input dialog |
| `PermissionsDashboardSection` | `ui/prayer/PrayerSettingsScreen.kt` | permission rows dashboard |
| `ButtonLike` | `ui/prayer/PrayerSettingsScreen.kt` | label-button used in adhan section |
| wallpaper cards/preview | `ui/wallpapers/WallpapersScreen.kt` | template gallery + fullscreen preview |
| `AboutRow`, `LanguageOptionRow` | `ui/settings/SettingsScreen.kt` | settings rows |
| `UpdatesSection`, `UpdateAvailableRows` | `ui/settings/SettingsScreen.kt` | updater states |
| `MiniPlayerViewModel` | `ui/audio/MiniPlayerViewModel.kt` | shared state for bar + Now Playing |

## M3 components in active use

`Scaffold`, `TopAppBar`, `NavigationBar`+`NavigationBarItem`,
`FilterChip`, `RadioButton`, `Slider`, `LinearProgressIndicator`,
`CircularProgressIndicator`, `IconButton`, `Button`, `TextButton`,
`ElevatedCard` (via AyahCard), `HorizontalDivider`, `HorizontalPager`
(reader), `AlertDialog` (via ConfirmDialog/PageJumpDialog).
