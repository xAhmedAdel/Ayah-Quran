# 05 — Screen-by-Screen Redesign Specification

Complete architectural, visual, and behavioral specification for every screen in Ayah under the recommended **Al-Mushaf Al-Hadith** design system and **5-Tab + Header Settings** navigation structure.

---

## 1. Home Sanctuary (`HomeScreen.kt`)

- **Purpose**: Calm, welcoming daily companion answering immediately: *Where did I stop reading? When is the next prayer? What is today's Ayah/Wallpaper?*
- **Screen Hierarchy (Top to Bottom)**:
  1. **Compact Editorial Header (`56dp`)**:
     - Start: Refined Amiri Wordmark (`آية` / `Ayah`) + subtle Hijri date subtitle (`١٩ جمادى الأولى ١٤٤٨ هـ`).
     - End: `48dp` Settings `IconButton` (`Icons.Outlined.Settings`, subtle tonal circle) opening Unified Settings Hub.
  2. **Conditional Prayer Setup Banner** *(shown only while `!onboardingCompleted`)*:
     - Slim `16dp`-radius amber-tinted banner (`statusWarningContainer`) with bell icon, 1-line prompt (`أكمل إعداد تنبيهات الصلاة والأذان`), and compact `إعداد` pill CTA. Never dominates the entire top half of the screen.
  3. **Hero Card — Continue Reading** *(Primary Focal Point)*:
     - Deep Emerald Sanctuary card (`heroSurface`, `22dp` radius) with subtle gold hairline frame.
     - *When reading position exists*: Gold label (`متابعة القراءة`), Surah name in `28sp` Amiri (`سورة الكهف`), metadata row (`صفحة ٢٩٣ · الجزء ١٥ · الآية ١ من ١١٠`), slim gold progress bar (`heroAccent`) with percentage, and crisp light/gold pill button (`متابعة`). Entire card tappable.
     - *First launch (no saved position)*: Invites `ابدأ بقراءة الفاتحة` (`صفحة ١`) with primary CTA `ابدأ القراءة` + subtle `تصفح السور` text action.
  4. **Next Prayer Compact Card**:
     - Clean `surface` card with `1dp` border.
     - *Location configured*: Start shows Prayer icon + Next Prayer name in Amiri (`العصر`) + city (`القاهرة`); End shows live `tnum` countdown (`01:24:15`) + exact time (`15:18`) + Adhan status icon. Tap navigates to Prayer tab.
     - *No location*: Calm 1-row prompt (`مواقيت الصلاة · حدد موقعك`) + compact `اختيار مدينة` button.
  5. **Daily Ayah & Active Wallpaper Card**:
     - *When wallpaper applied*: Side-by-side rounded `76dp x 136dp` clipped thumbnail + Surah:Ayah reference (BiDi-isolated) + template name + automation pill (`تحديث كل ٦ ساعات` or `يدوي`) + `إدارة الخلفية` action.
     - *When no wallpaper applied yet*: Shows today's deterministic Daily Ayah snippet in calm Amiri typography with a direct `تخصيص كخلفية` (`Create Wallpaper`) button opening Wallpapers Studio.
  6. **Quick Access Row (2 Balanced Tonal Pills)**:
     - `المحفوظات` (`Bookmarks` with count badge if > 0) · `مكتبة التلاوات` (`Audio Recitations`).
- **States**:
  - *Loading*: Soft skeletal placeholders matching card heights (no jarring layout jump).
  - *Empty*: Honest, helpful starter prompts (never raw "nothing exists" dead ends).
  - *Error*: Inline subtle retry notice if local load fails.
- **Accessibility**: Logical RTL focus order; every card has merged semantic summary + distinct button label; countdown announces politely only on focus (never spamming TalkBack every second).

---

## 2. Quran Hub (`QuranScreen.kt`)

- **Purpose**: Effortless Quran discovery across Surahs, Juz, Pages, and Bookmarks from one coherent home.
- **Screen Hierarchy**:
  1. **Header**: Title `القرآن الكريم` (`Quran`) + trailing **`انتقال لصفحة` (Go to Page)** tonal chip (`Icons.Outlined.MenuBook` + `صفحة`) opening the Page/Juz Jump Sheet directly!
  2. **Search Pill (`AyahSearchBar`)**: Full-width rounded pill (`ابحث عن سورة...`) with search icon and instant Clear (`X`) button when non-empty.
  3. **3-Segment Switcher** (`SingleChoiceSegmentedButtonRow` / Pill Tabs):
     - **`السور` (Surahs)** — default
     - **`الأجزاء` (Juz 1–30)**
     - **`المحفوظات` (Bookmarks)** — eliminates hiding bookmarks at bottom of Home!
  4. **List Content (`LazyColumn`)**:
     - *Surahs tab*: Clean rows separated by inset hairline dividers:
       - Start: Octagonal geometric rosette badge (`38dp`) containing localized Surah number (`١`..`١١٤`).
       - Center: In Arabic: Surah name in `20sp` Amiri (shown ONCE — fixes duplicate name bug!) + metadata subtitle (`مكية · ٧ آيات · ص ١`). In English: Transliterated name (`Al-Fatihah`) + English meaning (`The Opening`) + metadata (`Meccan · 7 verses · p. 1`), with Arabic calligraphy (`الفاتحة`) cleanly aligned at the End!
       - End (Arabic): Subtle page tag (`ص ١`) + chevron.
     - *Juz tab*: 30 rows (`الجزء ١`..`الجزء ٣٠`) showing starting Surah, starting Ayah snippet, and page range (`ص ١–٢١`); tapping opens Mushaf directly at that Juz's starting page.
     - *Bookmarks tab*: Embedded `BookmarksContent` showing Uthmani verse snippet, localized Surah name + Ayah number (fixing Latin-only bug!), tap to open reader at that exact ayah, and trailing remove-bookmark icon button with Undo snackbar.
- **States**:
  - *Importing (first launch)*: Centered serene card with circular progress, localized percentage (`%`), and `"جارٍ تجهيز المصحف..."`.
  - *Import Error*: Honest `AyahEmptyState` with reason and primary `إعادة المحاولة` button.
  - *Search No Results*: `AyahEmptyState` with search-off icon and `"لا توجد نتائج لـ «query»"` + `مسح البحث` button.
  - *Bookmarks Empty*: Calm guidance `"اضغط على أي آية أثناء القراءة لحفظها هنا"`.

---

## 3. Mushaf Page Reader (`MushafPageReaderScreen.kt`)

- **Protected Core**: `MushafText.kt` (`buildMushafString`, `findAyahAtOffset`), 604-page mapping (`GetMushafPageUseCase`, `PageForAyahUseCase`), `HorizontalPager` pinch-zoom + double-tap reset, `MushafFollowPolicy`, reading-position persistence.
- **Purpose**: Peaceful, distraction-free, heirloom-quality 604-page Quran reading experience.
- **Screen Hierarchy**:
  1. **Canvas**: Full-screen `mushafCanvas` (`#FBF7EE` Light / `#101613` Dark). Global `MiniPlayerBar` is **suppressed** here so it NEVER stacks above the reader TopAppBar!
  2. **Minimalist Top Bar (`52dp`, subtle `surface` wash + bottom hairline)**:
     - Start: Back arrow (`AutoMirrored`).
     - Center/Title: Surah name (`سورة الملك`) + compact subtitle (`الجزء ٢٩ · ص ٥٦٣`).
     - End (max 2 clean actions):
       - **Reading Options** (`Icons.Outlined.Tune`) -> opens **Reader Settings Sheet** (Switch Mushaf / Continuous Mode, Bookmark current ayah/page, Jump to Page/Juz, Audio Reciter shortcut).
       - **Quick Mode Pill or Bookmark indicator** (kept minimal; zero icon clutter).
     - *Test-safety note*: Top & bottom chrome remain **visible by default** on open (preserving `MushafPageReaderUiTest` expectations), with an optional subtle full-screen toggle in Reader Settings rather than hijacking single/double-tap gestures used for ayah selection and zoom reset!
  3. **Mushaf Page Content (Inside `HorizontalPager` + `ZoomableMushafPage`)**:
     - **Ornamental Inline Surah Header** *(replaces floating white `AyahCard` shadow box)*: Full-width subtle parchment-tinted band (`primaryContainer` @ 35%) framed by top and bottom antique-gold hairline rules + minimal side diamond flourishes, centered Surah name (`30sp` Amiri Bold, `primary`), and Bismillah gracefully seated beneath with generous breathing room.
     - **`MushafText`**: Unchanged `AnnotatedString` pipeline; upgraded highlight token (`mushafAyahHighlight` at 22% gold wash) for clear visibility in both Light and Dark modes, generous bottom padding (`24dp`) so the last line never clips against the bottom bar.
  4. **Contextual Floating Ayah Action Pill** *(appears smoothly above bottom bar ONLY when an ayah is tapped/selected)*:
     - Displays `الملك · الآية ١٤` + 3 clear icon+label actions:
       - **`تشغيل` (Play from Ayah)** -> starts per-ayah recitation immediately.
       - **`حفظ` / `محفوظة` (Bookmark)** -> toggles bookmark with filled gold icon when saved.
       - **`خلفية` (Wallpaper)** -> opens `preview/{surahId}/{ayahNumber}` directly! *(Connects Reader to Wallpaper Studio using existing route!)*
     - Plus subtle `X` to dismiss selection (resuming recitation auto-follow).
  5. **Compact Active Audio Strip** *(appears seamlessly above bottom nav bar ONLY when recitation is active & no ayah selection pill is open)*:
     - `44dp` slim pill: Reciter + `الآية ٢١ / ٣٠` + Pause/Play + tap to open Now Playing. Never pushes down the TopAppBar!
  6. **Calm Bottom Page Bar (`52dp`)**:
     - RTL-accurate Previous Page button (`KeyboardArrowRight` in RTL, `48dp`), centered tappable Page Pill (`صفحة ١ من ٦٠٤` -> opens redesigned **Page Jump Sheet/Dialog** with numeric keypad + validation `1..604` + Cancel/Go buttons), and RTL-accurate Next Page button (`KeyboardArrowLeft` in RTL, `48dp`).

---

## 4. Continuous Reader (`ReaderScreen.kt`)

- **Purpose**: Comfortable, adjustable-font continuous surah reading with unified chrome matching Mushaf Reader.
- **Key Fixes & Redesign**:
  1. **Unified Minimal Top Bar**: Back + `سورة الفاتحة` + only 2 actions: **`Aa` Font Size** (`Icons.Outlined.FormatSize` -> opens clean bottom **Reading Controls Sheet** with Font Scale slider `0.5x–2.0x`, localized Arabic/English size labels, working Close button, Auto-Scroll toggle with clear label `"متابعة التلاوة تلقائياً"`, and Mode Switch `"التبديل لوضع المصحف"`). Eliminates the 5-icon jammed TopAppBar, removes the broken no-op close button (`onClick = {}`), and retires the baffling `ScreenRotation` icon!
  2. **Refined Editorial Surah Header**: Matches Mushaf ornamental inline banner + single metadata subtitle (`مكية · ٧ آيات · الجزء ١ · ص ١`). Removes duplicate Surah name.
  3. **Floating Contextual Ayah Action Pill**: Anchored cleanly at bottom whenever an ayah is tapped (so it is ALWAYS visible even in 286-ayah Al-Baqarah, fixing the buried-at-bottom bug!). Offers **تشغيل من الآية (Play from Ayah)** + **حفظ (Bookmark — wiring the already-implemented `ReaderViewModel.toggleBookmark`!)** + **خلفية (Wallpaper)**.
  4. **Bottom Surah Bar**: RTL-corrected Previous Surah / Next Surah buttons (matching Mushaf direction!) flanking current progress.

---

## 5. Audio Studio (`AudioScreen.kt`)

- **Protected Core**: Consumes `AudioViewModel` (`ExoPlayerAyahPlaylistPlayer.state`, `QuranAudioDownloadRepository.states`, `AudioPreferencesRepository.preferences`). Removes unused legacy Phase-5 `PlaybackControls`/`SeekBarRow` rendering.
- **Purpose**: Effortless reciter browsing, instant streaming, and crystal-clear offline download management.
- **Screen Hierarchy (Single `LazyColumn` — fixing critical 114-row eager composition bug!)**:
  1. **Top Bar**: Title `الاستماع والتلاوات` (`Recitations`).
  2. **Adhan Priority Banner** *(only when `blockedByAdhan == true`)*: Clear amber/red status card explaining Adhan is currently active.
  3. **Reciters Horizontal Carousel (`LazyRow` with subtle edge fade)**:
     - 5 refined visual Reciter Cards (`164dp` wide, `16dp` radius): soft monogram/waveform emblem, localized reciter name (`nameAr` in Arabic, `nameEn` in English!), style & quality tag (`مرتل · 128 kbps`), and downloaded surahs count badge. Selected card gets emerald fill or gold border + check icon.
     - Compact honest attribution footnote directly beneath (corrected Arabic copy — removes inaccurate `"رحم الله القرّاء"` for living reciters).
  4. **Sticky Filter & Search Bar**:
     - `AyahSearchBar` (`ابحث عن سورة...`) + Filter chips: `الكل (114)` · `المحملة (N)`.
  5. **Virtualized Surah List (`items(filteredRows, key = { it.surah })`)**:
     - Each row (`min 64dp` height):
       - Start: Octagonal Surah number badge (morphs to animated waveform / emerald highlight when `row.isCurrent`).
       - Center: Localized Surah name + clean status line:
         - Default: `٧ آيات` (localized plurals — fixes hardcoded `"آية "` in English!).
         - Downloading: `جارٍ التحميل · ١٢ / ٢٨٦` + slim `3dp` `LinearProgressIndicator` beneath.
         - Downloaded: Emerald badge `متاح دون اتصال`.
         - Failed: Error text `فشل التحميل` with retry cue.
       - End: `48dp` Play/Pause circular tonal button (with dynamic accurate `contentDescription`!) + `48dp` Download / Cancel / Delete icon button (Delete triggers `ConfirmDialog`!).
  6. **Docked Mini Player**: Now visible at bottom of Audio Studio too (above Bottom Navigation Bar) and tappable to open **Now Playing** — fixing the trapped sleep-timer/speed bug!

---

## 6. Now Playing Screen (`NowPlayingScreen.kt`)

- **Purpose**: Serene, complete audio control surface. Global `MiniPlayerBar` MUST be hidden on this route.
- **Screen Hierarchy**:
  1. **Top Bar**: Down-chevron / Back button (`إغلاق`), title `قيد التشغيل` (`Now Playing`), and trailing **`المصحف` (Open in Reader)** tonal pill button jumping directly to the currently recited Surah & Ayah!
  2. **Calligraphic Centerpiece**:
     - Calm Emerald/Gold geometric medallion (`160dp`) displaying the Surah name in `32sp` Amiri Bold + Reciter localized name below + subtle offline/streaming pill (`متاح دون اتصال` or `بث مباشر`).
     - If `blockedByAdhan`, displays prominent honest Adhan-active banner.
  3. **Ayah Progress & Seek Bar**:
     - Large clear Ayah counter (`الآية ٦ من ٧`) + interactive `Slider` wired to `MiniPlayerViewModel.positionMs()` / `durationMs()` with `tnum` elapsed and remaining time labels.
  4. **Primary Transport Cluster**:
     - Prominent `64dp` circular Primary Play/Pause button flanked by `48dp` Previous Ayah / Next Ayah buttons + subtle Stop button and Repeat Ayah toggle (`تكرار الآية`).
  5. **Secondary Utility Row (3 Clear Pill Triggers opening clean Bottom Sheets / inline `FlowRow`s)**:
     - **نطاق التكرار (Repeat Range)**: Exposes `viewModel.setRepeatRange(QuranRepeatRange(start, end))` with simple From-Ayah / To-Ayah selectors (currently unexposed backend capability!).
     - **مؤقت النوم (Sleep Timer)**: Shows active badge (e.g. `بعد السورة` or `١٥ د`) -> sheet/wrapping `FlowRow` with Off, After Ayah, After Surah, 10m, 20m, 30m.
     - **السرعة (Speed)**: Shows current speed (`1.0x`) -> clean selector (`0.75x`, `1.0x`, `1.25x`, `1.5x`).
- **Idle State**: Calm `AyahEmptyState` with button to browse Audio Studio.

---

## 7. Prayer Times & Adhan Hub (`PrayerTimesScreen.kt`)

- **Purpose**: Glanceable spiritual clock with clear timetable, per-prayer Adhan status, and honest reliability indicator.
- **Screen Hierarchy**:
  1. **Top Bar**: Title `مواقيت الصلاة` (`Prayer Times`) + trailing location switch pill + Settings gear icon (`prayer/settings`).
  2. **No-Location State**: Centered calm setup card with location icon, clear privacy-first note (`يُحسب محلياً بالكامل بدون تتبع`), primary `استخدام موقعي` button, and secondary `اختيار مدينة` button opening a clean **Searchable City Bottom Sheet**.
  3. **Sanctuary Hero Card (When Location Set)**:
     - Deep Emerald Sanctuary card (`heroSurface`, `22dp` radius).
     - Top row: City · Country pill (tappable to change city) + Adhan Active/Muted badge.
     - Center: Localized Hijri (`١٩ جمادى الأولى ١٤٤٨ هـ`) & Gregorian dates -> label `الصلاة القادمة` -> Prayer Name in `32sp` Amiri (`المغرب`) -> `36sp` tabular countdown (`00:42:18`) -> exact time (`17:46`) + slim subtle progress bar showing elapsed fraction of the current prayer window.
  4. **Daily Timetable Card**:
     - Single clean `surface` card (`16dp` radius) listing Fajr, Dhuhr, Asr, Maghrib, Isha:
       - Upcoming prayer row gets a soft tinted rounded pill (`primaryContainer` @ 55%) + bold `primary` text + small `القادمة` badge.
       - Each row displays: Prayer icon + Prayer name (Amiri `18sp`) + Time (`tnum`, `17sp` SemiBold) + trailing **Adhan Mode Icon** (Full Adhan speaker / Notification bell / Silent / Off) — tapping a row opens a lightweight **Quick Prayer Adhan Sheet** to switch mode or jump to voice selection!
     - Subtle hairline divider + compact muted **الشروق (Sunrise)** row with clear inline note (`وقت الشروق — ليس صلاة`).
  5. **Conditional Reliability Health Strip** *(shown only when `!exactAlarmGranted` or notifications denied)*:
     - Calm `statusWarningContainer` card explaining exact impact (`قد يتأخر الأذان حتى ١٠ دقائق`) with one-tap `مراجعة الإعدادات` button opening Onboarding or Prayer Settings.

---

## 8. Prayer Settings (`PrayerSettingsScreen.kt` + `PermissionsDashboardSection.kt`)

- **Purpose**: Replace the exhausting 8-card vertical scroll wall (and 3x duplicated permission sections) with a calm, organized settings list.
- **Screen Hierarchy (4 Clean Logical Groups)**:
  1. **الموقع (Location)**: Current city row + inline `تحديث تلقائي` / `تغيير المدينة` actions.
  2. **طريقة الحساب والتعديل (Calculation & Adjustments)**:
     - Single summary row for **طريقة الحساب (Calculation Method)** showing current value (`الهيئة المصرية العامة للمساحة`) -> taps open a calm **Selection Bottom Sheet** with radio list of all 10 methods (replaces 10 vertical stacked chips!).
     - Single summary row for **حساب العصر (Asr Method)** -> 2-option segmented control (`الجمهور` / `الحنفي`).
     - Single summary row for **خطوط العرض العالية (High Latitude)** -> Bottom Sheet (3 options).
     - Single summary row for **تعديل الدقائق (Minute Adjustments)** showing active summary (`افتراضي` or `+2 فجر`) -> opens clean Bottom Sheet with 5 prayer `-` / `0` / `+` steppers.
     - Single summary row for **تعديل التاريخ الهجري (Hijri Adjustment)** (`-2`..`+2` days, exposing existing `setHijriAdjustment`!).
  3. **الأذان والأصوات (Adhan & Voices)**:
     - Prominent navigation row **أصوات الأذان لكل صلاة (Adhan Voices)** -> opens `AdhanVoiceScreen`.
     - Compact 5-row **نمط التنبيه لكل صلاة (Per-Prayer Mode)**: Prayer name + current mode pill (`أذان كامل` / `نغمة إشعار` / `صامت` / `متوقف`) -> tap opens quick bottom sheet (eliminating 20 squeezed FilterChips!).
     - **ملف أذان مخصص (Custom Audio File)**: Current filename or none + Pick / Clear buttons. *(Stale `"لا توجد تسجيلات أذان مرخّصة بعد"` copy DELETED!)*
  4. **الإشعارات والموثوقية (Notifications, Reliability & Diagnostics)** *(Single unified section — replaces 3 duplicated cards!)*:
     - 5 honest verified `StatusRow`s with semantic `AyahStatusBadge`s (Notifications, Adhan Channel, Exact Alarms, Background Launch, Adhan Modes) — each ungranted row has a single clear action button (**fixing the `"ممنوح"` bug** -> `"تفعيل الإشعارات"`!).
     - Collapsible **أدوات الفحص والتشخيص (Diagnostics)** accordion containing: `إعادة تشغيل دليل الإعداد`, `فتح إعدادات البطارية`, `اختبار الإشعار`, `اختبار تشغيل الأذان`, `إنذار تجريبي بعد ٦٠ ثانية`.

---

## 9. Prayer Setup Stepper (`PrayerOnboardingScreen.kt`)

- **Purpose**: Guided, calm, non-intimidating 5-step permission setup (especially vital for HONOR/MagicOS and Android 14+).
- **Layout**:
  - Top bar with Back/Close + linear step progress (`الخطوة ١ من ٥`).
  - Clean vertical checklist where completed steps collapse into compact green checkmark summary rows, the **Active Step** expands into a spacious highlighted card with clear plain-Arabic/English explanation and full-width Primary CTA (`تفعيل الإشعارات`, `فتح إعدادات القناة`, `السماح بالمنبهات الدقيقة`, `عرض دليل الجهاز`, `اختيار نمط الأذان`), and future steps stay quiet and dimmed.
  - Replaces confusing `RadioButtonUnchecked` with numbered circular step badges (`١`, `٢`, `٣`, `٤`, `٥`) and `CheckCircle` when verified.
  - Honest Ready celebration card when `board.readyForPrayerNotifications` is true; clean bottom `"تخطي الآن"` button with plain warning.

---

## 10. Adhan Voices (`AdhanVoiceScreen.kt`) & Adhan Library (`AdhanLibraryScreen.kt`)

- **Adhan Voices**:
  - Top quick-action card: **`تطبيق صوت واحد لجميع الصلوات`** (lets user pick once instead of 5 times!).
  - Single grouped `surface` card with 5 rows (Fajr through Isha): Prayer name (Amiri), current clean voice name (no raw `(CC0)` in primary title), inline preview Play button, and `تغيير` chevron opening Library.
- **Adhan Library**:
  - Top bar `مكتبة الأذان — الفجر` + `AyahSearchBar` (with clear button) + single-line `LazyRow` category chips (`الكل`, `مكة`, `المدينة`, `مشهورون`, `مناطق`, `مدمجة`, `المحملة` — concise labels so zero 2-line wrapping!).
  - Honest Makkah unavailability `AyahEmptyState` when Makkah filter selected.
  - Clean Recording Cards:
    - Start: `44dp` circular Play/Pause preview button (shows circular progress ring while previewing).
    - Center: Clean localized title, Muadhin/Mosque subtitle, duration + subtle license caption (`2:34 · CC0`).
    - End: Single context-aware action:
      - Selected: Emerald `محدد` badge with check.
      - Bundled or Downloaded: Tonal `اختيار` pill (+ subtle delete icon ONLY on downloaded non-bundled recordings — **fixes Delete icon appearing on un-downloaded recordings!**).
      - Remote not downloaded: Outlined `تحميل · 3.0 MB` pill (tapping downloads directly; while downloading shows linear progress + `%` + cancel `X`).

---

## 11. Wallpapers Studio (`WallpapersScreen.kt`) & Fullscreen Preview (`FullscreenPreviewScreen.kt`)

- **Purpose**: Make designing, previewing, applying, and scheduling Quranic wallpapers effortless — with **live preview always visible while editing**.
- **Wallpapers Studio Layout**:
  1. **Top Bar**: Title `خلفيات آية` (`Wallpapers`) + subtle Auto-Rotation status pill (`تلقائي: ٦ س` or `يدوي`).
  2. **Sticky Upper Live Preview Stage (~40% height)**:
     - Centered crisp `9:19` phone mockup frame with **proper `clip(RoundedCornerShape(20.dp))`** (fixing unclipped sharp corners!), subtle bezel outline, live rendered `previewBitmap` (no duplicated reference text underneath!).
     - Flanked by two tactile floating tonal icon buttons: **تبديل الآية (Shuffle)** and **معاينة كاملة (Fullscreen)**.
  3. **Lower Tabbed Studio Panel (`4` Clean Tabs)**:
     - **`التصميم` (Style)**: Horizontal row of **5 Visual Template Swatches** (mini gradient/pattern cards with live emerald/cream/midnight/teal/geometric previews + check badge — keeping exact 5 template IDs!), compact Font Size slider (`0.6x–1.6x`), and 3-way Position pill (`أعلى` · `وسط` · `أسفل`).
     - **`الآية` (Verse)**: 2x2 Selection Mode cards (Random / From Selection / Sequential / Daily) + clean tappable Surah Picker Sheet + Ayah number input pre-filled with current `fixedAyahNumber` and numeric keypad.
     - **`التلقائي` (Auto)**: Auto-update switch + clean 6-chip `FlowRow` intervals (`٣٠ د`, `١ س`, `٣ س`, `٦ س`, `١٢ س`, `٢٤ س`) + last applied timestamp + plain-language scheduling hint (removes `"WorkManager"` jargon!).
     - **`السجل` (History)**: Thumbnail rows (`clip(8dp)`) with localized Surah name, timestamp, destination, status badge, Reapply button, Delete icon, and Clear All with `ConfirmDialog`.
  4. **Pinned Bottom Apply Bar**:
     - Compact 3-way Destination selector (`الرئيسية` · `القفل` · `كلاهما`) + full-width Primary Button **`تطبيق الخلفية`**. Outcome animates cleanly as icon + text pill.
- **Fullscreen Preview**:
  - True edge-to-edge bitmap; top-start Close `IconButton` inside a `40dp` translucent dark scrim circle (`scrim` token — always legible over any template!).
  - Floating subtle dark-frosted bottom bar with destination selector (`Home / Lock / Both`) and `تطبيق الآن` button reporting the **actual** selected destination (fixing hardcoded `BOTH`).

---

## 12. Unified Settings Hub (`SettingsScreen.kt`)

- **Entry Point**: Top-end Settings button on Home (1 tap, always visible).
- **Layout (`TopAppBar` + Grouped `LazyColumn`)**:
  1. **المظهر (Appearance)**: 3 visual mini-cards side-by-side (`تلقائي (النظام)` — **fixes `"توقّع النظام"`!** · `فاتح` · `داكن`) with live miniature light/dark illustrations and clear selection ring.
  2. **اللغة (Language)**: Clean 2-option card (`العربية` · `English`) with instant check indicator.
  3. **تفضيلات التطبيق (App Preferences — Unified Entry Points)**:
     - `القراءة والمصحف` -> shows current mode (`المصحف ٦٠٤ صفحة` / `نص مستمر`) & font scale -> quick sheet.
     - `الصوت والتنزيلات` -> shows default reciter & speed -> links to Audio Studio / Storage summary (shows offline audio + adhan storage count/size with clear option).
     - `مواقيت الصلاة والأذان` -> shows current city & permission health badge -> navigates to `prayer/settings`.
     - `الخلفيات والتحديث التلقائي` -> shows rotation state -> navigates to Wallpapers tab.
  4. **التحديثات (Updates)**: Clean card with honest GitHub Releases check/download/install states + determinate progress bar + manual re-check button.
  5. **حول آية (About & Licenses)**:
     - Brand mark + tagline + **BiDi-isolated version** (`\u20661.0.0-beta.6\u2069` — fixes `"beta.6-debug-1.0.0"`!).
     - Tappable **`التراخيص ومصادر البيانات` (Licenses & Attributions)** row opening a clean readable Modal Bottom Sheet (preserving all required Tanzil, Amiri, Quran.com, Wikimedia, GeoNames, AlQuran Cloud attributions with fixed Arabic spelling `"صباح فخري"` and `"نسب المصنّف"`).
