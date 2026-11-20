# 04 — UX Information Architecture & Navigation Strategy

## 1. Guiding UX Principles

1. **Protect the UI-0..UI-9 Redesign**: Never regress calm visual balance, generous whitespace, or legibility at `1.5x` system font scaling.
2. **Maximum 5 Bottom Navigation Destinations**: Material 3 hard ceiling for clarity and RTL/LTR label stability. Never add a 6th tab.
3. **1-Tap Discovery for Daily Worship**: Morning/Evening Adhkar, Tasbih, Hadith, and Duas must be reachable within 1 tap from Home without turning Home into a noisy multi-card dashboard.
4. **Session Continuity Over Screen Hopping**: Completing Morning Adhkar or counting 33 Tasbih should feel seamless and one-handed, with zero repetitive list-to-detail back-navigation.
5. **Unified Content Model, Distinct Religious Provenance**: Never blur the line between Quranic text, Prophetic Hadith, and Hisn al-Muslim Adhkar — every screen clearly attributes its exact source.

---

## 2. Navigation Architecture Evaluation

### Option A (Recommended Default): Keep Approved 5 Tabs + 1-Tap Companion Hub

```text
Bottom Bar (Unchanged — 5 Tabs)
├── Home (الرئيسية)
│   ├── Settings (top bar icon) -> Companion Reminders & Home preferences added cleanly
│   ├── Companion Strip (4 calm tonal tiles: Adhkar · Tasbih · Hadith · Duas)
│   │   ├── Adhkar Hub -> Guided Adhkar Session (Morning / Evening / After Prayer / Sleep / Waking / Daily Surahs)
│   │   ├── Digital Tasbih (one-handed counter + presets + custom Dhikr/Salawat)
│   │   ├── Hadith Library (Collections / Chapters / Search / Favorites / History -> Hadith Detail + Share Card)
│   │   └── Duas Hub (Quranic Duas [live from Mushaf] · Prophetic Duas · Situational -> Detail / Mushaf jump)
│   └── Daily Reflection Card (calm 2-segment toggle: Daily Ayah | Daily Hadith)
├── Quran (القرآن) — Unchanged (Surahs · Juz · Bookmarks)
├── Prayer (الصلوات) — Unchanged + contextual "After-Prayer Adhkar" action chip below timetable
├── Audio (الصوت) — Unchanged
└── Wallpapers (الخلفيات) — Unchanged
```

**Why Option A is recommended**:
- Zero disruption to the approved UI-2 5-tab architecture and existing `TopLevelDestinationTest` / `AppUiTest` contracts.
- Every new Companion pillar (`Adhkar`, `Tasbih`, `Hadith`, `Duas`) is **1 tap from Home** (the landing screen on every app launch).
- Contextual bridges make discovery natural when users actually need them:
  - `PrayerTimesScreen` -> **After-Prayer Adhkar** chip right below the 5 prayers.
  - `Adhkar` -> **Daily Sunnah Surahs** (`Al-Kahf` on Fridays, `Al-Mulk` & `As-Sajdah` nightly) open `MushafPageReaderScreen` directly.
  - `Quranic Duas` -> **Read in Mushaf** jumps directly to the ayah's page.
  - `Hadith / Dhikr / Dua` -> **Share as Card** generates a Direction D card image via Android Share Sheet.

### Option B (High-Frequency Alternative for Approval): Promote Companion to 5th Bottom Tab

```text
Bottom Bar (5 Tabs — Evolved)
├── Home (الرئيسية) — Includes Wallpaper summary card (1-tap to Wallpapers Studio)
├── Quran (القرآن)
├── Companion (الأذكار / الورد) — Unified 4-segment hub: Adhkar · Tasbih · Hadith · Duas
├── Prayer (الصلوات)
└── Audio (الصوت)
(Wallpapers Studio remains 1-tap from Home card, Settings, Reader Ayah More menu, and Hadith/Dhikr Share)
```

**Trade-off vs. Option A**:
- **Pro**: Muslims open Adhkar, Tasbih, and Hadith 2–5 times per day versus Wallpapers ~once a week; giving Companion a dedicated bottom tab surfaces it from every top-level screen.
- **Con**: Moves `Wallpapers` out of the bottom bar (requires updating `TopLevelDestination` and `AppUiTest`).
- Both options preserve the 5-tab ceiling and identical internal screens; the final choice is presented explicitly in `10-DECISIONS-AND-OPEN-QUESTIONS.md`.

### Option C (Rejected): 6 Bottom Navigation Tabs
Rejected because 6 tabs violate Material 3 ergonomics, compress horizontal touch targets below `48dp` on narrow phones, and cause Arabic/English label clipping at `1.5x` font scaling.

---

## 3. Home Screen Integration (Zero Dashboard Clutter)

Current Home vertical hierarchy (`HomeScreen.kt`) has 6 calm blocks. Under the expansion, Home stays at **the exact same calm density**:

| Slot | Current Home (`v1.0.0-beta.9`) | Expanded Home (Default) | Optional / Configurable? |
| :--- | :--- | :--- | :--- |
| 1 | Amiri Header + Hijri Date + Settings | Unchanged | Always shown |
| 2 | Conditional Prayer Onboarding Banner | Unchanged (disappears once completed) | Conditional |
| 3 | Continue Reading Hero (Sanctuary Emerald) | Unchanged | Always shown |
| 4 | Live Next Prayer Card | Unchanged | Always shown |
| 5 | *(New — compact single row)* | **Companion Quick Strip**: 4 compact tiles (`Adhkar` with smart time-of-day badge e.g. `Morning · 0/13`, `Tasbih`, `Hadith`, `Duas`) | Always shown (1 compact row, ~84dp tall) |
| 6 | Daily Ayah Card | **Daily Reflection Card**: clean pill toggle `[ Daily Ayah | Daily Hadith ]` inside the SAME single card footprint | Daily Hadith toggleable in Settings |
| 7 | Wallpaper Summary Card + Bookmarks/Audio Row | Unchanged | Always shown |

**Why this works**: Home gains immediate 1-tap access to ALL four companion experiences + Daily Hadith while adding only **one compact horizontal row** (~84dp) — zero stacked quote cards, zero chart widgets, zero cognitive overload.

---

## 4. Core User Flows

### Flow 1: Guided Morning / Evening Adhkar Session
1. User taps **Adhkar** on Home Companion Strip (or opens Companion Hub -> Adhkar).
2. Smart time-of-day highlight puts **Morning Adhkar** (Fajr -> Dhuhr) or **Evening Adhkar** (Asr -> Isha) or **Sleep Adhkar** (after Isha) at the top with live progress (`4 / 13`).
3. User taps the card -> enters **Guided Session Mode** (horizontal `HorizontalPager` + top `AyahStepProgress` bar, e.g., `4 / 13`):
   - Reads clear Amiri Arabic dhikr text + optional collapsible translation/virtue (`Fadl`) + verified source citation.
   - Taps anywhere on the large lower count button (`1 / 3` -> `2 / 3` -> `3 / 3`) -> subtle haptic pulse -> on reaching target (`3 / 3`), stronger completion pulse + smooth optional auto-advance to next dhikr after `450ms` (respecting reduced motion).
   - Accidental tap? Single-tap **Undo (`-1`)** button right beside counter.
   - Reaching the final item displays a calm completion summary (`Session Complete`) with option to return or reset.
   - Leaving mid-session **auto-saves exact progress to Room** (resets automatically at the next Hijri/local day boundary, never wiped on back navigation!).

### Flow 2: One-Handed Digital Tasbih
1. User taps **Tasbih** on Home Companion Strip (or opens from a high-count Dhikr like `33x` / `100x`).
2. Active Dhikr selector pill at top (`SubhanAllah · 33`, `Alhamdulillah · 33`, `Allahu Akbar · 33`, `Astaghfirullah · 100`, `Salawat · 10`, or `+ Custom Dhikr`).
3. Lower 55% of the screen is a **large Sanctuary Emerald/Parchment tactile counting surface** with circular progress arc, tabular counter (`17 / 33`), and subtle scale/ripple feedback.
4. Bottom utility bar (reachable by thumb): **Undo (`-1`)**, **Haptic/Sound quick toggle**, **Reset** (guarded by `ConfirmDialog`).
5. Reaching target gives a distinct double-pulse haptic and increments completed-rounds counter quietly without intrusive popups.

### Flow 3: Browse, Read, Save & Share Hadith
1. User taps **Hadith** -> lands on **Hadith Library** with `AyahSearchBar`, quick filter chips (`All`, `Saved`, `Recent`), **Daily Hadith** highlight strip, and clean **Collection Cards** (`40 Hadith An-Nawawi`, curated thematic collections from `HadeethEnc.com`: `Faith`, `Worship`, `Character`, `Supplication`, `Family`, plus optional offline books if downloaded).
2. Tapping a collection -> Chapter/Topic list -> Hadith list showing title/opening snippet, Hadith number, and verified grading badge (`Sahih` emerald / `Hasan` gold).
3. Tapping a Hadith -> **Hadith Detail Screen**:
   - Crisp Amiri Arabic Matn (adjustable font size synced or local).
   - Verified English translation (when English mode active or toggleable in Arabic mode).
   - Explicit metadata card: Collection, Book/Chapter, Hadith Number, **Authenticity Grade + Grader Attribution** (never fabricated).
   - Collapsible **Brief Explanation (`Sharh`)** when provided by HadeethEnc.com (explicitly attributed).
   - Action bar: **Favorite**, **Copy**, **Share Card** (generates clean Sanctuary Emerald / Warm Parchment PNG card with source attribution).

### Flow 4: Unified Duas & Quranic Dua -> Mushaf Bridge
1. User taps **Duas** -> 3 clean segments: **Quranic Duas (`أدعية قرآنية`)** | **Prophetic Duas (`أدعية نبوية`)** | **By Situation (`حسب الحال`)** (Anxiety, Hardship, Gratitude, Forgiveness, Healing, Travel, Guidance) + `Saved` filter & `AyahSearchBar`.
2. **Quranic Duas** pull text live from `AyahEntity` and include an **Open in Mushaf** button jumping directly to `Routes.mushaf(surahId, ayahNumber)`.
3. **Prophetic & Situational Duas** share the underlying `dhikr_items` table with `kind = PROPHETIC_DUA` — zero duplicate storage between Adhkar and Duas!

---

## 5. Route Registry Additions (`Routes` in `AyahApp.kt`)

All new routes fit cleanly inside the existing `NavHost` with `shouldShowMiniPlayer` kept `true` on browse screens and `false` only on full-focus `ADHKAR_SESSION` and `TASBIH` (or kept visible if user is listening to Quran while browsing):

```text
companion/adhkar                         -> AdhkarHubScreen
companion/adhkar/session/{categoryId}    -> AdhkarSessionScreen
companion/tasbih?counterId={counterId}   -> TasbihScreen
companion/duas                           -> DuasHubScreen
companion/hadith                         -> HadithLibraryScreen
companion/hadith/collection/{id}         -> HadithCollectionScreen
companion/hadith/detail/{hadithId}       -> HadithDetailScreen
settings/reminders                       -> CompanionRemindersScreen (from Settings Hub)
```
