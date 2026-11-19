# Screen Inventory (verified: code + emulator tour 2026-11-19)

Every screen that exists. "Verified visually" = opened on the emulator
during this handover (screenshot in `screenshots/`).

## Top-level tabs (bottom bar, RTL right→left)

| # | Tab | Route | Screen file | Visually verified |
|---|---|---|---|---|
| 1 | الرئيسية (Home) | `home` | `ui/home/HomeScreen.kt` | ✅ `home/01_home_dashboard.png` |
| 2 | القرآن (Quran) | `quran` | `ui/quran/QuranScreen.kt` | ✅ `quran/15_quran_list.png` |
| 3 | الصلوات (Prayers) | `prayer` | `ui/prayer/PrayerTimesScreen.kt` | ✅ `prayer/07_prayer_times.png` |
| 4 | الخلفيات (Wallpapers) | `wallpapers` | `ui/wallpapers/WallpapersScreen.kt` | ✅ `wallpapers/12_wallpapers_tab.png` |
| 5 | الصوت (Audio) | `audio` | `ui/audio/AudioScreen.kt` | ✅ `audio/04_audio_tab_catalog.png` |
| 6 | الإعدادات (Settings) | `settings` | `ui/settings/SettingsScreen.kt` | ✅ `settings/13_settings_tab.png` |

## Stacked screens (routes with back navigation)

| Screen | Route | File | Entry points | Visually verified |
|---|---|---|---|---|
| Mushaf page reader | `mushaf?surahId=&page=&ayah=` | `ui/reader/MushafPageReaderScreen.kt` | Quran list tap; continue-reading card; mode switch | ✅ `reader/02_mushaf_page_mode.png` |
| Continuous reader | `reader/{surahId}?ayah=` | `ui/reader/ReaderScreen.kt` | mushaf "وضع النص المتغير" action; bookmarks | ✅ `reader/03_continuous_reader.png` |
| Fullscreen wallpaper preview | `preview/{surahId}/{ayahNumber}` | `ui/wallpapers/FullscreenPreviewScreen.kt` | Wallpapers screen | not visited this tour (verified in code; previously exercised in Phase 5-6) |
| Bookmarks | `bookmarks` | `ui/bookmarks/BookmarksScreen.kt` | Home card "العلامات المرجعية" | not visited this tour |
| Adhan voices (per-prayer) | `prayer/adhan-voice` | `ui/prayer/AdhanVoiceScreen.kt` | Prayer settings → "أصوات الأذان لكل صلاة" | ✅ (reused) `prayer/10_adhan_voices_per_prayer.png` |
| Adhan library | `prayer/adhan-library/{prayer}` | `ui/prayer/AdhanLibraryScreen.kt` | Adhan voices → "اختيار الصوت" | ✅ (reused) `prayer/11_adhan_library.png`, `12_..._downloaded.png` |
| Prayer onboarding | `prayer/onboarding` | `ui/prayer/PrayerOnboardingScreen.kt` | Home prayer card; dashboard card | ✅ (reused) `prayer/13_onboarding_steps.png` |
| Prayer settings | `prayer/settings` | `ui/prayer/PrayerSettingsScreen.kt` | Prayers tab gear | ✅ `prayer/08_…top.png`, `09_…adhan.png` |
| Now Playing | `audio/now-playing` | `ui/audio/NowPlayingScreen.kt` | Mini player tap | ✅ `audio/06_now_playing.png` |

## In-screen structures (not separate routes)

| Structure | Location | Notes |
|---|---|---|
| Mini player bar | `AyahApp.kt` Scaffold (above NavHost) | hidden on Audio tab + when idle; restore/resume state supported (`audio/05…`, `07…`) |
| Permissions dashboard | inside Prayer settings | rows: notifications, adhan channel, exact alarms, background, adhan mode |
| Diagnostics (test alarm/adhan buttons) | inside Prayer settings (advanced) | `09_…adhan.png` area |
| Updates section | inside Settings | check/download/install states (`settings/14_settings_updates.png`) |
| Theme + language cards | inside Settings | light/dark/system radio rows; Arabic/English rows |
| Page jump dialog | inside Mushaf reader | `PageJumpDialog` (go-to-page input) |
| Search field | Quran list ("ابحث عن سورة") | filters surah list |

## States observed

- **Empty states**: Audio tab before slice 1 (honest catalog note),
  wallpapers "لم يتم تطبيق خلفية بعد", bookmarks empty.
- **Loading**: Quran import (spinner in reader while DB seeds — first
  launch only), download progress rows (n/total), update downloading %.
- **Error**: download failure rows with retry, update check failure text,
  player error state label.
- **Selected/disabled**: reciter chips, per-prayer adhan mode chips,
  disabled reader play action when no highlight.

## Screens NOT present (do not design for them)

No splash-configuration screen beyond the default SplashScreen API theme,
no login/accounts, no ads, no tablet/two-pane layout, no
settings-subpages (everything is cards inside the two settings surfaces).
