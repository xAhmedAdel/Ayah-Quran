# AYAH — UI-8 Report: Wallpapers Studio & Fullscreen Preview

## 1. Scope Completed

Phase UI-8 transforms the Wallpapers experience (`WallpapersScreen`, `FullscreenPreviewScreen`, `FullscreenPreviewViewModel`) into the **Al-Mushaf Al-Hadith** Wallpapers Studio while preserving the production `WallpaperRenderer`, `WallpaperTemplate.ALL` data model, `WallpaperManager` application pipeline, WorkManager scheduling, and Room history persistence.

### Wallpapers Studio (`WallpapersScreen`)
- **Live TopAppBar Auto-Rotation Badge**: Displays live scheduling status (`Auto: <interval>` in emerald / `Auto: Off` in neutral) via `AyahStatusBadge`.
- **Sticky Clipped Preview Stage**: Compact `9:16` phone-frame live preview (`clip(RoundedCornerShape(20.dp))` with crisp bezel border — fixing the unclipped rectangular overflow), paired directly with Shuffle and Fullscreen Preview action buttons and the live verse reference label.
- **Visual Template Swatches**: Replaced plain text `FilterChip`s with 5 visual swatch cards rendered using each template's real `WallpaperTemplate.background` gradient/solid color, `accentColor` frame border, Amiri sample glyph in `textColor`, and gold selection indicator while keeping all 5 localized template labels immediately composed.
- **Typography & Verse Card**: Added live percentage font-scale readout (BiDi-isolated tabular numbers), preserved 3-way vertical position segmented control, replaced deprecated `ExposedDropdownMenuBox.menuAnchor()` with a clean `AyahSelectionSheet` Surah picker, pre-filled the numeric Ayah input with `fixedAyahNumber` and numeric keyboard validation, and kept the 2x2 selection mode chips.
- **Automation Card**: Consolidated the 6 rotation intervals (`30m`, `60m`, `180m`, `360m`, `12h`, `24h`) into a clean wrapping `FlowRow`, replaced the static top-level `DateTimeFormatter` with a dynamic locale-aware `rememberLocalizedTimeFormatter()`, and kept plain-language scheduling copy.
- **History Card**: Clipped `8dp` thumbnails, dynamic locale timestamps, Reapply + Delete icon, and `ConfirmDialog` Clear History.
- **Pinned Bottom Apply Bar**: Always-accessible 3-way destination segmented control (`Home` / `Lock` / `Both`), full-width `AyahPrimaryButton` with loading state, and animated icon + text outcome feedback.

### Fullscreen Preview (`FullscreenPreviewScreen` + `FullscreenPreviewViewModel`)
- **High-Contrast Floating Close Button**: Circular dark-scrim (`56%` black + white icon) button guarantees legibility over all 5 light and dark templates, including Minimal Cream.
- **Interactive Destination Selector & Accurate Outcome Copy**: Added `destinations` state and `setDestinations(...)` to `FullscreenPreviewViewModel` so users can choose Home / Lock / Both directly inside Fullscreen Preview, fixing the hardcoded `wallpapers_apply_both` success message bug.

## 2. Files Changed

- `app/src/main/java/com/ayah/quran/ui/wallpapers/WallpapersScreen.kt`
- `app/src/main/java/com/ayah/quran/ui/wallpapers/FullscreenPreviewScreen.kt`
- `app/src/main/java/com/ayah/quran/ui/wallpapers/FullscreenPreviewViewModel.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-ar/strings.xml`

## 3. Verification

- `:app:assembleDebug` — PASS
- `:app:testDebugUnitTest` — **163/163 passed**
- `:app:connectedDebugAndroidTest` (`AppUiTest` on `ayah_avd`, API 36) — **PASS**

## 4. Visual Verification Screenshots (`docs/UI-UX-REDESIGN/screenshots/ui-8/`)

1. `01_wallpapers_studio_ar_light.png` — Wallpapers Studio AR Light (sticky clipped preview stage, visual template swatches, pinned Apply bar).
2. `02_wallpapers_scrolled_ar_light.png` — Scrolled Verse, Automation, and History cards.
3. `03_wallpapers_fullscreen_ar_light.png` — Immersive Fullscreen Preview with high-contrast scrim Close button and frosted floating destination + Apply bar.
4. `04_wallpapers_studio_ar_dark.png` — Wallpapers Studio AR Dark (Obsidian Moss dark mode).
