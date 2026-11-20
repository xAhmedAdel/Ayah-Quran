# 01 — Reference Repository Audit (`BelalYehiaSwilam0/quran-app`)

## 1. Repository Metadata & Verification

- **URL**: `https://github.com/BelalYehiaSwilam0/quran-app`
- **Inspected Branch / Commit**: `main` @ `8f26120` (`refactor: remove redundant data info from storage panel`, pushed `2026-10-04T00:22:06Z`)
- **Repository Description**: `"مشغل القرآن الكريم - يعمل بدون إنترنت"` (Quran Player — Works Offline)
- **License**: **None (`licenseInfo: null`)** — All Rights Reserved by default under copyright law.
- **Tech Stack**: Vanilla HTML5 (`index.html`), CSS3 (`css/style.css`, `css/adhkar.css`), Vanilla JavaScript (`js/app.js` 2,028 LOC, `js/adhkar.js` 767 LOC, `js/pwa.js` 166 LOC), Service Worker (`sw.js` 116 LOC), Node.js data build script (`scripts/build-data.js` 132 LOC), static pre-generated JS data bundles (`data/texts.js` 1.41 MB, `data/timings-1..6.js` ~1.66 MB total).

### File Inventory

| File | Size | Actual Purpose |
| :--- | ---: | :--- |
| `index.html` | 29.4 KB | Single-page shell, topbar, mobile drawer, live subtitle strip, surah grid, footer player, range panel, queue drawer, sleep/download/storage modals |
| `js/app.js` | 142.9 KB | Entire Quran audio player, IndexedDB blob/meta storage, timing lookup + estimation fallback, live subtitles, cross-surah range playback, queue, bulk downloader, storage panel, sleep timer, theme toggle |
| `js/adhkar.js` | 46.6 KB | Self-contained `"أذكار المسلم"` slide-in drawer, 65 hardcoded items across 16 categories, Arabic normalization search, tap counter, custom Salawat editor |
| `js/pwa.js` | 8.6 KB | Service Worker registration, update banner, install prompt banner |
| `sw.js` | 3.8 KB | Offline cache (`quran-cache-v4.1.3`) for app shell + `data/*.js` + Google Fonts (`Amiri Quran`) |
| `scripts/build-data.js` | 5.5 KB | Offline pre-builder pulling `mp3quran.net/api/v3/ayat_timing` (6 reciters) and `api.alquran.cloud/v1/surah/{n}/quran-uthmani` |
| `data/texts.js` | 1.41 MB | Pre-fetched Uthmani ayah texts (`window.__TEXTS`) |
| `data/timings-1..6.js` | 1.66 MB | Pre-fetched per-ayah start/end timestamps for 6 reciters (`window.__TIMINGS_1..6`) |
| `css/style.css`, `css/adhkar.css` | 85.8 KB | Dark/Light styling (dark navy `#0a0d13` + gold `#d4af37` default) |

---

## 2. Comprehensive Discovered Feature Inventory

### Feature 1: Adhkar Drawer (`js/adhkar.js`)
- **What it actually does**: Opens a slide-in panel titled `"أذكار المسلم"` listing **65 hardcoded entries grouped into 16 collapsible accordion categories**:
  - `الصلاة على النبي ﷺ` (1) — special customizable entry (`type: 'prayer'`)
  - `الاستيقاظ` (2)
  - `الصباح` (13)
  - `المساء` (10)
  - `النوم` (7)
  - `الوضوء` (2)
  - `المسجد` (2)
  - `بعد الصلاة` (7)
  - `الطعام` (2)
  - `المنزل` (2)
  - `السفر` (2)
  - `المرض والرقية` (2)
  - `الهم والحزن` (2)
  - `تسبيحات` (3)
  - `سور يومية` (3) — static reminder cards (`Al-Kahf`, `Al-Mulk`, `As-Sajdah`)
  - `متفرقات` (5)
- **How users access it**: Star icon button (`#adhkarBtn`) in desktop topbar or item in mobile slide-out menu (`#mobileSidebar`).
- **UI/UX structure**:
  - Header with close button, item/category summary (`65 ذكر · 16 أقسام`), and global reset button.
  - Debounced (`140ms`) diacritic-insensitive Arabic search (`normAr`: strips harakat/tatweel, normalizes `أإآٱ -> ا`, `ى/ئ -> ي`, `ؤ -> و`).
  - Accordion list -> tapping an item slides in a detail view (`#adDetail`) with Arabic text, source badge, progress display (`current / target` + progress bar), large tap button (`اضغط للعد` or `تم القراءة` when `count === 1`), Copy button, and single-item Reset button.
  - Haptic vibration via `navigator.vibrate(18)` on normal tap and `navigator.vibrate(80)` on completion.
- **Customizable Salawat / Wird (`id: 'prayer'`)**: Allows editing both the Arabic Salawat wording (`textarea`) and daily target (`1..1000`), persisted to `localStorage` (`quran-adhkar-prayer-v1`).
- **Offline operation**: 100% offline (hardcoded JS array).
- **Permissions / external services**: None.
- **Observed limitations & defects**:
  1. **Zero persistence for standard Adhkar**: `close()` calls `resetVisualState()`, clearing `S.completed` and `S.sessionCounts` for all items. Closing a single detail (`closeDetail()`) wipes incomplete counts.
  2. **Truncated authentic texts**: Evening `Sayyid al-Istighfar` (`m5`, line 54) is cut off after `"خَلَقْتَنِي وَأَنَا عَبْدُكَ"` (missing the core supplication); `m7` (`بسم الله الذي لا يضر`), `s6`, `m6` are also truncated compared to full authentic wording.
  3. **No sequential session flow**: Completing one morning dhikr requires backing out to the list and tapping the next item manually.
  4. **No Undo button**: Accidental double-tap cannot be decremented without resetting to zero.
  5. **Unlinked `"Daily Surahs"` (`سور يومية`)**: `Al-Kahf`, `Al-Mulk`, and `As-Sajdah` are static `type: 'action'` cards that do not launch audio playback or reading of those surahs.
  6. **Inconsistent citations & dialectal UI**: Several sources lack numbers or authenticity grading (`الحاكم`, `أبو داود`), and UI toasts mix colloquial Egyptian Arabic (`"مفيش عدّادات شغالة"`, `"الصيغة فاضية"`).
  7. **No favorites, no share image/card, no English localization, no audio, no reminders**.

### Feature 2: Quran Audio Player & Live Ayah Subtitle Strip (`js/app.js`)
- **What it actually does**: Streams full-surah MP3s for 6 hardcoded Hafs reciters (`الحصري`, `العفاسي`, `العجمي`, `الغامدي`, `السديس`, `المنشاوي`) from `mp3quran.net` servers while displaying the currently recited ayah in a live subtitle strip using bundled `timings-1..6.js` and `texts.js`.
- **Fallback timing estimation**: Includes a weighted duration heuristic (`estimateTimings` / `getFatihahEstimated`) when exact timings are absent.
- **Offline operation**: Works offline for pre-downloaded surahs stored as blobs in IndexedDB (`quran-player-downloads`, v4, split into `audio` and `audio-meta` stores).

### Feature 3: Advanced Recitation Range Panel (`Range` module in `js/app.js`)
- **What it actually does**: Supports:
  - Start ayah -> end ayah within a surah (or open-ended from start ayah).
  - **Cross-surah range** (start Surah A ayah X -> end Surah B ayah Y in two legs).
  - **Duration-based end mode** (play from start ayah for H hours M minutes).
  - Repeat count `1..100` with quick presets (`1x`, `2x`, `3x`, `7x`, `40x`) and `"Same Full Surah"` toggle.
  - Visual ayah markers and range highlights on the seekbar.
- **Limitations**: Operates on full-surah MP3 timestamp seeking (`mp3quran.net` timings), not per-ayah media items; relies on `mp3quran.net` server availability for non-downloaded surahs.

### Feature 4: Playback Queue Drawer (`Queue` module)
- **What it actually does**: Slide-out drawer allowing users to add multiple surahs to a custom playback queue, play from any index, shuffle queue, remove entries, or clear queue (persisted in `localStorage`).

### Feature 5: Bulk Multi-Select Downloader & Storage Manager (`Downloads` & `StoragePanel`)
- **What it actually does**:
  - Multi-select modal with `"Select All"` to download multiple surahs for the active reciter (2 concurrent downloads, per-item progress bar, cancel support).
  - Storage Manager modal using `navigator.storage.estimate()` to display used/available quota bar, total surahs/reciters/bytes, and grouped per-reciter accordion list with individual delete and `"Clear All"`.

### Feature 6: Sleep Timer, Speed, Theme Toggle, PWA Lifecycle
- **Sleep Timer**: Custom H:M + presets (`5, 15, 30, 45, 60 min`) with live countdown badge in header.
- **Playback Speed & Volume**: Cycling speed chip, drag volume slider, mute toggle, keyboard shortcuts.
- **Theme**: Dark/Light toggle persisted in `localStorage`.
- **PWA (`js/pwa.js`, `sw.js`)**: Service Worker app-shell cache, standalone update banner, install prompt.

---

## 3. Features Explicitly Absent from the Reference Repository

To avoid false assumptions, the following categories were **searched directly in all source files and confirmed NOT present** in `BelalYehiaSwilam0/quran-app`:

| Category | Status in Reference Repo |
| :--- | :--- |
| Hadith Library (collections, chapters, grading, search) | **Not implemented** (zero files/code) |
| Standalone Digital Tasbih (multi-counter, undo, targets, history) | **Not implemented** (only 3 static items under `تسبيحات` + 1 custom Salawat card inside Adhkar drawer) |
| Dedicated Dua Library (Quranic / Prophetic / categorized) | **Not implemented** (~10 situational duas folded into Adhkar categories) |
| Quran Mushaf / Continuous Text Reader & Bookmarks | **Not implemented** (audio player + single-ayah live subtitle strip only) |
| Prayer Times / Adhan / Qibla / Hijri Calendar | **Not implemented** |
| Notifications / Daily Adhkar or Hadith Reminders | **Not implemented** |
| Daily Home Hub / Favorites / Reading Goals | **Not implemented** |
| English Localization / LTR Support | **Not implemented** (Arabic-only UI) |
| Islamic Wallpapers | **Not implemented** |

---

## 4. License & Legal Verdict on Reference Repository

- **Repository License**: None declared (`licenseInfo: null`, no `LICENSE` file in tree).
- **Third-Party Data Inside Repo**:
  - `data/texts.js`: Fetched via `api.alquran.cloud/v1/surah/{n}/quran-uthmani` with no upstream attribution file.
  - `data/timings-1..6.js` and audio URLs: Pulled from `mp3quran.net` API/servers with no documented redistribution license.
  - `js/adhkar.js` `DATA`: Manually authored/abridged array with no provenance or license header.
- **Compliance Rule for Ayah**: **Zero files, code snippets, CSS rules, timing tables, or Arabic text strings from `BelalYehiaSwilam0/quran-app` may be copied into Ayah.** Only high-level UX concepts (custom Salawat target, diacritic-normalized Arabic search, Daily Surahs deep-link idea, distinct tap/complete haptics, storage breakdown UX) are adopted as original clean-room implementations in Kotlin/Compose.
