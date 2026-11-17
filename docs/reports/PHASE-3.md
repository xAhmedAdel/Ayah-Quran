# Ayah — Phase 3 Report (Premium Wallpaper Engine)

Date: 2026-11-17 · Status: **complete — rendered, applied to Home + Lock,
scheduled, and verified end-to-end on the emulator**

## 1. What was implemented

### WallpaperRenderer (`core/wallpaper/WallpaperRenderer.kt`)
- Bitmap/Canvas rendering at ARGB_8888; canvas clamped to ≤ 3840 px per side.
- Authentic verse text comes **only** from the Room database (Phase 2
  verified dataset); the renderer never transforms Quranic strings.
- RTL-aware layout via `StaticLayout` + `TextDirectionHeuristics.RTL` with
  the bundled Amiri typeface; full diacritic/Quranic-mark rendering (platform
  HarfBuzz shaping).
- **Fit-to-content shrink pass**: text size iteratively reduces until the
  verse + reference label fit the safe content area — Quranic text is never
  ellipsized or clipped (verified from Al-Fatiha 1:1 to Al-Baqarah 2:282,
  the longest verse).
- Safe content area per template (clock zone, gesture area, punch-hole/cutout
  margins) with Top/Center/Bottom user positioning.
- Screen-dimension-aware output (see §4, fix #2).

### Five premium templates (structured data, `WallpaperTemplate.kt`)
Emerald Luxury · Minimal Cream · Midnight Gold · Islamic Architecture ·
Geometric Art — each with independent palette (ARGB), background
(gradient/solid), decorations (frame / rules / radial glow / stars / star
lattice / pointed arch), base typography scale, line spacing, and safe-area
insets. All five render and were visually verified (screenshots).

### AyahSelector (`core/quran/AyahSelector.kt`, pure)
Random (with recent-verse avoidance), Sequential (persistent cursor, wraps),
Daily (deterministic per epoch day), fixed-Surah pool, fixed-Ayah. Avoidance
uses a persistent `selection_history` Room table trimmed to 100 rows; when
the whole pool is "recent", avoidance resets so selection never deadlocks.
Selection pools come only from the verified dataset — fabricated or missing
references are impossible (missing verse → honest FAILED, instrumented
test `missingVerseIsNeverFabricated`).

### Preview screen (`ui/wallpapers/`)
Live preview rendered by the **same renderer** as the final wallpaper (720×
1520 preview resolution), template chips, shuffle/specific-verse selection
(surah dropdown + ayah number with validation), font-size slider (0.6–1.6×),
vertical position control, destination selection, Apply button with honest
success/failure feedback, automation section, and history.

### WallpaperManager integration (`data/wallpapers/WallpaperRepositoryImpl`)
- `FLAG_SYSTEM`, `FLAG_LOCK`, or both — nothing reports success before
  `WallpaperManager.setBitmap` accepted **every** requested flag; failures
  recorded with the exception reason.
- Bitmap lifecycle: rendered bitmap compressed → recycled; setBitmap consumed
  the bitmap directly (screen-sized, bounded memory).
- Success/failure recorded in `wallpaper_history` with ayah reference,
  template, destinations, timestamp, status, and failure reason.

### WorkManager scheduling (`background/WallpaperWorker.kt` + scheduler)
- Unique periodic work (`ExistingPeriodicWorkPolicy.UPDATE` — intervals
  change without duplicating; scheduling survives restarts by design).
- Intervals: 30/60/180/360/720/1440 min (default 60). Battery-friendly
  `Constraints.setRequiresBatteryNotLow(true)`. No network constraints
  (fully offline). No foreground service.
- Pause = cancel unique work; resume = re-enable. `@HiltWorker` with
  on-demand WorkManager initialization (HiltWorkerFactory).
- Honest UI status: "Every N min (approximate)" + the documented caveat that
  WorkManager timing is inexact.

### Wallpaper history
`wallpaper_history` table (Room schema **v3**) + history list UI with
template · ayah · time · destination · status per row and Reapply per entry
(re-applies through the same confirmed path).

## 2. Verification (all executed, none assumed)

| Check | Result |
|---|---|
| `gradlew assembleDebug` | ✅ 68 MB APK (incl. dataset assets) |
| `gradlew assembleRelease` | ✅ 4.05 MB APK (R8), unsigned |
| `gradlew testDebugUnitTest` | ✅ **35 tests, 0 failures** (7 AyahSelector, 6 template contract, 22 Phase 1–2) |
| `gradlew connectedDebugAndroidTest` (ayah_avd, API 36) | ✅ **26 tests, 0 failures** — 8 Quran engine + 7 renderer (dimensions, 5-template render, shrink-fit, Arabic-pixel presence, aspect ratios, invalid-dims rejection) + 4 apply (SYSTEM/Lock wallpaper-id change, history recording, avoidance persistence, missing-verse honesty) + 1 real automatic cycle + 6 scheduling lifecycle |
| Emulator walkthrough | ✅ see below |

### Emulator walkthrough (ayah_avd, API 36, fresh install)
1. Import completed on first launch (Phase 2 behavior).
2. Wallpapers tab: live preview (Emerald Luxury, verse 17:38 — the
   deterministic daily selection), template chips, controls
   (`phase3-wallpapers-preview.png`).
3. Template switched to Minimal Cream → preview re-rendered in the new
   design (`phase3-template-cream-preview.png`); same for Midnight Gold
   (`phase3-template-gold.png`), Islamic Architecture (pointed-arch motif,
   `phase3-template-arch.png`), Geometric Art (star lattice,
   `phase3-template-geometric.png`).
4. **Apply now** (Both): logcat shows `wallpaper applied to FLAG_SYSTEM
   (17:38)` and `wallpaper applied to FLAG_LOCK (17:38)`; success banner in
   UI; history row recorded (`phase3-apply-success.png`).
5. **Home screen shows the wallpaper** — full verse centered with diacritics
   behind the launcher icons (`phase3-home-wallpaper.png`).
6. **Lock screen**: FLAG_LOCK applied (log + instrumented wallpaper-id
   assertion). Screenshot `phase3-lock-wallpaper.png` is black — headless
   emulator limitation (no lock-screen surface is rendered for screencap);
   functional verification is via the system wallpaper-id change.
7. **Automation enabled**: toggle ON → status "Every 60 min (approximate)"
   (`phase3-automation-enabled.png`); a wallpaper cycle (Random → 8:25,
   Emerald Luxury, Both) executed and recorded SUCCESS on-device shortly
   after enabling — visible in History.
8. **Persistence**: force-stop → relaunch → automation still ON with
   "Every 60 min", history intact (`phase3-schedule-persisted.png`).
9. **Reapply** exercised from History (8:25 entry) — recorded SUCCESS.
10. History + Reapply UI: `phase3-automation-enabled.png` (bottom half).

Screenshots: `docs/reports/screenshots/phase3-*.png`.

## 3. Bugs found and fixed during verification

1. **RTL double-offset (real rendering bug):** `TextPaint.textAlign =
   Paint.Align.CENTER` combined with StaticLayout's own RTL centering
   shifted every Arabic line left by ~½–1× its width, clipping text at the
   bitmap's left edge. Detected by the instrumented pixel-presence +
   size-comparison tests, root-caused with a line-geometry probe
   (`getLineLeft/Right` proved the layout was correct; drawing wasn't).
   Fix: keep paint alignment at default LEFT; StaticLayout centers itself.
   This bug would have made every wallpaper unreadable — the tests caught it
   before release.
2. **Wallpaper over-crop:** rendering at `WallpaperManager.desiredMinimum*`
   (the scrollable canvas, ~2× width) caused the launcher to crop the
   centered verse. Fix: render at screen dimensions.
3. **Kotlin `apply`-scope shadowing:** `textSize = referenceTextSize` inside
   `referencePaint.apply {}` assigned the enclosing local `var textSize`
   (locals shadow receiver members), breaking `usedTextSizePx` reporting.
   Fix: explicit `this.textSize`. (Also fixed a template `lineSpacing`
   multiplier that was never applied, and Amiri-tall-metrics spacing values.)

## 4. Known limitations / notes

1. Lock-screen visual confirmation is limited by the headless emulator (no
   lock-screen surface for screencap); FLAG_LOCK application is verified by
   system wallpaper-id change + logs. Physical-device confirmation belongs
   to the Phase 6 compatibility pass.
2. WorkManager periodic execution is inexact (documented in the UI); the
   observed on-device cycle ran shortly after enabling (emulator scheduling).
3. Selection-mode chips row can overflow horizontally on narrow screens
   (AYAH chip requires horizontal scroll); a visual-polish item for Phase 4.
4. Release APK unsigned (Phase 6). Emulator GPU is software-rendered;
   hardware-device typography check in Phase 6.
5. History is unbounded (rows accumulate; selection ring is trimmed to 100).
   A cap/pruning policy is a Phase 4 candidate.

## 5. Next phase (Phase 4 — Premium UI)

Home screen live wallpaper status + daily ayah, wallpaper preview
full-screen mode, animations/transitions, selection-mode chip layout fix,
history pruning, empty-state polish, accessibility pass.
