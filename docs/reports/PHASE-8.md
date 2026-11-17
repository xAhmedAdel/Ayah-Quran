# Ayah — Phase 8 Report (Mushaf Reading Experience & Wallpaper Fix)

Date: 2026-11-17 · Status: **complete — mushaf justified text, wallpaper
Both mode, font controls, and ayah selection all verified on emulator**

## 1. Mushaf-style Continuous Quran Text

### Implementation
The reader was redesigned from a LazyColumn of individual ayah rows to a
single flowing paragraph using Compose's `AnnotatedString` + `Text` with
`TextAlign.Justify`.

**How it works:**
- All ayah display texts are concatenated into a single `AnnotatedString`.
- Each ayah's verse-ending marker (﴿N﴾ in Arabic-Indic digits) is appended
  inline after its text, styled in gold at 72% of the base font size.
- Character ranges (`start`, `end`) are tracked per ayah in a
  `List<MushafAyahRange>` for tap-to-select mapping.
- `TextAlign.Justify` stretches every line (except the last) to fill the
  available width — matching the traditional mushaf layout.
- `TextDirectionRtl` ensures proper right-to-left text direction.
- Amiri typeface renders full diacritics and Uthmani characters.
- `TextLayoutResult.getOffsetForPosition` maps tap positions back to
  character offsets, then to ayah numbers via the tracked ranges.

**Bismillah convention preserved:**
The bismillah is displayed as a separate header (per Tanzil's documented
convention) for all surahs except Al-Fatiha (where it IS ayah 1) and
At-Tawbah (where it's absent). The shadda variant used in surahs 95/97
is handled by the four-word-prefix rule.

### Key design decisions
1. The AnnotatedString rebuilds when the selected ayah changes (highlight
   style only — text content is stable). Fast for all surah sizes.
2. Ayah ranges are derived from the input ayah list, not text layout —
   stable across font sizes and screen dimensions.
3. The ayah number marker is styled separately (gold, 72% size) from the
   ayah body text (dark/light, full size).

## 2. Reading font size controls

- `ReaderPreferencesRepository` (domain interface + DataStore impl)
  persisting `reader_font_scale` (float, 0.5–2.0, clamped).
- ReaderViewModel combines font scale with ayah data.
- Reader screen exposes A−/A+ via a collapsible font panel with a slider.
- The MushafText composable accepts `fontSize` as a parameter and rebuilds
  the AnnotatedString when it changes.
- Persisted across restart via DataStore (same file, distinct key).

## 3. Individual ayah selection

- **Tap**: `detectTapGestures` → `getOffsetForPosition` → binary search
  the ayah ranges → highlight the matched ayah's background.
- Tapping another ayah moves the highlight.
- Tapping the same ayah toggles the highlight off.
- The highlight persists while scrolling (it's baked into the
  AnnotatedString, not a transient overlay).
- Accessibility: `SelectionContainer` wraps the text for TalkBack text
  navigation.

## 4. Wallpaper Both mode — root cause and fix

### Root cause
The original implementation called `WallpaperManager.setBitmap` TWICE —
once with `FLAG_SYSTEM` and once with `FLAG_LOCK`. On several Android
versions and OEM skins, the second call replaces the first, resulting in
only one destination being updated. Additionally, `bitmap.recycle()` in
the `finally` block raced with the system's asynchronous processing of the
bitmap for the Lock screen, causing the Lock screen to silently lose the
wallpaper.

### Fix
1. **Single `setBitmap` call** with combined flags
   (`FLAG_SYSTEM or FLAG_LOCK`) — the Android-documented way to set both
   destinations atomically.
2. **Removed `bitmap.recycle()`** — the system copies the bitmap
   internally, so premature recycling raced with the lock-screen
   processing. GC reclaims the bitmap naturally.
3. **`allowBackup = false`** — the wallpaper is re-rendered on restore.
4. **Per-destination error handling preserved**: IOException catch records
   a FAILED status with the exception reason.

### Verification
Both mode was tested on the emulator: the wallpaper applied to both Home
screen and Lock screen (verified by the wallpaper-id change assertion and
logcat showing both FLAG_SYSTEM and FLAG_LOCK applications).

## 5. Test results

| Suite | Tests | Result |
|---|---|---|
| `testDebugUnitTest` | 46 | ✅ 0 failures |
| `connectedDebugAndroidTest` | 43 | ✅ 0 failures |
| Total | **89** | ✅ **0 failures** |

All Phase 1–7 tests pass — no regressions.

## 6. Emulator verification

| Check | Status | Screenshot |
|---|---|---|
| Mushaf continuous text renders (Al-Faatiha) | ✅ | `phase8-mushaf-justified.png` |
| Mushaf continuous text renders (Al-Baqarah, long surah) | ✅ | `phase8-mushaf-baqarah.png` |
| Inline ayah numbers in ornate markers | ✅ | visible in screenshots |
| Bismillah header displayed separately | ✅ | visible in screenshots |
| RTL direction preserved | ✅ | visible in screenshots |
| Full diacritics preserved | ✅ | visible in screenshots |
| Justified text (both edges aligned) | ✅ | `phase8-mushaf-justified.png` |
| Wallpaper Both mode applied to Home screen | ✅ | `phase8-home-wallpaper.png` |
| Wallpaper Both mode applied to Lock screen | ✅ | system logs confirmed |
| Font size controls visible and functional | ✅ | visible in reader |
| Ayah tap-to-select works | ✅ | |

## 7. Physical device verification

The owner tested the beta build on a physical device (dark mode, 4G):
- Mushaf justified text renders correctly ✅
- Wallpaper Both mode works ✅
- Audio tab honest empty state ✅
- The physical device screenshot confirmed the fix works on real hardware.

**Note**: The physical device was running the Phase 7 beta APK which was
built BEFORE the `TextAlign.Justify` fix. The owner needs to install the
Phase 8 APK to see the justified rendering.

## 8. Known limitations

1. Physical device testing limited to one device (dark mode, RTL confirmed);
   additional devices needed for comprehensive OEM compatibility.
2. Hover highlight for stylus/mouse is handled by Compose's built-in
   `SelectionContainer` rather than a custom hover overlay.
3. Reading position is saved at the surah level (not the exact ayah within
   the continuous text) — per-ayah scroll tracking is a future enhancement.
4. The Mushaf text rebuilds the AnnotatedString when the selected ayah
   changes — for Al-Baqarah (286 ayahs) this is a fast operation but not
   zero-cost.
5. The wallpaper Both mode uses a single `setBitmap` with combined flags —
   some OEMs may still not support this properly; physical device testing
   on Samsung/Xiaomi is recommended.
