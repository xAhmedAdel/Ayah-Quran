# 10 — Key Decisions, Trade-Offs & Open Questions Requiring Approval

Before starting Phase C-1 implementation, please review and approve these 6 decisions:

---

## Decision 1: Navigation Placement (Bottom Bar vs. Home Companion Strip)

| Option | Bottom Bar Tabs (Always 5) | How Companion Is Reached | Pros | Cons |
| :--- | :--- | :--- | :--- | :--- |
| **Option A (Recommended)** | `Home · Quran · Prayer · Audio · Wallpapers` *(unchanged)* | 1 tap from Home **Companion Strip** (`Adhkar · Tasbih · Hadith · Duas`) + contextual triggers on Prayer screen | Zero disruption to approved UI-2 nav & existing UI tests; Wallpapers stays top-level; 1-tap reach on launch | Switching directly from Quran/Audio to Adhkar takes 2 taps (`Home -> Adhkar`) |
| **Option B** | `Home · Quran · Companion · Prayer · Audio` | Dedicated 3rd bottom tab (`Companion` with 4 segments: `Adhkar · Tasbih · Hadith · Duas`); Wallpapers moves to Home card, Settings, Reader More menu & Share | Matches daily worship frequency (Adhkar/Tasbih used 2–5x/day vs Wallpapers ~1x/week); 1 tap from anywhere | Changes approved UI-2 bottom bar; requires updating `TopLevelDestinationTest` & `AppUiTest` |

**Question**: Do you prefer **Option A** (keep current 5 tabs + Home Companion Strip) or **Option B** (promote Companion to bottom bar and move Wallpapers to Home/Settings)?

---

## Decision 2: Initial Bundled Hadith Scope & APK Size Trade-Off

| Option | Bundled Offline Content | Est. APK Delta | English Translation |
| :--- | :--- | :--- | :--- |
| **Option 2A (Recommended)** | **40 Hadith An-Nawawi** (42) + **HadeethEnc.com Essential Thematic Pack** (~300–500 scholar-verified hadiths across Faith, Worship, Dhikr, Character, Family) | `+1.0 – 1.6 MB` (APK stays ~14.5 MB) | Verified bilingual AR + EN + grading + concise `Sharh` (100% license-compliant with attribution & version) |
| **Option 2B** | Option 2A + optional on-demand downloadable full classical Arabic books (`Sahih al-Bukhari`, `Sahih Muslim`, `Riyad as-Salihin`) in Phase C-6 | `+1.6 MB` base (`0 MB` extra in APK; user downloads on demand) | Arabic-only for full classical books until English translator licenses are independently verified |
| **Option 2C** | Bundle full Sahih al-Bukhari + Sahih Muslim upfront inside APK | `+12 – 18 MB` (APK doubles to ~28–31 MB) | Arabic-only (English licensing unresolved for Darussalam translations) |

**Question**: Approve **Option 2A** for Phase C-4 (with **2B** on-demand classical packs evaluated in Phase C-6)?

---

## Decision 3: Hisn al-Muslim Non-Commercial Condition & English Provenance

- **Finding**: Sheikh Sa'id al-Qahtani's *Hisn al-Muslim* permits free, unaltered redistribution (*waqf* condition: no paid gating, no commercial ads tied to the book, no abridgment/deletion).
- **Confirmation Needed**:
  1. Confirm Ayah will remain free & ad-free so *Hisn al-Muslim* text complies cleanly with the author's condition.
  2. For English Adhkar translations: `Seen-Arabic/Morning-And-Evening-Adhkar-DB` (MIT) covers Morning & Evening AR+EN cleanly; for secondary chapters (Travel, Mosque, Home, etc.), should we ship **Arabic verbatim first** and include English only for verified MIT-sourced entries until non-Morning/Evening English translator provenance is confirmed? *(Recommended: Yes — never ship unverified English translations.)*

---

## Decision 4: Adhkar Audio Recordings

- **Finding**: Third-party GitHub Adhkar repos (`YousefAsalya/Islamic-Pro-azkar-API`, `rn0x/Adhkar-json`) include MP3 files with **no verifiable per-reciter copyright/license provenance**.
- **Recommendation**: **Defer Adhkar audio playback** until legally cleared recordings (CC0/CC-BY or direct reciter permission, matching Ayah's Phase 15 Adhan standard) are obtained. Do NOT bundle unverified MP3s.
- **Question**: Confirm deferring Adhkar audio?

---

## Decision 5: Home Daily Reflection Layout

- **Recommendation**: Keep Home calm by upgrading the existing `DailyAyahCard` slot into a single **Daily Reflection Card** with a clean 2-pill toggle (`Daily Ayah | Daily Hadith`), rather than stacking two separate large quote cards on Home.
- **Question**: Approve single tabbed `Daily Reflection` card on Home?

---

## Decision 6: Post-Expansion Backlog Items (Out of Scope for Phases C-1..C-6)

Confirmed explicitly deferred so Phases C-1..C-6 stay focused and cohesive:
- **Qibla Compass** (requires magnetometer calibration, declination, sensorless fallback — best as its own dedicated phase).
- **Audio Player Enhancements** inspired by the reference repo (cross-surah range playback, playback queue drawer, per-reciter storage breakdown modal) — best as a separate Audio v2 phase.

---

## Summary of Primary Technical & Product Risks

| Risk | Severity | Mitigation |
| :--- | :--- | :--- |
| Room `v3 -> v4` migration corrupting existing users' Quran bookmarks or reading progress | High | Explicit `Migration_3_4` (zero destructive fallback) verified by `MigrationTestHelper` against exported `3.json`. |
| Truncated or misattributed religious text entering offline bundles | High | Strict source manifest + automated `AdhkarDatasetIntegrityTest`, `QuranicDuasIntegrityTest`, and `HadithDatasetIntegrityTest` in JVM CI. |
| HadeethEnc.com terms violation (missing version or altered wording) | Medium | Store & render `sourceVersion` verbatim; importer performs zero text rewriting; add HadeethEnc to Settings Licenses sheet. |
| Companion reminders interfering with exact Prayer/Adhan alarms | High | Completely separate WorkManager pipeline, separate notification channel (`ayah_companion_reminders`), separate notification IDs (`7001..7005`), zero touch to `PrayerTimeManager`. |
| Home screen becoming cluttered | Medium | Compact 4-tile Companion Strip + tabbed Daily Reflection card keeps Home vertical footprint almost unchanged. |
