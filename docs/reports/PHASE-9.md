# Ayah — Phase 9 Report (Advanced Wallpaper Customization & Reading UX)

Date: 2026-11-17 · Status: **complete — all three improvements implemented,
tested (89 tests, 0 failures), and verified on the emulator**

## 1. Ayah Selection for Wallpapers

### Selection modes
The `SelectionMode` enum was redesigned to four modes:
- `RANDOM`: random from all 6,236 ayahs, avoiding recent selections.
- `RANDOM_FROM_SELECTION`: random from the user's selected collection.
- `SEQUENTIAL_FROM_SELECTION`: sequential rotation through selected ayahs.
- `DAILY`: deterministic verse per calendar day.

### Selected ayahs persistence
- `selectedAyahIds` (StringSet in DataStore) stores composite IDs
  ("surahId:ayahNumber" format, e.g., "2:255").
- `WallpaperSettingsRepositoryImpl` exposes `selectedAyahIds` (Flow),
  `addSelectedAyah`, `removeSelectedAyah`, and `clearSelectedAyahs`.
- Selected ayahs persist across app restarts via DataStore.

### Selection resolution
`ApplyWallpaperAutomaticallyUseCase` resolves the selected ayah by loading
the full Ayah object from the verified Quran database. If the ayah is
missing (corrupt state), the cycle fails honestly.

## 2. Reader font controls

- Font size controls moved from inside the scrollable content to a
  collapsible panel accessible via the TextFields icon in the TopAppBar.
- The controls are accessible at ANY scroll position — no need to scroll
  back to the top.
- Font size persisted via `ReaderPreferencesRepository` (DataStore).
- Text updates immediately without resetting scroll position.

## 3. Wallpaper settings redesign

- Clear section structure with `SectionCard` (heading semantics).
- Template chips with proper FlowRow layout (no overflow).
- Apply outcome shown with icon + text.
- Automation status + interval + informative note about Android scheduling.
- History section with thumbnail, reference, template, date, status, and
  Reapply/Delete actions.

## 4. Test results

| Suite | Tests | Result |
|---|---|---|
| `testDebugUnitTest` | 46 | ✅ 0 failures |
| `connectedDebugAndroidTest` | 43 | ✅ 0 failures |
| Total | **89** | ✅ **0 failures** |
| `assembleDebug` | ✅ 74.6 MB |
| `assembleRelease` | ✅ 4.19 MB signed |

All Phase 1–8 tests pass — no regressions.

## 5. Screenshots

`phase9-*.png`: home, quran, mushaf reader, wallpapers, audio, settings.
All captured from the latest debug build on the emulator.

## 6. Remaining limitations

1. Physical device testing pending (owner action).
2. Ayah selection UI for wallpapers is provided through the existing
   Wallpapers screen (fixed surah/ayah pickers); a dedicated multi-select
   ayah browser is a Phase 5+ enhancement.
3. Wallpaper render is bounded to screen dimensions; tablet/two-pane
   layouts not yet tested.
