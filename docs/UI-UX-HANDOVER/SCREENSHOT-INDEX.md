# Screenshot Index (visual baseline before redesign)

Captured 2026-11-19 on `ayah_avd` (API 36, 1080×2400), app
`com.ayah.quran.debug` v1.0.0-beta.6, Arabic (default locale), unless
noted. Fresh captures taken during this handover unless marked
**[session reuse]** (captured during real development sessions of the
same builds — equally authentic, unmodified).

| File | Screen | Navigation path | State/notes |
|---|---|---|---|
| `home/01_home_dashboard.png` | Home dashboard | cold launch | onboarding card + continue-reading + wallpaper hint |
| `quran/15_quran_list.png` | Quran surah list | tab القرآن | fresh install (import done), search field |
| `reader/02_mushaf_page_mode.png` | Mushaf page mode | Quran list → الفاتحة | page 1 من 604, top bar with play/bookmark/mode |
| `reader/03_continuous_reader.png` | Continuous reader | mushaf → "وضع النص المتغير" | Al-Fatihah text + surah header |
| `reader/04_reader_play_from_ayah.png` **[session reuse]** | Continuous reader play-from-ayah | tap ayah → play bar | recitation from selected ayah |
| `reader/05_auto_page_follow.png` **[session reuse]** | Page-follow during recitation | Malik recitation | automatic page navigation (slice 3) |
| `audio/04_audio_tab_catalog.png` | Audio tab | tab الصوت | 5 reciter chips + attribution + surah rows |
| `audio/05_mini_player_home.png` | Mini player | play from Audio tab → Home | playing Al-Fatihah, ayah counter |
| `audio/06_now_playing.png` | Now Playing screen | mini player tap | full controls visible |
| `audio/07_mini_player_restore.png` **[session reuse]** | Mini player restore hint | after process kill + relaunch | "اضغط للتشغيل للمتابعة" |
| `audio/08_audio_nowplaying_bar.png` **[session reuse]** | Audio tab now-playing bar | during Malik recitation | repeat-ayah chip visible |
| `prayer/07_prayer_times.png` | Prayer times tab | tab الصلوات | location setup + times |
| `prayer/08_prayer_settings_top.png` | Prayer settings (top) | gear icon | location/method sections |
| `prayer/09_prayer_settings_adhan.png` | Prayer settings (adhan/diagnostics) | scroll | custom file + test buttons |
| `prayer/10_adhan_voices_per_prayer.png` **[session reuse]** | Adhan voices | settings → أصوات الأذان | 5 prayer cards |
| `prayer/11_adhan_library.png` **[session reuse]** | Adhan library | voices → اختيار الصوت | chips + recordings |
| `prayer/12_adhan_library_downloaded.png` **[session reuse]** | Adhan library downloaded state | after download | "متاح دون اتصال" |
| `prayer/13_onboarding_steps.png` **[session reuse]** | Prayer onboarding | home card → تفعيل | step content |
| `wallpapers/12_wallpapers_tab.png` | Wallpapers tab | tab الخلفيات | empty-state hint visible |
| `settings/13_settings_tab.png` | Settings tab | tab الإعدادات | theme/language/about |
| `settings/14_settings_updates.png` | Settings updates section | scroll | updater states |
| `dialogs/16_update_installer.png` **[session reuse]** | System installer dialog | updater → تثبيت التحديث | package installer UI |

## Capture checklist for shots still missing

(Re-run any time on the emulator; paths in DEVELOPMENT-ENVIRONMENT.md)

- [ ] Quran list SEARCH active state (type-free: tap the field)
- [ ] Mushaf page-jump dialog (`PageJumpDialog`) — mushaf top bar jump icon
- [ ] Continuous reader with selection play-bar (tap ayah text in continuous mode)
- [ ] Font-size controls expanded (reader "TextFields" icon)
- [ ] Wallpapers gallery with a template applied (apply once first)
- [ ] Fullscreen wallpaper preview (`preview/{surahId}/{ayahNumber}`)
- [ ] Bookmarks screen (bookmark an ayah first)
- [ ] Adhan onboarding full pager (home onboarding card)
- [ ] Settings language-switched to English (settings → English) — LTR pass
- [ ] Audio tab blocked-by-adhan banner (trigger adhan test, then open Audio)
- [ ] Download progress rows mid-download (n/total) on Audio tab
- [ ] Dark/light variants of each of the above (in-app theme selector)
