# 10 — Design Decisions Log (ADRs)

Record of every significant product, UX, visual, and architectural UI decision made in Phase UI-0, along with alternatives considered and tradeoffs accepted.

---

### ADR-01: Flagship Visual Direction — "Al-Mushaf Al-Hadith" (Synthesized Direction D)
- **Context**: Ayah's current UI feels generic, boxy, and disconnected despite having a solid emerald/gold/cream brand palette and 5 attractive wallpaper templates.
- **Alternatives Considered**: (A) Pure Quiet Luxury; (B) Pure Modern Editorial; (C) Pure Contemporary Minimal; (D) Synthesized Sanctuary.
- **Decision**: Adopt **Direction D ("Al-Mushaf Al-Hadith")** — Emerald + Antique Gold + Warm Parchment (Light) / Obsidian Moss (Dark), tonal matte cards with `1dp` hairline borders instead of drop shadows, dual Amiri + crisp sans-serif typography, and Contemporary Minimal interaction ergonomics.
- **Rationale**: Preserves brand continuity with Ayah's existing identity and wallpaper templates while achieving world-class usability for beginners, older users, and extended reading sessions.

---

### ADR-02: Bottom Navigation — 5 Daily Pillars + Top-Bar Settings Hub (Replacing 6 Tabs)
- **Context**: Current 6-tab `NavigationBar` violates Material 3's 3–5 destination limit, squeezes labels on `360dp` screens, and dedicates 17% of prime bottom navigation to low-frequency Settings.
- **Alternatives Considered**: Keep 6 tabs; reduce to 4 tabs (moving Wallpapers into Home); reduce to **5 tabs (`Home · Quran · Prayer · Audio · Wallpapers`) + Settings icon in Home Header**.
- **Decision**: Recommend **5 Bottom Tabs + Home Header Settings Hub**.
- **Tradeoff / Impact**: Requires updating `TopLevelDestinationTest.kt` (JVM) and `AppUiTest.kt` (instrumented) in Slice UI-2. Explicitly flagged for Product Owner sign-off before coding.

---

### ADR-03: Mini-Player Placement — Docked Above Bottom Navigation Bar + Smart Exclusion
- **Context**: `MiniPlayerBar` currently sits ABOVE `NavHost` at the top of the screen, causing ~72dp double status-bar gaps on screens with `TopAppBar` and rendering redundantly on top of `NowPlayingScreen` and `MushafPageReaderScreen`. Additionally, hiding it on `AudioScreen` without making the inline bar tappable stranded `NowPlayingScreen`.
- **Decision**: Dock `AyahMiniPlayerBar` immediately **above the Bottom Navigation Bar** on all 5 tabs (including Audio!) and standard sub-screens; **suppress** it on `NowPlayingScreen`, `FullscreenPreviewScreen`, and Readers (where a sleek compact inline audio pill handles reader-mode playback without shifting the TopAppBar).
- **Rationale**: Eliminates the single most glaring visual bug across screenshots, matches universal thumb-zone media ergonomics, and guarantees 1-tap access to Now Playing from every tab.

---

### ADR-04: Quran Hub Unification (Surahs + Juz + Bookmarks + Quick Page Jump)
- **Context**: Bookmarks were isolated behind a button at the bottom of Home; Juz browsing did not exist; jumping to a page required opening a Surah first; Arabic Surah names rendered twice per row.
- **Decision**: Turn `QuranScreen` into a unified 3-segment **Quran Hub** (`السور · الأجزاء · المحفوظات`) with a top-bar `انتقال لصفحة` trigger, while keeping `Routes.BOOKMARKS` registered for backward-compatible Home shortcuts.
- **Rationale**: Matches how real Quran readers navigate (by Surah, by Juz, by Page, or by Bookmark) in one predictable place.

---

### ADR-05: Keep Reader Chrome Visible by Default + Floating Ayah Action Pill (Instead of Hidden Immersive HUD)
- **Context**: Many Quran apps hide top/bottom bars on single tap. However, Ayah's `MushafText` uses single-tap to select/highlight individual ayahs, `ZoomableMushafPage` uses double-tap to reset pinch-zoom, and `MushafPageReaderUiTest` asserts the page indicator and prev/next buttons are immediately visible on open and after gestures.
- **Decision**: Keep a **calm, minimalist 52dp Top Bar and 52dp Bottom Page Bar visible by default**, move secondary controls (Mode switch, Font scale, Auto-scroll) into a clean `ReaderSettingsSheet`, and surface a **Floating Ayah Action Pill** (`Play · Bookmark · Wallpaper`) immediately when an ayah is tapped.
- **Rationale**: Zero gesture collision with ayah tap or double-tap zoom reset, superior discoverability for older users, and 100% compatibility with `MushafPageReaderUiTest`.

---

### ADR-06: Protect `MushafText` Pipeline Completely
- **Context**: `MushafText.kt` handles verbatim Tanzil Uthmani rendering, verse markers, highlight spans, and character-offset tap mapping.
- **Decision**: Never alter `buildMushafString` concatenation or `findAyahAtOffset`. Only update `highlightColor` (`22%` gold wash) and `markerColor` tokens via `LocalAyahColors`.
- **Rationale**: Eliminates risk of breaking Uthmani diacritics, tap accuracy, or 604-page mapping integrity.

---

### ADR-07: Modal Bottom Sheets Over Stacked Vertical FilterChips (Prayer Settings & Now Playing)
- **Context**: `PrayerSettingsScreen` renders 10 Calculation Methods, 2 Asr Methods, 3 High-Lat rules, and 5x4 Adhan Mode chips inline — producing an overwhelming multi-screen scroll wall that clips at large font scales.
- **Decision**: Replace inline multi-option lists with compact single-row `AyahSettingRow`s showing the active selection value, opening clean `AyahSelectionSheet` modals on tap. Merge the 3 duplicated permission/battery sections into one unified Reliability & Diagnostics section.
- **Rationale**: Cuts vertical scroll length by ~65%, scales cleanly to `2.0x` font size, and makes every current setting scannable at a glance.

---

### ADR-08: Audio Tab `LazyColumn` Virtualization & Legacy Phase-5 Cleanup
- **Context**: `AudioScreen` composes all 114 Surah rows eagerly in a `Column` with `verticalScroll`, causing frame drops and high memory churn. Legacy Phase-5 `PlaybackControls`/`SeekBarRow` files remain dead code.
- **Decision**: Rebuild `AudioScreen` on a single `LazyColumn` with search + Downloaded filter, visual Reciter cards, linear download bars, and delete confirmation; retire dead Phase-5 UI components while keeping `ExoPlayerAyahPlaylistPlayer` and `QuranAudioDownloadRepository` flows strictly intact.
- **Rationale**: Fixes the #1 runtime performance issue in the UI layer and clarifies the offline audio workflow.

---

### ADR-09: Split Sticky-Canvas Wallpaper Studio
- **Context**: On `WallpapersScreen`, editing templates, font size, or alignment requires scrolling far below the preview card so users edit blind, and templates have no visual preview swatches.
- **Decision**: Pin a compact rounded phone-frame live preview in the upper ~40% of the screen, place a 4-tab studio panel (`Design · Verse · Auto · History`) with visual gradient/pattern swatches below it, and pin the Home/Lock/Both + Apply bar at the bottom.
- **Rationale**: Instant visual feedback on every tap or slider drag without altering `WallpaperRenderer` or WorkManager scheduling.

---

### ADR-10: Dual Font Strategy (Amiri + Optional IBM Plex Sans Arabic) & Stable `testTag` Discipline
- **Context**: Platform Roboto renders Arabic UI chrome with tight ascender/descender spacing and generic visual personality; meanwhile, all 4 Compose UI test suites have zero `testTag`s and break whenever Arabic/English copy improves.
- **Decision**:
  1. Pair bundled **Amiri** (Quran & calligraphic headers) with **IBM Plex Sans Arabic** (SIL OFL 1.1) for UI chrome — with a documented zero-asset fallback to tuned system sans if owner prefers minimal APK size.
  2. Add stable semantic `Modifier.testTag(...)` identifiers across all redesigned screens during Slices UI-2..UI-8 while preserving localized `contentDescription`s for TalkBack.
- **Rationale**: Elevates Arabic readability to flagship product quality and decouples automated regression tests from cosmetic copy improvements.
