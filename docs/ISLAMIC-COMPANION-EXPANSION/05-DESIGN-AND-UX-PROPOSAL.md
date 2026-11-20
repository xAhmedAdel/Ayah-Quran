# 05 — Design & UX Proposal (Direction D: Al-Mushaf Al-Hadith Extension)

## 1. Visual Language Continuity

Every new screen strictly extends Ayah's approved **Direction D — Al-Mushaf Al-Hadith** design system (`core/designsystem/`):

- **Light Mode**: Warm Parchment canvas (`#F6F2E9`), soft elevated cards (`#FCF9F2`), Sanctuary Emerald primary (`#0F3D32`), Antique Gold accent (`#C4A052` decorative / `#8F6E2A` AA text).
- **Dark Mode**: Obsidian Moss canvas (`#0C1210`), quiet elevated surfaces (`#131B17`), Luminous Mint/Emerald highlights, Warm Gold accents (`#D6B66B`).
- **Typography**:
  - `Amiri` (`QuranTextFontFamily` / calligraphic display) for Quranic Duas, Hadith Arabic Matn, Dhikr Arabic text, and hero display headings.
  - `IBM Plex Sans Arabic` for all functional Arabic & English UI chrome, metadata, search bars, buttons, and source citations.
  - `withTabularNumbers()` + `AyahNumbering.isolateLtr()` for every counter (`17 / 33`), Hadith number (`#42`), and progress ratio (`4 / 13`).
- **Surface Discipline**: Generous `16dp` outer margins, `12dp..16dp` card spacing, hairline `1dp` borders (`AyahTouchTargets.hairline`), zero excessive gradients, zero emojis, zero decorative clutter.

---

## 2. Screen-by-Screen UX Specifications

### Screen 1: Home Companion Strip & Daily Reflection Card (`HomeScreen` Extension)
- **Companion Quick Strip**: Horizontal 4-equal-column row of clean `AyahSurfaceCard` tiles (`minHeight = 84.dp`):
  - **Adhkar** (`الأذكار`): Subtle time-of-day dot/subtitle (e.g., `Morning · 0/13` or `Evening · 10/10` with emerald check).
  - **Tasbih** (`المسبحة`): Active counter preview (e.g., `33` or active count).
  - **Hadith** (`الحديث`): Collection icon + label.
  - **Duas** (`الأدعية`): Supplication icon + label.
- **Daily Reflection Card**: Upgrades existing `DailyAyahCard` slot with a compact 2-pill header toggle (`Daily Ayah | Daily Hadith`). Switching smoothly cross-fades (respecting `rememberMotionEnabled()`) without altering vertical page rhythm.

### Screen 2: Adhkar Hub (`AdhkarHubScreen`)
- **Top Bar**: Title (`الأذكار` / `Adhkar`), Back button, Saved/Favorites filter toggle.
- **Search**: `AyahSearchBar` using diacritic-insensitive Arabic + English matching.
- **Recommended Now Hero (`AyahHeroCard`)**: Contextual Sanctuary Emerald card highlighting the current time-of-day session (e.g., `Morning Adhkar · 13 items · 0%` with one-tap `Start Session` gold pill CTA).
- **Core Daily Sessions Grid (2-column `AyahSurfaceCard`s)**:
  - Morning (`أذكار الصباح`), Evening (`أذكار المساء`), After Prayer (`بعد الصلاة`), Before Sleep (`أذكار النوم`), Waking Up (`الاستيقاظ`).
  - Each card shows title, item count, and thin linear progress indicator (`done / total` for today).
- **Daily Sunnah Surahs Strip (`AyahGroupCard`)**:
  - `Surah Al-Kahf` (highlighted with subtle gold badge on Fridays), `Surah Al-Mulk` (nightly), `Surah As-Sajdah` (nightly).
  - Each row has an authentic brief note + **Read in Mushaf** action opening `MushafPageReaderScreen` directly + today's read checkmark.
- **More Categories (`AyahGroupCard`)**: Wudu, Mosque, Food, Home, Travel, Healing/Ruqyah, Anxiety/Distress — clean single-row list items with item counts.

### Screen 3: Guided Adhkar Session (`AdhkarSessionScreen`)
- **Top Bar**: Session title (e.g., `أذكار الصباح`), step counter (`4 / 13`), Reset Session button (guarded by `ConfirmDialog`), and Toggle List/Session View button for users who prefer scrolling a full list.
- **Progress Track**: `AyahStepProgress` across the top showing exact session completion.
- **Focus Card (`HorizontalPager`)**:
  - Generous reading card on Warm Parchment (`mushafCanvas`) / Obsidian Moss surface.
  - Clear `Amiri` Arabic Dhikr text (`22sp..26sp`, generous `1.8x` line height, font-size `A- / A+` quick adjuster).
  - Verified source pill (`البخاري · 6306` / `Sahih al-Bukhari 6306`) + optional expandable **Virtue / Translation** disclosure row (collapsed by default in Arabic mode to preserve calm reading space; shown by default in English mode).
  - Utility icon row: **Favorite**, **Copy**, **Share Card**, **Open in Mushaf** (shown only for Quranic items like Ayat al-Kursi).
- **Integrated One-Handed Tap Counter (Pinned Bottom Dock)**:
  - Large `64dp+` primary pill button displaying live tabular count (`2 / 3` -> `Tap to count`, or `Mark as read` when `target == 1`) with smooth radial/linear fill bar behind the button.
  - Flanking controls: **Undo (`-1`)** on start edge (`48dp` touch target), **Next / Previous** subtle arrows for free navigation.
  - Auto-advances smoothly to the next uncompleted dhikr after completion (user-toggleable, instant jump when reduced motion is enabled).
- **Session Complete View**: Calm Sanctuary Emerald completion card summarizing total completed items for the session with `Done` and `Repeat Session` actions.

### Screen 4: One-Handed Digital Tasbih (`TasbihScreen`)
- **Layout Principle**: Strictly one-handed — top 40% is read-only display + preset selector; bottom 60% is the thumb-reachable counting surface and utility bar.
- **Top Bar**: Title (`المسبحة` / `Tasbih`), History/Manage Counters button, Settings popover (Haptic on/off, Soft Click Sound on/off).
- **Active Dhikr Selector Card**: Tappable pill/card showing active Arabic Dhikr text (e.g., `سُبْحَانَ اللَّهِ وَبِحَمْدِهِ` or custom Salawat) + target badge (`33`, `100`, `1000`, or `Free Count`). Tapping opens an `AyahSelectionSheet` listing presets + custom counters + `+ Add Custom Dhikr` button (dialog/sheet with Dhikr text + target `1..10000`, inspired by the reference repo's custom Salawat editor).
- **Center/Lower Tactile Tap Surface**:
  - Large rounded Sanctuary Emerald (or Parchment + Gold Ring) touch pad (`>= 240dp` tall, generous full-width margin) with circular gold progress ring, bold tabular count (`17`), subtle `/ 33` target, and quiet completed-rounds subtitle (`Cycle 2 · Total today: 50`).
  - Pressing triggers gentle scale compression (disabled when reduced motion is active) + optional crisp haptic tick (`HapticFeedbackType.TextHandleMove`) and stronger double pulse at target completion (`HapticFeedbackType.LongPress`).
- **Bottom Utility Row (`48dp+` targets)**:
  - **Undo (`-1`)** (disabled at `0`).
  - **Next Preset** quick cycle button (e.g., `SubhanAllah 33` -> `Alhamdulillah 33` -> `Allahu Akbar 33` seamlessly for post-prayer tasbih!).
  - **Reset** (always opens `ConfirmDialog` when `count > 0` so accidental taps never wipe 90+ counts).
- **Zero Clutter**: No streak fire icons, no XP bars, no noisy weekly bar charts.

### Screen 5: Hadith Library & Detail (`HadithLibraryScreen`, `HadithCollectionScreen`, `HadithDetailScreen`)
- **Hadith Library**:
  - `AyahSearchBar` (full-text Arabic diacritic-normalized + English search across installed collections).
  - Compact filter chips (`Collections`, `Saved`, `Recent`).
  - **Daily Hadith** preview card at top (`AyahSurfaceCard` with gold accent border + collection & grade badge).
  - **Bundled Collections**: Clean `AyahSurfaceCard` grid/list:
    - **40 Hadith An-Nawawi** (`الأربعون النووية`) — 42 foundational hadiths.
    - **HadeethEnc Thematic Chapters** (`مختارات من السنة النبوية`) — Faith, Worship, Dhikr & Dua, Character & Manners, Family & Society, Virtues.
    - **Optional Offline Books (Download Manager)** — Future/optional downloadable packs (e.g., Sahih al-Bukhari, Sahih Muslim) clearly showing download size and offline-ready badge (`AyahIcons.OfflineReady`), mirroring Ayah's Audio Download UX.
- **Hadith Detail Screen**:
  - Top bar: Collection title, font-size quick toggle (`A- / A+`), Favorite toggle.
  - Reading card (`mushafCanvas`): Full Arabic Hadith text in `Amiri` (`21sp..28sp`, generous line height).
  - Verified English translation card (when available for this edition).
  - **Verification & Reference Card (`AyahGroupCard`)**:
    - Collection & Book/Chapter name.
    - Hadith Number (BiDi-isolated).
    - **Grade Badge (`AyahStatusBadge`)**: `Sahih` (emerald), `Hasan` (gold), `Da'if` (neutral/warning if ever present in full books) + **explicit Grader Attribution** (e.g., `HadeethEnc.com` / `Sahih al-Bukhari` / `Al-Albani`). Never displays an unattributed grade.
    - Dataset Attribution & Version line (required by HadeethEnc.com terms!).
  - **Collapsible Explanation (`Sharh`)**: Smooth accordion showing concise scholarly explanation and word meanings when present in source data.
  - **Bottom Action Bar**: **Favorite**, **Copy** (formatted with source reference), **Share Card** (opens Share Card preview sheet -> renders clean PNG -> Android Share Sheet).

### Screen 6: Duas Hub (`DuasHubScreen`)
- **Top Bar**: Title (`الأدعية` / `Duas`), Saved filter icon.
- **3-Segment Switcher** (`SingleChoiceSegmentedButtonRow` matching Quran Hub):
  1. **Quranic (`قرآنية`)** — Curated authentic Quranic supplications (`Rabbana` & Prophets' Duas) rendered straight from `AyahEntity`, showing Surah:Ayah badge + **Open in Mushaf** button.
  2. **Prophetic (`نبوية`)** — Authentic Prophetic Duas from Sunnah with exact source reference.
  3. **By Situation (`حسب الحال`)** — Context chips (`Anxiety & Distress`, `Gratitude`, `Forgiveness`, `Healing`, `Travel`, `Guidance`, `Family & Parents`).
- **Search**: `AyahSearchBar` searching Arabic & English across all segments.
- **Item Card**: Crisp `Amiri` text, source badge, Favorite icon, Copy icon, Share Card icon, and `Open in Mushaf` (for Quranic Duas).

### Screen 7: Share Card Sheet (`CompanionShareCardSheet`)
- Triggered from Hadith, Dhikr, or Dua **Share Card** action.
- Offers 2–3 Direction D card styles (`Sanctuary Emerald`, `Warm Parchment`, `Obsidian Gold`) rendered using Ayah's existing offscreen Canvas + `Amiri` font typography engine.
- Always includes verified text + clean source reference footer + subtle `Ayah` wordmark.
- Exports PNG to app cache and opens native Android `Intent.ACTION_SEND` via existing `FileProvider`.

### Screen 8: Companion Reminders (`CompanionRemindersScreen` inside Settings Hub)
- Accessible from `SettingsScreen` under a single clean `AyahSettingRow` (`Daily Reminders` / `التذكيرات اليومية`).
- If `POST_NOTIFICATIONS` is denied on Android 13+, shows `AyahBannerStrip(WARNING)` with 1-tap permission/settings button (reusing `PrayerNotifications.canNotify()` and `PermissionIntents`).
- Clean `AyahGroupCard` toggles (all **OFF by default** — zero unsolicited spam):
  - **Morning Adhkar Reminder** (default `07:00`, customizable time picker).
  - **Evening Adhkar Reminder** (default `17:30`, customizable time picker).
  - **Daily Hadith Reminder** (default `09:00`, customizable time picker).
  - **Daily Quran Reading Reminder** (default `21:00`, customizable time picker).
  - **Friday Surah Al-Kahf Reminder** (default Friday `10:00`).
- **Quiet Hours Row**: Optional Start/End quiet window (e.g., `23:00 -> 05:00`) suppressing any companion reminder that drifts into sleep hours.
- **Honest Footnote**: Clearly explains that reminders are gentle, non-alarm notifications scheduled approximately by Android (never interfering with exact Prayer/Adhan alarms).

---

## 3. Component Reuse vs. New Shared Primitives

| Component | Source | Usage |
| :--- | :--- | :--- |
| `AyahHeroCard`, `AyahSurfaceCard`, `AyahGroupCard`, `AyahInsetDivider` | Existing `core/designsystem` | Adhkar hero, category grids, Hadith metadata, Settings reminders |
| `AyahSearchBar`, `AyahSelectionSheet`, `AyahStatusBadge`, `AyahStepProgress` | Existing `core/designsystem` | Search, Tasbih preset picker, Hadith grade badges, Adhkar session stepper |
| `AyahPrimaryButton`, `AyahTonalButton`, `AyahOutlinedButton`, `ConfirmDialog`, `EmptyState` | Existing `core/designsystem` | Session controls, Reset confirmation, Search empty states |
| `TasbihTapSurface` *(New)* | `ui/companion/tasbih/` | Dedicated one-handed circular-arc counting canvas with reduced-motion fallback |
| `CompanionShareCardRenderer` *(New)* | `core/share/` | Offscreen Canvas PNG generator reusing `WallpaperRenderer` text layout patterns |
| `ArabicTextNormalizer` *(New)* | `core/text/` | Pure Kotlin diacritic/alif/yaa/taa-marbuta normalizer for offline search & FTS |

---

## 4. Accessibility, Localization & State Standards

- **Touch Targets**: All interactive buttons, chips, and counters strictly `>= 48.dp` (`AyahTouchTargets.min`).
- **Font Scaling**: Verified up to `1.5x` system font scale; counters and buttons reflow vertically without clipping.
- **TalkBack Semantics**: Tasbih counter announces `"Count 17 of 33"` via `contentDescription` and `liveRegion = LiveRegionMode.Polite`; Adhkar step progress announces `"Item 4 of 13"`.
- **Reduced Motion**: `rememberMotionEnabled()` disables pager spring bounce, counter scale bounce, and animated fills when `ANIMATOR_DURATION_SCALE == 0`.
- **States**: Every screen defines explicit **Loading**, **Empty** (e.g., no favorites yet, no search results), **Error** (e.g., offline pack download failed with retry), and **Success** states — never placeholder religious text.
