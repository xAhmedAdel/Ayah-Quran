# 00 — Executive Summary: Ayah Islamic Daily Companion Expansion

## 1. Mission & Scope

This document summarizes the Phase 0 audit and expansion plan for **Ayah** (`com.ayah.quran`, branch `main`, commit `9c757e8`, release `v1.0.0-beta.9`), evaluating how to evolve the application from a completed Quran, Prayer/Adhan, Audio, and Wallpaper app (UI-0 through UI-9) into a calm, trustworthy, offline-first **Islamic Daily Companion** adding **Adhkar**, **Digital Tasbih**, **Duas**, **Hadith Library**, and **User-Controlled Daily Reminders**.

No application source code, resources, schemas, or releases were modified during this phase.

## 2. Key Audit Findings

### Reference Repository (`BelalYehiaSwilam0/quran-app`, commit `8f26120`)
- **What it actually is**: An Arabic-only vanilla HTML/CSS/JS Progressive Web App centered on a **Quran Audio Player** (`mp3quran.net` streams + bundled `ayat_timing` + `alquran.cloud` text) plus a single slide-in **Adhkar Drawer** (`js/adhkar.js`).
- **Critical License Finding**: The repository has **no license file** (`licenseInfo: null` on GitHub — default All Rights Reserved copyright). **No code, stylesheets, audio timing files, or text arrays may be copied from it.** It is used strictly as UX and functional inspiration.
- **Actual Companion Features Found**:
  - 65 hardcoded Adhkar items across 16 categories with diacritic-insensitive Arabic search (`normAr`), haptic tap/completion pulses, and a customizable `Salawat / Wird` card (`1..1000` target persisted in `localStorage`).
  - 3 `"Daily Surahs"` reminder items (`Al-Kahf`, `Al-Mulk`, `As-Sajdah`) — unlinked static cards in the reference PWA, which Ayah can elevate into real one-tap Mushaf deep links.
  - Several strong audio-player ergonomics (cross-surah range playback, duration-based end mode, play queue, per-reciter storage manager) documented separately as optional future audio enhancements.
- **Critical Gaps & Defects in Reference Repo**:
  - **No Hadith Library**, **no standalone Tasbih**, **no dedicated Dua Library**, **no Prayer Times/Adhan**, **no Reminders/Notifications**, **no Favorites**, **no English localization**.
  - **All 64 standard Adhkar counters wipe to zero** the moment the drawer or detail view closes.
  - **Truncated religious texts** (e.g., Evening `Sayyid al-Istighfar` `m5` is cut off mid-sentence) and mixed Egyptian colloquial UI strings.

### Current Ayah Codebase (`C:\AYAH`, `9c757e8`)
- **Mature Foundation**: Clean Architecture + Hilt + Room (`AyahDatabase` v3, `exportSchema = true`, explicit migrations required — zero destructive fallback) + DataStore (`ayah_preferences`) + WorkManager (`@HiltWorker`) + `AlarmManager` (`PrayerTimeManager` single-alarm mutex pattern) + Media3 + Jetpack Compose + Direction D (**Al-Mushaf Al-Hadith**) design system.
- **High Reuse Potential**:
  - **Quranic Duas** can reference Ayah's existing verified **Tanzil Uthmani** `ayahs` table (6,236 verses) directly by `(surahId, ayahNumber)` — zero external text, 100% textual integrity, instant Mushaf deep links.
  - **Shareable Hadith / Dhikr / Dua Cards** can reuse `WallpaperRenderer`'s offscreen Canvas + Amiri typography + Direction D template styling.
  - **Design System**: `AyahHeroCard`, `AyahSurfaceCard`, `AyahGroupCard`, `AyahSearchBar`, `AyahSelectionSheet`, `AyahStatusBadge`, `AyahPrimaryButton`, `AyahNumbering`, and `AyahMotionSpecs` cover ~85% of required UI primitives.
  - **Verification Harness**: 163 JVM unit tests + 64 instrumented tests + Room schema exports (`1.json`, `2.json`, `3.json`) ready for `MigrationTestHelper`.

## 3. Recommended Product Direction

1. **Preserve the 5-Tab Bottom Navigation Bar** (`Home · Quran · Prayer · Audio · Wallpapers`) by default — avoid 6-tab crowding. Introduce a calm **Companion Hub (`الذكر والسنن` / `Companion`)** reachable in 1 tap from Home via a compact 4-tile quick-access row (`Adhkar · Tasbih · Hadith · Duas`), plus contextual triggers (After-Prayer Adhkar chip on Prayer screen, Sunnah Surah links into Mushaf). *(An alternative Option B promoting Companion into the 5th bottom tab is also presented for user approval.)*
2. **Keep Home Calm**: Replace visual sprawl with a single **Daily Reflection** switcher card (`Daily Ayah | Daily Hadith`) and the 4-tile **Companion Strip** showing live time-of-day Adhkar progress (e.g. `Morning · 4/13`).
3. **Guided Sessions Over Static Lists**: Adhkar (`Morning`, `Evening`, `After Prayer`, `Sleep`, `Waking Up`) run as distraction-free **sequential sessions** with persistent daily progress, integrated tap counters, haptic ticks, Undo, and one-tap Mushaf jumps for Quranic Adhkar and Daily Sunnah Surahs.
4. **One-Handed Digital Tasbih**: Clean lower-thumb tap zone, circular progress ring, Undo (`-1`), confirmation-guarded Reset, custom Dhikr / Salawat targets, optional haptic/tick feedback, and crash-safe Room persistence — zero gamification clutter.
5. **Trustworthy, Attribution-First Hadith Library**: Bundle a lean, scholar-verified offline starter set (**40 Hadith An-Nawawi** + curated **HadeethEnc.com** thematic collections with Arabic text, verified English translation, explicit grading + scholar attribution, and concise `Sharh`), with optional user-initiated offline pack downloads for larger collections (`Sahih al-Bukhari`, `Sahih Muslim`, etc.) so APK size stays lean.
6. **Unified, Non-Intrusive Reminders**: Dedicated reminder notification channel strictly isolated from `PrayerNotifications` / `AdhanPlaybackService` (Adhan always wins priority), opt-in only, quiet-hours aware, and permission-respecting.

## 4. Key Decisions Requiring Approval Before Phase 1

1. **Navigation Placement**: Keep existing 5 tabs + Home Companion Strip (Option A, recommended) vs. swap Wallpapers into Home/Settings and promote Companion to 5th bottom tab (Option B).
2. **Hadith Initial Content Strategy**: Lean bundled starter (`HadeethEnc.com` thematic + 40 Nawawi) with optional downloadable full books vs. bundling larger Arabic-only Kutub al-Sittah upfront.
3. **Hisn al-Muslim Non-Commercial Condition**: Confirm Ayah remains free/non-commercial so Sheikh Sa'id al-Qahtani's *waqf* redistribution condition is honored without reservation.
4. **Adhkar Audio**: Defer Adhkar audio playback until legally cleared recordings are verified (do not import unverified MP3s from third-party repos).
5. **Phase Sequencing**: Confirm the proposed 6-phase rollout (`C-1` Data & Foundation -> `C-2` Guided Adhkar & Duas -> `C-3` Digital Tasbih -> `C-4` Hadith Library & Share Cards -> `C-5` Home Integration & Optional Reminders -> `C-6` Hardening & Release).
