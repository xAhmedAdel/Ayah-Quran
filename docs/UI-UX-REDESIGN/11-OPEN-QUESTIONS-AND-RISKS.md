# 11 — Open Questions, Technical Constraints & Risks

Transparent record of everything that could not be verified directly during Phase UI-0, platform/technical constraints that bound implementation, and explicit decisions requiring Product Owner sign-off before Phase UI-1 begins.

---

## 1. Items Not Directly Verified in Phase UI-0

1. **Live Emulator Inspection Blocked Inside Standard Sandbox**:
   - `adb.exe` and `emulator.exe` reside outside the workspace sandbox (`C:\Users\sokar\AppData\Local\Android\Sdk`). Rather than executing unsandboxed binaries during a documentation-only phase, all code-level audits were verified directly against all 38 Kotlin UI files, all 38 test files, both `strings.xml` files, and all 22 handover screenshots.
   - **Follow-up**: At the start of Slice UI-1, capture fresh baseline screenshots of the missing views (`QuranScreen` real list, `BookmarksScreen`, `FullscreenPreviewScreen`, Dark Mode, and English LTR) on `ayah_avd`.
2. **Misnamed Handover Screenshot (`quran/15_quran_list.png`)**:
   - Visual inspection revealed `docs/UI-UX-HANDOVER/screenshots/quran/15_quran_list.png` actually captured the bottom of `PrayerSettingsScreen`, not `QuranScreen`. `QuranScreen.kt` was audited thoroughly from source code instead.
3. **Dark Mode & English LTR Visual Baselines**:
   - The handover screenshot archive contains only Light Mode Arabic captures. Dark Mode (`AyahDarkColors`) and English LTR layouts were audited directly from Kotlin source (`Color.kt`, `SurahName.kt`, `values/strings.xml`).
4. **Device-Specific Display Tests (HONOR 600 / MagicOS)**:
   - Physical HONOR 600 / MagicOS behavior was reviewed via handover documentation (`PERMISSIONS-AND-OS-INTEGRATIONS.md`, `PRAYER-TIMES-AND-ADHAN.md`) rather than physical hardware.

---

## 2. Technical & Platform Constraints

1. **Pinned Build Matrix**: AGP `8.13.2`, Kotlin `2.3.21`, KSP `2.3.14`, Compose BOM `2026.06.00`, Media3 `1.11.1`, Hilt `2.58`. Do not upgrade during UI slices.
2. **Material 3 Dark/Light `surfaceContainer*` Requirement**: Compose M3 falls back to neutral gray on Cards, Sheets, and `NavigationBar` unless all 5 `surfaceContainer*` tokens are explicitly specified.
3. **Android 14+ Exact-Alarm Revocation Kills Process**: Revoking `SCHEDULE_EXACT_ALARM` terminates the app process (OS behavior). `PrayerPermissionsInstrumentedTest` skips the revoke test on API 33+ for this reason; UI guidance copy must remain honest and clear.
4. **HONOR / MagicOS Background Alarm Restrictions**: External OEM behavior holds `AlarmManager` delivery unless the user disables automatic app-launch management. The vendor guidance dialog, battery settings link, 60-second diagnostic alarm, and test notification/adhan actions MUST remain intact and accessible.
5. **Wallpaper Engine & `WallpaperTemplateContractTest`**:
   - `WallpaperTemplateContractTest` pins the exact 5 template IDs (`emerald_luxury`, `minimal_cream`, `midnight_gold`, `islamic_architecture`, `geometric_art`). Visual swatches in the Studio must map 1:1 to these 5 IDs unless contract tests are intentionally updated.
   - `WallpaperManager` combined-flag and bitmap-recycle ordering in `WallpaperRenderer` are load-bearing; do not alter engine internals.
6. **Audio "Dead Flow" Hazard**:
   - Two historical bugs occurred when UI collected locally constructed `StateFlow`s instead of the singleton `ExoPlayerAyahPlaylistPlayer.state` and `QuranAudioDownloadRepository.states`. Every redesigned audio surface must retain the exact repository/player flow bindings.
7. **System Notification / Lock-Screen Player**:
   - Owned by Media3 `MediaSessionService` (`QuranPlaybackService` / `AdhanPlaybackService`). Do not attempt custom notification UI overrides.
8. **No `res/raw` Audio Compression**: Never add audio files to `res/raw` without checking `noCompress` in Gradle.

---

## 3. High-Priority Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| **Test Breakage from 6 -> 5 Bottom Tabs** (`TopLevelDestinationTest`, `AppUiTest`) | Certain (if 5-tab approved) | Medium | Update both test files cleanly in Slice UI-2 alongside adding stable `testTag`s; never delete behavioral assertions. |
| **Test Breakage from Lazy/Sheet Refactoring** (`PrayerTimesUiTest`, `AppUiTest`, `AudioTabUiTest` expect simultaneous un-scrolled nodes) | High | Medium | Update instrumented tests in the same slice as each screen refactor to scroll/open sheets explicitly and assert via `testTag` + semantic content. |
| **Gesture Conflict in Mushaf Reader** (`MushafPageReaderUiTest` uses `doubleClick(center)` & pinch offsets on `onRoot()`) | Medium | High | Keep reader chrome visible by default (ADR-05); never intercept double-tap or multi-touch pinch on `ZoomableMushafPage`; verify all 6 `MushafPageReaderUiTest` cases immediately after Slice UI-5. |
| **Sticky Preview Height on Small/Zoomed Screens (`WallpapersScreen`)** | Medium | Medium | Measure available viewport height (`BoxWithConstraints`): on compact screens (`height < 640dp` or fontScale >= `1.4f`), allow the preview stage to shrink proportionally (`min 180dp`) or collapse gracefully into scroll so controls never become unreachable (preventing the historical HONOR 600 overflow bug). |
| **Home Screen Over-Fetching (Next Prayer + Wallpaper Bitmap Rendering)** | Low-Medium | Medium | Reuse existing `PrayerTimeManager.todayTimes` / `tomorrowFajr` flows (with lifecycle-aware 1-minute or 1-second ticker only while Home is resumed) and cache rendered thumbnail bitmaps inside `HomeViewModel` so tab switching causes zero frame drops. |
| **APK Size Growth from Bundling a New Arabic UI Font** | Low | Low | Subset IBM Plex Sans Arabic (Arabic + Basic Latin glyphs, 3 weights: Regular/Medium/SemiBold = ~130–180KB total) OR fall back to platform sans if zero APK increase is preferred. |

---

## 4. Open Decisions Requiring Product Owner Approval Before Slice UI-1

Please review and confirm these 5 decisions before implementation starts:

1. **Visual Direction Sign-Off**: Approve **Direction D — "Al-Mushaf Al-Hadith"** (Sanctuary Emerald + Antique Gold + Warm Parchment / Obsidian Moss), or request adjustments toward Direction A, B, or C?
2. **Navigation Structure (5 Tabs vs 6 Tabs)**: Approve consolidating the Bottom Navigation Bar from **6 tabs to 5 daily tabs** (`Home · Quran · Prayer · Audio · Wallpapers`) and moving **Settings** to a prominent Home top-bar button (updating `TopLevelDestinationTest` + `AppUiTest`), or retain 6 bottom tabs?
3. **Arabic UI Typography Asset**: Approve bundling **IBM Plex Sans Arabic** (SIL OFL 1.1, ~150KB) alongside existing **Amiri** for noticeably cleaner Arabic UI legibility, or keep platform `Roboto` (`FontFamily.Default`) for zero APK size growth?
4. **Home Screen Prayer & Daily Ayah Enrichment**: Approve surfacing the live **Next Prayer** summary and **Daily Ayah** preview on `HomeScreen` (connecting existing `PrayerTimeManager` and `AyahSelector` into `HomeViewModel` — zero new backend engines), or keep Home limited strictly to its current Continue Reading + Wallpaper data?
5. **Now Playing Repeat Range & Reader -> Wallpaper Shortcut**: Approve exposing the already-implemented `setRepeatRange` (From/To ayah loop) in `NowPlayingScreen` and adding a direct `Wallpaper` action to the Reader's Ayah Action Pill (linking to existing `preview/{surahId}/{ayahNumber}`)?
