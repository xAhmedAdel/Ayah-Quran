# 04 — Final Design System ("Al-Mushaf Al-Hadith")

Concrete visual language and Jetpack Compose Material 3 token specification for Ayah's redesigned UI. Designed to replace/parallel `app/src/main/java/com/ayah/quran/core/designsystem/`.

Dynamic Material You extraction remains **disabled** so Ayah maintains its calm, unmistakable identity across every Android OEM.

---

## 1. Color System

### A. Brand Foundation Tokens

| Token | HEX | Role |
|---|---|---|
| `SanctuaryEmerald` | `#0F3D32` | Light primary, Hero surfaces (Next Prayer, Continue Reading accent) |
| `SanctuaryEmeraldDeep` | `#0A2921` | Pressed states, Hero gradient end (subtle 4% tonal depth), Dark emerald hero |
| `SanctuaryEmeraldMuted` | `#1C5949` | Light tertiary, active indicator fills |
| `AntiqueGold` | `#C4A052` | Brand gold accent, Ayah verse markers `﴿١﴾`, Dark primary |
| `AntiqueGoldDeep` | `#8F6E2A` | Light secondary (WCAG AA compliant >= 4.7:1 on Parchment!) |
| `AntiqueGoldSoft` | `#E6D29E` | Text/icons on Dark Emerald hero surfaces, Dark gold highlights |
| `ParchmentCanvas` | `#F6F2E9` | Light screen background (warm, glare-free) |
| `ParchmentSurface` | `#FCF9F2` | Light elevated card surface |
| `ObsidianCanvas` | `#0C1210` | Dark screen background (green-tinted ink, never neutral gray) |
| `ObsidianSurface` | `#131B17` | Dark card surface |

### B. Complete Material 3 Color Schemes (Required Full `surfaceContainer*` Ramp)

> **Critical M3 Constraint**: All 5 `surfaceContainer*` roles MUST be explicitly defined in both schemes so Compose M3 never falls back to cold neutral gray on Cards, Sheets, NavigationBar, or Chips.

| Material 3 Role | Light (`AyahV2LightColors`) | Dark (`AyahV2DarkColors`) | Contrast Verified |
|---|---|---|---|
| `primary` | `#0F3D32` | `#D6B66B` | >= 10.2:1 (L) / >= 9.6:1 (D) |
| `onPrimary` | `#FAF7F0` | `#16201B` | >= 10.2:1 / >= 9.6:1 |
| `primaryContainer` | `#DCEAE4` | `#1B3B31` | >= 8.5:1 / >= 8.1:1 |
| `onPrimaryContainer` | `#0A2921` | `#E6D29E` | >= 8.5:1 / >= 8.1:1 |
| `secondary` | `#8F6E2A` | `#E2C887` | >= 4.7:1 / >= 10.8:1 |
| `onSecondary` | `#FFFDF8` | `#261E0A` | >= 4.7:1 / >= 10.8:1 |
| `secondaryContainer` | `#F2E5C6` | `#3A2F16` | >= 7.8:1 / >= 8.4:1 |
| `onSecondaryContainer` | `#43320E` | `#F4E5BF` | >= 7.8:1 / >= 8.4:1 |
| `tertiary` | `#1C5949` | `#6DBAA2` | >= 7.1:1 / >= 7.9:1 |
| `onTertiary` | `#FAF7F0` | `#0B221B` | >= 7.1:1 / >= 7.9:1 |
| `background` | `#F6F2E9` | `#0C1210` | Base canvas |
| `onBackground` | `#151C18` | `#F2EEE5` | 15.1:1 (L) / 15.6:1 (D) |
| `surface` | `#FCF9F2` | `#131B17` | Primary card surface |
| `onSurface` | `#151C18` | `#F2EEE5` | 15.8:1 (L) / 14.3:1 (D) |
| `surfaceVariant` | `#EAE4D7` | `#1C2621` | Input / pill fills |
| `onSurfaceVariant` | `#525C56` | `#A7B2AC` | 6.2:1 (L) / 7.8:1 (D) |
| `outline` | `#838D87` | `#6F7A74` | Interactive borders |
| `outlineVariant` | `#DDD7CA` | `#24302A` | Hairline 1dp card & list rules |
| `error` | `#B33A3A` | `#F08A8A` | 5.6:1 (L) / 7.2:1 (D) |
| `onError` | `#FFFFFF` | `#3B0B0B` | >= 5.6:1 |
| `errorContainer` | `#FBE8E8` | `#441B1B` | Alert banners |
| `onErrorContainer` | `#6E1B1B` | `#FADADA` | >= 8.2:1 |
| `surfaceContainerLowest` | `#FFFFFF` | `#090E0C` | Highest contrast sheet / crisp card |
| `surfaceContainerLow` | `#FAF7EF` | `#111815` | Subtle inset containers |
| `surfaceContainer` | `#F3EFE4` | `#161F1B` | Nav bar, Mini player, Grouped rows |
| `surfaceContainerHigh` | `#ECE7DA` | `#1D2722` | Modals, Bottom sheets, Selected chips |
| `surfaceContainerHighest` | `#E4DEC0` | `#25312B` | Progress tracks, pressed states |

### C. Semantic Extended Tokens (`AyahExtendedColors` via `CompositionLocalProvider`)

| Token | Light | Dark | Purpose |
|---|---|---|---|
| `heroSurface` | `#0F3D32` | `#122D25` | Deep Emerald Sanctuary Hero Cards (Next Prayer, Featured Continue Reading) |
| `heroOnSurface` | `#F9F5EB` | `#F5F1E8` | Primary text on Hero card |
| `heroAccent` | `#DFC178` | `#DFC178` | Gold metadata, badges, progress fill on Hero card |
| `heroMuted` | `#B7CEC6` | `#A5BEB5` | Secondary metadata on Hero card |
| `mushafCanvas` | `#FBF7EE` | `#101613` | Dedicated low-glare reading paper for Mushaf & Continuous Reader |
| `mushafAyahHighlight` | `#C4A052` @ `22%` | `#D6B66B` @ `20%` | Warm translucent ayah recitation/selection highlight (higher legibility than old 15%) |
| `mushafMarker` | `#9A762D` | `#D6B66B` | Ornamental verse marker `﴿١﴾` |
| `statusSuccess` | `#1C6B4E` | `#68C79F` | Verified permissions, downloaded offline badge |
| `statusSuccessContainer` | `#E1F2EB` | `#143327` | Status badge container |
| `statusWarning` | `#9E6310` | `#EBB45B` | Unconfigured exact alarms / vendor background attention |
| `statusWarningContainer` | `#FBF0DC` | `#362812` | Attention strip container |
| `scrim` | `#0C1210` @ `55%` | `#000000` @ `65%` | Fullscreen close pill & modal scrims |

---

## 2. Typography System

### A. Dual Font Strategy

1. **Quranic & Calligraphic Display (`QuranTextFontFamily`)**:
   - **Font**: Bundled **Amiri** (`amiri_regular.ttf`, `amiri_bold.ttf`, SIL OFL 1.1).
   - **Strict Scope**: Uthmani Quranic text (`MushafText`), Ayah previews, Surah calligraphic names, Ayah brand wordmark, and Prayer Hero title (`الفجر`, `المغرب`).
   - **Rule**: Never force Amiri onto dense utility forms, small metadata, or Latin UI labels.
2. **Arabic & English UI Chrome (`AyahUiFontFamily`)**:
   - **Recommended**: Bundle **IBM Plex Sans Arabic** (`Regular`, `Medium`, `SemiBold`, `Bold` — SIL OFL 1.1, ~180KB subset/4 weights) for unified, contemporary Arabic+Latin harmony with superior Arabic diacritic clearance and crisp Latin metrics.
   - **Zero-dependency fallback**: If owner prefers zero new font assets in Phase UI-1, use `FontFamily.Default` with refined weights, line-heights, and letter-spacing — the entire scale works identically.
3. **Tabular Numerals (`fontFeatureSettings = "tnum"`)**:
   - Mandatory on Prayer countdown (`01:42:18`), Prayer timetable (`04:38`), Audio elapsed/total timers, Download counters (`14 / 286`), and Page indicators (`142 / 604`) to prevent layout jitter.
4. **BiDi Isolation Helper**:
   - All mixed numeric/version/reference tokens inside Arabic RTL prose MUST be wrapped with Unicode LRI/PDI (`\u2066...\u2069`), e.g. `\u20661.0.0-beta.6\u2069`, `\u206617:40\u2069`, `\u206612 / 286\u2069`.

### B. Type Scale (Arabic-Friendly Generous Line Heights)

| Role | Font Family | Weight | Size | Line Height | Usage |
|---|---|---|---|---|---|
| `wordmark` | Amiri | Bold | `34sp` | `44sp` | Home brand mark (compact, never oversized) |
| `surahDisplay` | Amiri | Bold | `30sp` | `44sp` | Mushaf Surah header, Now Playing medallion |
| `prayerCountdown` | UI Sans (`tnum`) | SemiBold | `36sp` | `42sp` | Next Prayer live countdown |
| `headlineMedium` | UI Sans | SemiBold | `24sp` | `34sp` | Screen titles (large editorial headers) |
| `titleLarge` | UI Sans | SemiBold | `20sp` | `28sp` | TopAppBar titles, Sheet titles |
| `titleMedium` | UI Sans | SemiBold | `16sp` | `24sp` | Card titles, Surah list names, Setting row titles |
| `titleSmall` | UI Sans | Medium | `14sp` | `20sp` | Section labels, Subheaders, Chip labels |
| `bodyLarge` | UI Sans | Normal | `16sp` | `25sp` | Primary body text, Descriptions |
| `bodyMedium` | UI Sans | Normal | `14sp` | `22sp` | Secondary prose, Permission explanations |
| `bodySmall` | UI Sans | Normal | `12.5sp` | `19sp` | Attribution notes, timestamps (never below 12sp!) |
| `labelLarge` | UI Sans | Medium | `14sp` | `20sp` | Buttons, prominent pills |
| `labelMedium` | UI Sans | Medium | `12sp` | `16sp` | Bottom bar labels, status badges |
| `mushafBody` | Amiri | Normal | `23sp` (Mushaf) / `scale * 26sp` (Continuous) | `1.95em` | Verbatim Tanzil Uthmani text |

---

## 3. Spacing, Layout Grid & Corner Radii

### Spacing Scale (`AyahSpacing` — strict `4dp` base grid)
- `xxs = 2.dp` (hairline nudge)
- `xs = 4.dp` (tight icon-to-label gap)
- `s = 8.dp` (chip gap, compact vertical rhythm)
- `sm = 12.dp` (list row vertical padding, dense card internal gap)
- `m = 16.dp` (standard screen horizontal margin, card internal padding, section spacing)
- `ml = 20.dp` (generous hero card padding, sheet horizontal margin)
- `l = 24.dp` (major section separation)
- `xl = 32.dp` (empty state padding, modal top/bottom breathing room)
- `xxl = 48.dp` (minimum interactive touch target size)

### Corner Radii (`AyahRadii`)
- `xs = 8.dp` — small thumbnail frames, inline status tags
- `s = 12.dp` — search inputs, compact buttons, filter chips, prayer active row pill
- `m = 16.dp` — standard grouped setting cards, recording rows, inline banners
- `l = 22.dp` — hero cards (Continue Reading, Next Prayer Sanctuary), docked Mini Player
- `xl = 28.dp` — Modal Bottom Sheets, Dialogs
- `pill = 100.dp` — Primary CTA buttons, segmented pills, floating reader action bar

---

## 4. Elevation & Surface Philosophy (Tonal-First, Zero Shadow Clutter)

Ayah replaces `ElevatedCard(elevation = 2.dp)` shadow-stacking with calm **Tonal Layering + Hairline Borders**:
- **Level 0 (Canvas)**: `background` (`#F6F2E9` / `#0C1210`) — base screen.
- **Level 1 (Standard Card / Grouped Container)**: `surface` (`#FCF9F2` / `#131B17`) with `1.dp` border in `outlineVariant`, `0.dp` drop shadow.
- **Level 2 (Interactive Floating Chrome — Docked Mini Player, Reader Ayah Pill)**: `surfaceContainerHigh` with `1.dp` `outlineVariant` border and subtle `3.dp` ambient elevation.
- **Level 3 (Sanctuary Hero Card)**: `heroSurface` (`#0F3D32` / `#122D25`) with subtle inner gold `1.dp` stroke at `18%` alpha — creates natural focal dominance without drop-shadow noise.
- **Level 4 (Modal Bottom Sheet / Dialog)**: `surfaceContainerLowest` (Light) / `surfaceContainerHigh` (Dark), `28.dp` top corners, standard M3 drag handle (`32dp x 4dp`, `outlineVariant`).

---

## 5. Iconography & Semantic Consistency Rules

1. **Style**: Rounded/Outlined Material Symbols (`Icons.Outlined.*` for inactive states, `Icons.Filled.*` for active/selected states). Stroke visual weight stays uniform (`24dp` standard, `20dp` inline/compact, `18dp` badges).
2. **Strict One-Icon-One-Meaning Contract** (eliminating current icon collisions):
   - **Font Size**: `Icons.Outlined.FormatSize` (`Aa` / `TT`) — ONLY for text size.
   - **Mushaf Page Mode**: `Icons.Outlined.AutoStories` (open book) — ONLY for 604-page Mushaf mode.
   - **Continuous Scroll Mode**: `Icons.Outlined.ViewAgenda` or `Icons.Outlined.Article` — ONLY for Continuous reading mode (NEVER `TextFields`).
   - **Auto-Scroll / Follow Recitation**: `Icons.Outlined.Sync` or `Icons.Outlined.VerticalAlignCenter` with text label — NEVER `ScreenRotation`.
   - **Unverified / Pending Permission**: `Icons.Outlined.ErrorOutline` or `Icons.Outlined.Schedule` — NEVER `RadioButtonUnchecked`.
3. **RTL Directionality Rules**:
   - Standard navigation (`ArrowBack`, `ChevronRight`) MUST use `Icons.AutoMirrored.*`.
   - **Mushaf Page Arrows Exception**: Earlier pages lie to the RIGHT in RTL Arabic and LEFT in LTR English; keep explicit RTL-aware icon selection (and align `ReaderScreen` prev/next surah arrows to match Mushaf direction!).
   - **Media Transport (`SkipPrevious`/`SkipNext`)**: Force LTR layout direction on the 3-button media transport bar (standard Android/iOS media ergonomics) OR pair with explicit Arabic accessibility labels (`الآية السابقة` / `الآية التالية`) so spatial intuition matches the progress scrubber.

---

## 6. Core Component Specifications

| Component | Height / Padding | Visual Spec | Behavior & States |
|---|---|---|---|
| **Primary CTA Button** | Min `50dp` height, `pill` radius | `primary` fill, `onPrimary` labelLarge text, optional `18dp` leading icon | Disabled = 38% alpha; Loading = inline `18dp` spinner + working text |
| **Secondary / Tonal Button** | Min `48dp` height, `pill` radius | `primaryContainer` or `surfaceVariant` fill, `0dp` border | Used for secondary actions (e.g., Browse Surahs, Pick City) |
| **Outlined Button** | Min `48dp` height, `pill` radius | `1dp` `outline`, transparent fill | Used for utility/diagnostic actions |
| **Search Pill (`AyahSearchBar`)** | `48dp` height, `pill` radius | `surfaceContainer` fill, `1dp` `outlineVariant`, leading search icon, trailing Clear (`X`) icon when non-empty | `ImeAction.Search`, clears query in 1 tap with explicit CD |
| **Status Badge (`AyahStatusBadge`)** | `24dp` min height, `pill` radius | Always pairs `14dp` icon + concise text (`statusSuccess`, `statusWarning`, `error`) | Never communicates state by color alone |
| **Setting Row (`AyahSettingRow`)** | Min `56dp` height, `16dp` H-padding | Leading `22dp` icon in soft `36dp` tonal circle + Title + Subtitle/Current Value + Trailing Chevron or Switch | Single-tap target across full width; hairline divider inset `64dp` |
| **Modal Bottom Sheet** | `28dp` top radius, drag handle | `surfaceContainerLowest` (L) / `surfaceContainerHigh` (D), `20dp` padding, title + optional close button | Replaces stacked 10-chip vertical walls (Calculation method, Reader settings, Page jump, Per-prayer Adhan) |
| **Progress Indicators** | `4dp`–`6dp` height, `pill` caps | Track = `surfaceContainerHighest`, Fill = `primary` (or `heroAccent` on Hero cards) | Indeterminate only during unknown-length network checks; determinate everywhere else (`ayah/total`, `downloaded/total`, `update %`) |
| **Docked Mini Player** | `64dp` height, `16dp` radius, `8dp` H-margin | Floats `8dp` above Bottom Navigation Bar (`surfaceContainerHigh` + `1dp` `outlineVariant` + `3dp` elevation) + `2dp` top ayah progress line | Shows Surah, Reciter, Ayah counter (or gold restore badge / red Adhan-blocked notice) + `48dp` Play/Pause button. Tap opens Now Playing. |

---

## 7. Accessibility & Localization Non-Negotiables

1. **Minimum Touch Target**: Every clickable control >= `48dp x 48dp` (`minimumInteractiveComponentSize()`).
2. **Contrast Floors**: Normal text >= `4.5:1` (target `>= 7:1` for primary reading text); large text & UI icons >= `3:1`.
3. **Font Scaling Resilience (`1.0x` to `2.0x`)**:
   - No fixed-height containers clipping text.
   - Chip groups MUST use `LazyRow` or `FlowRow` — NEVER equal-weight cramped `Row(Modifier.weight(1f))` with multi-word labels.
   - Bottom Navigation labels limited to 1 short word (`maxLines = 1`, `softWrap = false`).
4. **Never Color Alone**: Every status (permission granted/denied, wallpaper applied/failed, audio downloaded/downloading/failed, active prayer, blocked by adhan) combines icon + explicit localized text.
5. **Semantics & `testTag` Architecture**:
   - Dynamic Play/Pause controls MUST switch `contentDescription` accurately between Play and Pause strings.
   - Every key redesigned control adds a stable `Modifier.testTag(...)` alongside localized `contentDescription` so automated UI tests remain rock-solid across copy refinements.
6. **Arabic Copy Hygiene**:
   - Proper `<plurals>` for ayahs (`آية`/`آيتان`/`آيات`), minutes (`دقيقة`/`دقيقتان`/`دقائق`), hours (`ساعة`/`ساعتان`/`ساعات`).
   - Uniform numeral formatting per locale, with `\u2066...\u2069` BiDi isolation on compound numbers (`surah:ayah`, `n / total`, semver, page ranges).
   - Zero developer jargon (`WorkManager`, phase names) or inaccurate honorifics in user-facing copy.
