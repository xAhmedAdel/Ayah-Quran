# Wallpaper Engine — Special Documentation

## Purpose

Generates Quranic wallpapers (ayah text over rendered templates) and
applies them to the system wallpaper (home/lock/both), with optional
rotation.

## Files

| File | Role |
|---|---|
| `core/wallpaper/WallpaperRenderer.kt` | bitmap rendering of the ayah over the template (fonts, RTL text layout) |
| `core/wallpaper/WallpaperTemplate.kt` | template definitions (backgrounds/compositions) |
| `ui/wallpapers/WallpapersScreen.kt` | tab UI: template gallery, ayah selection, apply actions |
| `ui/wallpapers/WallpapersViewModel.kt` | gallery + selection + apply state |
| `ui/wallpapers/FullscreenPreviewScreen.kt` (+VM) | full-screen preview route `preview/{surahId}/{ayahNumber}` |
| `ui/wallpapers/WallpaperTemplateLabels.kt` | localized template labels |
| WorkManager worker (wallpaper rotation) | periodic rotation per settings (see `di/` + manifest `androidx.startup` config; WorkManager initialized on demand with Hilt factory) |

## Behaviors (must keep working)

- Apply wallpaper to home / lock / both via `WallpaperManager` (SET_WALLPAPER
  permission; both-mode uses combined flags — see
  `wallpaper-media3-gotchas` knowledge: combined flags + bitmap recycle
  race).
- Rotation modes incl. random/sequential with WorkManager periodic work;
  fixed-ayah option.
- Templates render Arabic ayah text with the Quran font (Amiri) and RTL
  layout inside `WallpaperRenderer`.
- Reading integration: wallpapers tab can pick ayah by surah/ayah numbers
  (route `preview/{surahId}/{ayahNumber}`).

## Redesign guidance

Template visuals, gallery layout, preview chrome = free to redesign.
`WallpaperRenderer`'s text layout/RTL correctness, WallpaperManager flag
handling, bitmap recycle ordering, and the WorkManager rotation
scheduling are load-bearing — restyle screens, keep the engine calls.
