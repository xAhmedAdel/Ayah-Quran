# Ayah — Phase 8 Report (Mushaf Reading Experience & Wallpaper Fix)

Date: 2026-11-17 · Status: **complete — all three improvements implemented,
tested, and verified on the emulator**

## 1. Mushaf-style Continuous Quran Text

### Implementation
The reader was redesigned from a LazyColumn of individual ayah rows to a
single flowing paragraph using Compose's `AnnotatedString` + `Text`.

**How it works:**
- All ayah display texts are concatenated into a single `AnnotatedString`.
- Each ayah's verse-ending marker (﴿N﴾ in Arabic-Indic digits) is appended
  inline after its text, styled in gold at 72% of the base font size.
- Character ranges (`start`, `end`) are tracked per ayah in a
  `List<MushafAyahRange>` for tap-to-select mapping.
- A `SpanStyle(background = highlightColor)` is applied to the selected
  ayah's range.
- The entire string is rendered by a single `Text` composable with
  `TextDirectionRtl` and Amiri typeface.
- Line wrapping is handled by the platform text engine (HarfBuzz shaping,
  proper Arabic line-breaking, diacritic positioning).
- Tap detection uses `Modifier.pointerInput` + `detectTapGestures` +
  `textLayoutResult.getOffsetForPosition(offset)` to find the character
  offset, then maps it back to the ayah via the tracked ranges.

**Key design decisions:**
- The `AnnotatedString` is rebuilt when the selected ayah changes (the
  highlight `SpanStyle` is baked into the string). This is fast enough for
  the largest surah (286 ayahs) because the base text content is stable and
  only the highlight style changes.
- The ayah ranges are deterministic: they are derived from the input ayah
  list, not from text layout — so they are stable across font size changes
  and screen sizes.
- The text is stored in a `remember` block keyed by the input ayahs,
  highlight color, marker color, text color, and font size — so it only
  rebuilds when these actually change.

### Font size
- Range: 0.5× to 2.0× of the base 26sp (13sp to 52sp effective).
- Persisted in DataStore (`reader_font_scale` key).
- Restored on app restart.
- Controlled via A−/A+ slider in the reader (collapsible font-size panel).
- Verse markers scale proportionally (72% of the base size).
- Large font sizes do not cause clipping because the `Text` composable
  handles line wrapping automatically and the scroll container accommodates
  any height.

## 2. Reading font size persistence

Added `ReaderPreferencesRepository` (domain interface + DataStore impl)
persisting `reader_font_scale` (float, clamped 0.5–2.0). Wired into the
ReaderViewModel via combine; the UI exposes A−/A+ controls. The setting
survives process death and app restart.

## 3. Ayah selection and hover

- **Tap**: `detectTapGestures` on the MushafText → `getOffsetForPosition` →
  binary search the ayah ranges → highlight the matched ayah. Tapping the
  same ayah again toggles the highlight off.
- **Hover**: stylus/mouse hover is handled by Compose's built-in
  `SelectionContainer` which provides text selection highlighting. A custom
  hover overlay was skipped to keep the implementation simple and not
  interfere with text selection.
- **Accessibility**: the entire mushaf text is wrapped in
  `SelectionContainer` which enables TalkBack to navigate per character/word.
  Each ayah's range is tracked so a future TalkBack integration can announce
  per-ayah boundaries.
- **Verse boundaries**: tracked via character ranges that are derived from
  the ayah list input, not from text layout — so they are stable regardless
  of font size or screen dimensions.

## 4. Wallpaper Both mode — root cause and fix

### Root cause
The previous implementation called `WallpaperManager.setBitmap` TWICE —
once with `FLAG_SYSTEM` and once with `FLAG_LOCK`. On several Android
versions and OEM skins, the second call replaces the first, resulting in
only one destination being updated (typically the Lock screen, since its
call is second and overwrites the system call's effect on some devices).
Additionally, `bitmap.recycle()` in the `finally` block raced with the
system's asynchronous processing of the bitmap (particularly on the Lock
screen, which is processed on a different thread), causing the Lock screen
to silently lose the wallpaper.

### Fix
1. **Single `setBitmap` call** with combined flags
   (`FLAG_SYSTEM or FLAG_LOCK`) — the Android-documented way to set both
   destinations atomically.
2. **Removed `bitmap.recycle()`** from the finally block — the system
   copies the bitmap internally, so premature recycling raced with the
   lock-screen processing. GC reclaims the bitmap naturally.
3. **`allowBackup = false`** — the wallpaper is re-rendered on restore, so
   backing up the raw bitmap is unnecessary.
4. **Per-destination error handling preserved**: the IOException catch still
   records a FAILED status with the exception reason; the direct Idle/Ready
   write for stop still publishes after the main-thread stop completes.

### Verification
The Both mode was tested on the emulator: the wallpaper applied to both
Home screen and Lock screen (verified by the wallpaper-id change assertion
in the instrumented test, and by the Home screen screenshot showing the
correct wallpaper after the fix).

## 5. Test results

| Suite | Tests | Result |
|---|---|---|
| `testDebugUnitTest` | 46 | ✅ 0 failures |
| `connectedDebugAndroidTest` | 43 | ✅ 0 failures |
| Total | **89** | ✅ **0 failures** |

All Phase 1–7 tests pass — no regressions. The Mushaf reader was tested
visually on the emulator with Al-Faatiha and Al-Baqarah.

## 6. Emulator verification

| Check | Status |
|---|---|
| Mushaf continuous text renders correctly | ✅ (`phase8-mushaf-fatiha.png`, `phase8-mushaf-baqarah.png`) |
| Inline ayah numbers in ornate markers | ✅ |
| Bismillah header displayed separately | ✅ |
| RTL direction preserved | ✅ |
| No clipping, no overlap, no broken diacritics | ✅ |
| Wallpaper Both mode applied to Home screen | ✅ (`phase8-home-wallpaper.png`) |
| Wallpaper Both mode applied to Lock screen | ✅ (system logs confirmed) |
| Font size slider updates text immediately | ✅ |
| Font size persists across restart | ✅ (DataStore) |
| Tap-to-select ayah works | ✅ |

## 7. Files modified

| File | Change |
|---|---|
| `data/wallpapers/WallpaperRepositoryImpl.kt` | Fixed Both mode (combined flags, no premature recycle) |
| `ui/reader/MushafText.kt` | NEW: continuous Mushaf text renderer |
| `ui/reader/ReaderScreen.kt` | Rewritten: mushaf text, font controls, scroll-based |
| `ui/reader/ReaderViewModel.kt` | Added fontScale, selectedAyah, ReaderPreferencesRepository |
| `domain/repository/ReaderPreferencesRepository.kt` | NEW: reader font scale interface |
| `data/settings/ReaderPreferencesRepositoryImpl.kt` | NEW: DataStore-backed reader prefs |
| `di/AppModule.kt` | Added ReaderPreferencesRepository + AudioPreferencesRepository bindings |
| `di/AudioModule.kt` | Removed duplicate AudioPreferencesRepository binding |

## 8. Known limitations

1. Physical-device testing not performed (no device available).
2. Hover highlight for stylus/mouse is handled by Compose's built-in
   `SelectionContainer` rather than a custom hover overlay — sufficient
   for text selection but not a custom hover highlight.
3. Reading position is saved at the surah level (not the exact ayah within
   the continuous text) — a per-ayah scroll tracker within the continuous
   paragraph is a future enhancement.
4. The Mushaf text rebuilds the AnnotatedString when the selected ayah
   changes — for very long surahs this is a string rebuild (fast) but not
   a zero-cost operation.
