# Ayah Screenshot Inventory

This document catalogs the 25 application views (50 screenshots total: 25 Light Mode and 25 Dark Mode pairs) captured from the signed release build of Ayah (`v1.0.0-beta.12`, `versionCode = 13`) at `1080x2400` resolution with Android SystemUI Demo Mode enabled (`09:41`, `100%` battery, full signal) and realistic Arabic user data seeded.

---

## 1. Summary Metrics

| Metric | Value |
| :--- | :--- |
| **Application Version** | `1.0.0-beta.12` (`versionCode = 13`) |
| **Resolution** | `1080 x 2400` px (Portrait, RTL Arabic UI) |
| **Documented Views** | `25` distinct screens and major UI states |
| **Total Screenshot Files** | `50` PNG files (`25` Light Mode + `25` Dark Mode) |
| **Root Asset Directory** | `docs/assets/screenshots/` |
| **Light Palette** | Warm Ivory (`#FAF8F3`) + Deep Emerald (`#1F4A3B`) + Antique Gold (`#C4A253`) |
| **Dark Palette** | Deep Obsidian (`#0C1210`) + Warm Sand (`#ECE6DA`) + Soft Gold (`#D6B66B`) |

---

## 2. Complete Screen Inventory & File Mapping

### 2.1 Home & Daily Reflection (`docs/assets/screenshots/home/`)

| # | Screen / View | Composable / Route | Seeded UI State | Light Mode Path | Dark Mode Path |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | **Home Dashboard** | `HomeScreen` (`home`) | Hijri header, Prayer reliability prompt, Continue Reading (`الفاتحة` Page 1, 14%), Daily Companion 4-tile grid (`الأذكار`, `المسبحة`, `الحديث`, `الأدعية`), active `MiniPlayerBar` | `docs/assets/screenshots/home/home-light.png` | `docs/assets/screenshots/home/home-dark.png` |
| 2 | **Home Daily Reflection & Quick Actions** | `HomeScreen` (`home` — scrolled) | Daily Reflection card (`تأمل اليوم` — `آية اليوم` / `حديث اليوم` toggle showing Surah Al-Isra `17:41`), Current Wallpaper status card, Bookmarks (`2`) & Recitations quick actions | `docs/assets/screenshots/home/home-reflection-light.png` | `docs/assets/screenshots/home/home-reflection-dark.png` |

### 2.2 Holy Quran & Mushaf Reader (`docs/assets/screenshots/quran/`)

| # | Screen / View | Composable / Route | Seeded UI State | Light Mode Path | Dark Mode Path |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 3 | **Quran Index — Surahs Tab** | `QuranScreen` (`quran`) | Search bar, Jump-to-Page button, 3-way segmented filter (`السور` active), Surah cards (`الفاتحة` to `الأنعام`) with Makki/Madani metadata, Ayah counts, and Madani starting page badges | `docs/assets/screenshots/quran/quran-light.png` | `docs/assets/screenshots/quran/quran-dark.png` |
| 4 | **Quran Index — Juz Tab** | `QuranScreen` (`quran` — `الأجزاء`) | 30-Juz index showing Juz number, page span (`ص 1–21`, `ص 22–41`, etc.), opening Surah:Ayah reference, and opening Uthmani verse preview | `docs/assets/screenshots/quran/quran-juz-light.png` | `docs/assets/screenshots/quran/quran-juz-dark.png` |
| 5 | **Quran Index — Bookmarks Tab** | `QuranScreen` (`quran` — `المحفوظات`) | Saved Ayah bookmarks (`البقرة 2:2` and `الفاتحة 1:4`) with Uthmani verse text and one-tap removal or reader navigation | `docs/assets/screenshots/quran/bookmarks-light.png` | `docs/assets/screenshots/quran/bookmarks-dark.png` |
| 6 | **604-Page Madani Mushaf Reader** | `MushafReaderScreen` (`reader/{surah}`) | Page `1 من 604` (`سورة الفاتحة`) in justified Uthmani script with ornamental verse markers, Ayah `1:4` highlighted, and floating Ayah action pill (`تشغيل`, Bookmark, Options) | `docs/assets/screenshots/quran/mushaf-reader-light.png` | `docs/assets/screenshots/quran/mushaf-reader-dark.png` |
| 7 | **Continuous Surah Reader** | `SurahDetailScreen` (`reader/{surah}` — Continuous Mode) | Continuous vertical Surah reading mode for `سورة الفاتحة` with font-size cycle button (`TT`), repeat indicator, and Surah metadata banner | `docs/assets/screenshots/quran/continuous-reader-light.png` | `docs/assets/screenshots/quran/continuous-reader-dark.png` |

### 2.3 Prayer Times, Adhan & Reliability Onboarding (`docs/assets/screenshots/prayer/`)

| # | Screen / View | Composable / Route | Seeded UI State | Light Mode Path | Dark Mode Path |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 8 | **Prayer Times Dashboard** | `PrayerScreen` (`prayer`) | Configured for Cairo (`القاهرة · مصر`), Hijri & Gregorian date header, live next-prayer countdown (`الفجر`), and 5 daily prayer rows with individual Adhan notification toggles | `docs/assets/screenshots/prayer/prayer-light.png` | `docs/assets/screenshots/prayer/prayer-dark.png` |
| 9 | **Prayer & Calculation Settings** | `PrayerSettingsScreen` (`prayer_settings`) | Location card (`القاهرة · مصر`), Egyptian General Authority calculation method, Shafi/Maliki/Hanbali Asr juristic method, high-latitude rule, manual minute offsets, Hijri adjustment, and Adhan section | `docs/assets/screenshots/prayer/prayer-settings-light.png` | `docs/assets/screenshots/prayer/prayer-settings-dark.png` |
| 10 | **Per-Prayer Adhan Voice Assignment** | `AdhanVoicesScreen` (`adhan_voices`) | Unified voice action card (`تطبيق صوت واحد لجميع الصلوات`) and individual voice selectors + inline audio preview buttons for Fajr, Dhuhr, Asr, Maghrib, and Isha | `docs/assets/screenshots/prayer/adhan-voices-light.png` | `docs/assets/screenshots/prayer/adhan-voices-dark.png` |
| 11 | **Bundled Adhan Audio Library** | `AdhanLibraryScreen` (`adhan_library/{target}`) | Searchable offline Adhan catalog with category filter chips (`الكل`, `مكة المكرمة`, `المدينة المنورة`, `مؤذنون مشهورون`), duration & license metadata, and live preview playback | `docs/assets/screenshots/prayer/adhan-library-light.png` | `docs/assets/screenshots/prayer/adhan-library-dark.png` |
| 12 | **Prayer & Adhan Reliability Onboarding** | `PrayerOnboardingScreen` (`prayer_onboarding`) | 5-step Android alarm & notification readiness checklist (`3 / 5` complete) covering Notifications, Adhan Notification Channel, Exact Alarms, Background Restrictions, and Location | `docs/assets/screenshots/prayer/prayer-onboarding-light.png` | `docs/assets/screenshots/prayer/prayer-onboarding-dark.png` |

### 2.4 Audio Recitations & Media3 Player (`docs/assets/screenshots/audio/`)

| # | Screen / View | Composable / Route | Seeded UI State | Light Mode Path | Dark Mode Path |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 13 | **Audio Recitations & Offline Downloads** | `AudioScreen` (`audio`) | Horizontal reciter carousel (`مشاري العفاسي` selected, `محمود خليل الحصري`, etc. at `128 kbps`), filter chips (`الكل (114)`, `المحملة (0)`), Surah list with play and offline download buttons, and `MiniPlayerBar` | `docs/assets/screenshots/audio/audio-light.png` | `docs/assets/screenshots/audio/audio-dark.png` |
| 14 | **Full-Screen Now Playing Sheet** | `NowPlayingScreen` (`now_playing`) | Active Surah playback (`الفاتحة` — `مشاري العفاسي`, `الآية 1 من 7`), seek slider, transport controls, Open in Mushaf button, Ayah/Range repeat modes, and sleep timer chips (`بدون مؤقت`, `بعد الآية`, `بعد السورة`, `10/20/30 دقائق`) | `docs/assets/screenshots/audio/now-playing-light.png` | `docs/assets/screenshots/audio/now-playing-dark.png` |

### 2.5 Quranic Wallpaper Studio (`docs/assets/screenshots/wallpapers/`)

| # | Screen / View | Composable / Route | Seeded UI State | Light Mode Path | Dark Mode Path |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 15 | **Wallpaper Studio — Live Preview & Templates** | `WallpapersScreen` (`wallpapers`) | Live rendered canvas preview card (`الإسراء 17:41`), Random Ayah & Fullscreen Preview actions, 6 curated design templates (`الفخامة الزمردية` selected, `كريمي بسيط`, `ذهب منتصف الليل`, `العمارة الإسلامية`, etc.), and target screen selector (`الرئيسية`, `القفل`, `كلاهما`) | `docs/assets/screenshots/wallpapers/wallpapers-light.png` | `docs/assets/screenshots/wallpapers/wallpapers-dark.png` |
| 16 | **Wallpaper Studio — Typography & Ayah Controls** | `WallpapersScreen` (`wallpapers` — scrolled) | Font size slider (`100%`), vertical text alignment selector (`أعلى`, `وسط`, `أسفل`), manual Surah & Ayah picker, and bottom Apply bar | `docs/assets/screenshots/wallpapers/wallpapers-customization-light.png` | `docs/assets/screenshots/wallpapers/wallpapers-customization-dark.png` |
| 17 | **Wallpaper Full-Screen Preview** | `WallpapersScreen` (Fullscreen Modal) | Edge-to-edge `1080x2400` high-resolution preview of Surah Al-Isra `17:41` on the Emerald Luxury template with direct target selector and `تطبيق الخلفية` action | `docs/assets/screenshots/wallpapers/wallpapers-preview-light.png` | `docs/assets/screenshots/wallpapers/wallpapers-preview-dark.png` |

### 2.6 Daily Islamic Companion (`docs/assets/screenshots/adhkar/`, `tasbih/`, `duas/`, `hadith/`)

| # | Screen / View | Composable / Route | Seeded UI State | Light Mode Path | Dark Mode Path |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 18 | **Adhkar Hub** | `AdhkarHubScreen` (`companion_adhkar`) | Search bar, time-aware recommended session card (`أذكار المساء` / `أذكار الصباح`), daily Adhkar progress grid (`أذكار الصباح 0/26`, `أذكار المساء 0/24`, `أذكار بعد الصلاة 0/8`, `أذكار النوم 0/15`, `أذكار الاستيقاظ 0/4`), and quick shortcuts to Tasbih & Duas | `docs/assets/screenshots/adhkar/adhkar-hub-light.png` | `docs/assets/screenshots/adhkar/adhkar-hub-dark.png` |
| 19 | **Interactive Adhkar Session** | `AdhkarSessionScreen` (`companion_adhkar_session/{id}`) | Morning Adhkar session (`أذكار الصباح`, item `1 / 26`), adjustable typography (`A+` / `A-`), vocalized Dhikr text, source grading & hadith reference, collapsible virtue section (`الفضل والمناسبة`), copy/favorite actions, and large tap counter button (`تمت القراءة 0/1`) | `docs/assets/screenshots/adhkar/adhkar-session-light.png` | `docs/assets/screenshots/adhkar/adhkar-session-dark.png` |
| 20 | **Digital Tasbih Counter** | `TasbihScreen` (`companion_tasbih`) | Selected preset (`سُبْحَانَ اللَّهِ`), target chips (`بدون هدف`, `33` active, `100`, `1000`, `مخصص`), animated circular progress ring at `7 / 33` (`متبقي 26`, `اليوم: 7`), haptic toggle, and undo/next/reset bar | `docs/assets/screenshots/tasbih/tasbih-light.png` | `docs/assets/screenshots/tasbih/tasbih-dark.png` |
| 21 | **Quranic & Prophetic Duas Library** | `DuasScreen` (`companion_duas`) | Search bar, source tabs (`قرآنية`, `نبوية`, `حسب الحال`), topic filter chips (`الكل`, `الهداية والثبات`, `المغفرة والتوبة`, `الأهل والذرية`), and Dua cards with vocalized Arabic text, Copy button, and direct `فتح في المصحف` deep-link | `docs/assets/screenshots/duas/duas-light.png` | `docs/assets/screenshots/duas/duas-dark.png` |
| 22 | **Hadith Library & Offline FTS Search** | `HadithLibraryScreen` (`companion_hadith`) | Bilingual FTS4 search bar (`ابحث في الأحاديث بالعربية أو الإنجليزية...`), collection & topic chips (`الكل`, `الأربعون النووية`, `القرآن الكريم وعلومه`, `العقيدة`), `202 حديثاً` count badge, and Hadith cards with authenticity badges | `docs/assets/screenshots/hadith/hadith-library-light.png` | `docs/assets/screenshots/hadith/hadith-library-dark.png` |
| 23 | **Hadith Reader Detail** | `HadithDetailScreen` (`companion_hadith_detail/{id}`) | Full vocalized text of Hadith #1 (`الأربعون النووية — إنما الأعمال بالنيات`), font scaling (`A+` / `A-`), bookmark and copy actions, and source attribution card (`المصدر والتخريج`) | `docs/assets/screenshots/hadith/hadith-detail-light.png` | `docs/assets/screenshots/hadith/hadith-detail-dark.png` |

### 2.7 Settings, Reminders & App Information (`docs/assets/screenshots/settings/`)

| # | Screen / View | Composable / Route | Seeded UI State | Light Mode Path | Dark Mode Path |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 24 | **Settings — Theme, Reading & Audio** | `SettingsScreen` (`settings`) | Theme selector cards (`تلقائي`, `فاتح`, `داكن`), Reading font size slider (`100%`) with live Bismillah preview, default reciter selector (`مشاري راشد العفاسي`), and Prayer & Adhan settings entry | `docs/assets/screenshots/settings/settings-light.png` | `docs/assets/screenshots/settings/settings-dark.png` |
| 25 | **Settings — Daily Reminders, Updates & About** | `SettingsScreen` (`settings` — scrolled) | Daily Islamic Companion reminder toggles & time pickers (Morning Adhkar `07:00`, Evening Adhkar `17:30`, Sleep Adhkar `22:00`, Friday Surah Al-Kahf `10:00`), Auto-Wallpaper schedule, In-App GitHub Release Updater (`1.0.0-beta.12`), and Tanzil/Fonts attribution footer | `docs/assets/screenshots/settings/settings-reminders-about-light.png` | `docs/assets/screenshots/settings/settings-reminders-about-dark.png` |

---

## 3. Capture & Quality Verification Checklist

- [x] **01. Home Dashboard** — Light (`home/home-light.png`) & Dark (`home/home-dark.png`) captured and verified
- [x] **02. Home Daily Reflection & Quick Actions** — Light (`home/home-reflection-light.png`) & Dark (`home/home-reflection-dark.png`) captured and verified
- [x] **03. Quran Index (Surahs Tab)** — Light (`quran/quran-light.png`) & Dark (`quran/quran-dark.png`) captured and verified
- [x] **04. Quran Index (Juz Tab)** — Light (`quran/quran-juz-light.png`) & Dark (`quran/quran-juz-dark.png`) captured and verified
- [x] **05. Quran Index (Bookmarks Tab)** — Light (`quran/bookmarks-light.png`) & Dark (`quran/bookmarks-dark.png`) captured and verified
- [x] **06. 604-Page Madani Mushaf Reader** — Light (`quran/mushaf-reader-light.png`) & Dark (`quran/mushaf-reader-dark.png`) captured and verified
- [x] **07. Continuous Surah Reader** — Light (`quran/continuous-reader-light.png`) & Dark (`quran/continuous-reader-dark.png`) captured and verified
- [x] **08. Prayer Times Dashboard** — Light (`prayer/prayer-light.png`) & Dark (`prayer/prayer-dark.png`) captured and verified
- [x] **09. Prayer & Calculation Settings** — Light (`prayer/prayer-settings-light.png`) & Dark (`prayer/prayer-settings-dark.png`) captured and verified
- [x] **10. Per-Prayer Adhan Voice Assignment** — Light (`prayer/adhan-voices-light.png`) & Dark (`prayer/adhan-voices-dark.png`) captured and verified
- [x] **11. Bundled Adhan Audio Library** — Light (`prayer/adhan-library-light.png`) & Dark (`prayer/adhan-library-dark.png`) captured and verified
- [x] **12. Prayer & Adhan Reliability Onboarding** — Light (`prayer/prayer-onboarding-light.png`) & Dark (`prayer/prayer-onboarding-dark.png`) captured and verified
- [x] **13. Audio Recitations & Offline Downloads** — Light (`audio/audio-light.png`) & Dark (`audio/audio-dark.png`) captured and verified
- [x] **14. Full-Screen Now Playing Sheet** — Light (`audio/now-playing-light.png`) & Dark (`audio/now-playing-dark.png`) captured and verified
- [x] **15. Wallpaper Studio (Templates & Preview)** — Light (`wallpapers/wallpapers-light.png`) & Dark (`wallpapers/wallpapers-dark.png`) captured and verified
- [x] **16. Wallpaper Studio (Typography & Ayah Picker)** — Light (`wallpapers/wallpapers-customization-light.png`) & Dark (`wallpapers/wallpapers-customization-dark.png`) captured and verified
- [x] **17. Wallpaper Full-Screen Preview** — Light (`wallpapers/wallpapers-preview-light.png`) & Dark (`wallpapers/wallpapers-preview-dark.png`) captured and verified
- [x] **18. Adhkar Hub** — Light (`adhkar/adhkar-hub-light.png`) & Dark (`adhkar/adhkar-hub-dark.png`) captured and verified
- [x] **19. Interactive Adhkar Session** — Light (`adhkar/adhkar-session-light.png`) & Dark (`adhkar/adhkar-session-dark.png`) captured and verified
- [x] **20. Digital Tasbih Counter** — Light (`tasbih/tasbih-light.png`) & Dark (`tasbih/tasbih-dark.png`) captured and verified
- [x] **21. Quranic & Prophetic Duas Library** — Light (`duas/duas-light.png`) & Dark (`duas/duas-dark.png`) captured and verified
- [x] **22. Hadith Library & Offline FTS Search** — Light (`hadith/hadith-library-light.png`) & Dark (`hadith/hadith-library-dark.png`) captured and verified
- [x] **23. Hadith Reader Detail** — Light (`hadith/hadith-detail-light.png`) & Dark (`hadith/hadith-detail-dark.png`) captured and verified
- [x] **24. Settings (Theme, Reading & Audio)** — Light (`settings/settings-light.png`) & Dark (`settings/settings-dark.png`) captured and verified
- [x] **25. Settings (Daily Reminders, Updates & About)** — Light (`settings/settings-reminders-about-light.png`) & Dark (`settings/settings-reminders-about-dark.png`) captured and verified
