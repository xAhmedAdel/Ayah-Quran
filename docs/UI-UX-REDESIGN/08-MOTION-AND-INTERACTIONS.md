# 08 — Motion, Transitions & Interaction Feedback

Motion design specification for Ayah. Motion in a Quran companion must feel **serene, weightless, and immediate** — like turning a fine paper page or settling into a quiet room. Never bouncy, never slow, never distracting.

---

## 1. Core Motion Principles

1. **Calm & Purposeful**: Every animation either preserves spatial orientation (sheets, navigation, page turns) or confirms an action (ayah tap, bookmark toggle, audio play/pause). Zero decorative idle animations.
2. **Fast & Responsive**: UI transitions complete within `150ms`–`280ms`; users must never wait on an animation to tap the next control.
3. **Strict Reduced-Motion Compliance**: Continue reading `rememberMotionEnabled()` (`Settings.Global.ANIMATOR_DURATION_SCALE > 0f`). When disabled, every `tween` / `spring` collapses to `0ms` / instant state swap without dropping visual state changes.
4. **Haptic Restraint**: Subtle tactile ticks only on meaningful physical thresholds (bookmark saved, ayah selected, wallpaper applied, prayer mode changed) using `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.TextHandleMove / LongPress)` — never on list scrolls or basic button taps.

---

## 2. Motion Tokens (`core/designsystem/motion/AyahMotion.kt`)

| Token | Duration / Physics | Easing | Usage |
|---|---|---|---|
| `MotionDurations.MICRO` | `120ms` | `FastOutSlowInEasing` | Icon tint shifts, chip selection fill, ayah highlight fade-in |
| `MotionDurations.FAST` | `180ms` | `CubicBezierEasing(0.2f, 0f, 0f, 1f)` (Emphasized Decelerate) | Tab cross-fade, mini-player enter/exit, contextual pill reveal |
| `MotionDurations.MEDIUM` | `240ms` | `CubicBezierEasing(0.2f, 0f, 0f, 1f)` | Push/pop screen transitions, accordion expand/collapse |
| `MotionDurations.SLOW` | `300ms` | `CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)` | Modal bottom sheets, Fullscreen Wallpaper preview reveal |
| `MotionSprings.GENTLE` | `dampingRatio = 0.88f, stiffness = 420f` | Critically-damped spring (zero overshoot) | Segmented indicator slide, docked Mini Player appearance |

---

## 3. Screen & Surface Transitions

| Transition | Enter | Exit | Reduced Motion |
|---|---|---|---|
| **Top-Level Tab Switch** | `fadeIn(180ms)` — NO vertical slide (avoids janky jump between sibling tabs) | `fadeOut(120ms)` | Instant cut |
| **Stacked Screen Push** (Settings, Prayer Settings, Voices, Library, Bookmarks) | `fadeIn(220ms) + slideInHorizontally(240ms, ±8% RTL-aware)` | `fadeOut(140ms)` | Instant cut |
| **Stacked Screen Pop** | `fadeIn(160ms)` | `fadeOut(160ms) + slideOutHorizontally(200ms, ±8% RTL-aware)` | Instant cut |
| **Immersive Modal** (`NowPlayingScreen`, `FullscreenPreviewScreen`) | `slideInVertically(300ms, +18%) + fadeIn(240ms)` | `slideOutVertically(220ms, +18%) + fadeOut(160ms)` | Instant fade |
| **Reader Mode Switch** (`Mushaf <-> Continuous`) | `fadeIn(180ms)` cross-dissolve (feels like shifting reading view, not leaving Quran) | `fadeOut(140ms)` | Instant cut |
| **Docked Mini Player** | `slideInVertically(220ms) + fadeIn(180ms)` | `slideOutVertically(180ms) + fadeOut(140ms)` | Instant visibility |
| **Reader Ayah Action Pill** | `scaleIn(0.94f, 180ms) + fadeIn(150ms)` anchored above bottom nav | `scaleOut(0.96f, 140ms) + fadeOut(120ms)` | Instant visibility |

---

## 4. Micro-Interactions & State Feedback

1. **Ayah Tap & Recitation Follow (Mushaf & Continuous)**:
   - Tapping an ayah smoothly highlights the range (`mushafAyahHighlight`), fires a gentle haptic tick, and fades in `ReaderAyahActionPill`.
   - Automatic page turns during recitation (`MushafFollowPolicy`) use existing `pagerState.animateScrollToPage(target - 1)` after preloading neighbors (`ensureLoaded(target)`), preventing white flashes.
   - Pinch-zoom (`1f..4f`) locks pager swipes immediately; double-tap smoothly animates zoom back to `1f` (`animateFloatAsState` if motion enabled, instant snap if reduced motion).
2. **Audio Play / Pause & Downloads**:
   - Play/Pause icon cross-fades cleanly (`AnimatedContent`, `120ms`) and immediately updates `contentDescription` (`تشغيل` <-> `إيقاف مؤقت`).
   - Downloading Surah rows reveal a `3dp` linear progress bar (`animateFloatAsState` smoothing `downloadedAyahs / totalAyahs`) and transition cleanly into the emerald `متاح دون اتصال` badge on completion.
   - Switching Reciters cross-fades the selection border and updates the Surah list badges seamlessly without jumping scroll position.
3. **Prayer Countdown & Timetable**:
   - Countdown ticks once per second using `fontFeatureSettings = "tnum"` (zero horizontal jitter; NO digit flip animations that distract or waste battery).
   - Selecting a prayer row opens the Quick Adhan Sheet smoothly; switching Adhan mode updates the trailing prayer icon with a `150ms` cross-fade.
4. **Wallpapers Studio**:
   - Tapping a visual template swatch updates the sticky top phone preview with a `180ms` `Crossfade` over the rendered `Bitmap` (keeping existing `150ms` ViewModel debounce so rapid slider drags stay buttery smooth).
   - Apply button transitions smoothly: Idle -> Inline `18dp` spinner + `"جارٍ التطبيق..."` -> Animated icon+text confirmation pill (auto-clears after `4s` or on next edit).
5. **Bookmarks & Destructive Actions**:
   - Removing a bookmark from Quran Hub -> Bookmarks shows a bottom `Snackbar` with `تراجع (Undo)` (`4s` duration) above the bottom bar.
   - Deleting a downloaded Surah or clearing wallpaper history always prompts `ConfirmDialog` (never silent accidental loss).
6. **Honest Loading, Empty & Error Transitions**:
   - All async transitions (`Loading -> Content`, `Content -> Error`, `Idle -> Downloading`) use gentle `180ms` `Crossfade` / `AnimatedVisibility` — never harsh layout snaps or fake progress percentages.
