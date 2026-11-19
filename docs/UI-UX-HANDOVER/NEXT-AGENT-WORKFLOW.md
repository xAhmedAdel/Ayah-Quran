# Next-Agent Workflow (recommended phased redesign)

## Phase R0 — Baseline (½ day)

1. Read the handover package top-to-bottom.
2. Build + run: `assembleDebug`, install, launch (`DEVELOPMENT-ENVIRONMENT.md`).
3. Walk every screen on the emulator; compare against
   `SCREENSHOT-INDEX.md` and `SCREEN-INVENTORY.md`.
4. Run both suites; record the green baseline
   (148 JVM / 58 instrumented as of handover).

## Phase R1 — Design system foundation (2-3 days)

1. Define the new tokens in `core/designsystem/theme/` (colors, type,
   spacing, shapes) as a parallel theme layer — do not delete the old
   tokens until migration completes.
2. Extend `core/designsystem/component/` (buttons, chips, list rows,
   status badges, heroes) with the new components.
3. Migrate ONE low-risk screen first (Settings) to validate the layer.
4. Screenshot old vs new; run suites.

## Phase R2 — Shell & navigation (1-2 days)

1. Bottom bar / tab visual refresh (keep the same 6 destinations + routes).
2. Mini player + Now Playing restyle (keep collecting the same flows;
   add the position seek slider while you're there).
3. Home dashboard restructure (hero continue-reading card).

## Phase R3 — Quran surfaces (3-4 days, most care)

1. Quran list (LazyColumn, metadata chips, search styling).
2. Reader chrome: top bars, jump dialog, font controls, highlight
   styling. ⚠️ Do not touch `MushafText` internals, mapping, or policy.
3. Reader verification: page-follow, auto-scroll, highlight, bookmarks,
   both modes, Arabic + English.

## Phase R4 — Prayer & wallpapers (2-3 days)

1. Prayer tab hero (next-prayer countdown) + settings sub-structure.
2. Onboarding steps as a pager; keep honest statuses.
3. Wallpapers gallery + preview restyle (engine untouched).

## Phase R5 — Audio surfaces (1-2 days)

1. Audio tab LazyColumn conversion (perf issue #1) + row redesign.
2. Remove/merge the legacy Phase-5 playback panel.
3. Download progress bars.

## Phase R6 — Hardening (2 days)

1. Full regression: `testDebugUnitTest` + `connectedDebugAndroidTest`.
2. Manual passes: Arabic, English, RTL, dark/light, offline, audio in
   background, reader sync, adhan priority.
3. Screenshots of every redesigned screen (before/after).
4. Release build; update docs; single commit per phase.

## Rules of engagement during the redesign

- Never redesign two tabs in the same change.
- After every screen: emulator visual check + affected tests.
- Strings: add new ones to BOTH `values` and `values-ar`.
- Keep the changelog in the commit messages (existing convention).
