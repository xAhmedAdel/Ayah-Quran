# AYAH — Phase C-7 Release Report (`v1.0.0-beta.10` & Follow-Up `v1.0.0-beta.11`)

- **Release Date**: 2026-11-20
- **Repository**: `https://github.com/xAhmedAdel/Ayah-Quran`
- **Branch**: `main`
- **Physical Device Validation**: **HONOR 600** (MagicOS / Android) — Completed and verified with zero reported blocking issues (plus immediate on-device UI polish in `v1.0.0-beta.11` hiding the Audio Mini-Player on the Wallpapers screen).

---

## 1. Release Preconditions & Commit History Audit

All 15 Phase 0 through Phase C-6 commits on top of baseline `9c757e80f37614011ff65be7826f2faaa5bb4ef9` (`v1.0.0-beta.9`, `versionCode = 10`), followed by the `v1.0.0-beta.10` release bump (`56c1154152c1387d8a75ac71e0fe28598391f64a`) and the HONOR 600 Wallpapers Mini-Player polish (`3c00c56ef759cf26e7506ab0c363e80744cb765d`, `v1.0.0-beta.11`), have been verified and pushed to `origin/main` without rewriting history, force-pushing, or modifying existing tags.

- **Untracked Files Excluded**: `docs/UI-UX-HANDOVER.zip` remains untracked and excluded from all commits and releases.

### Complete Commit Log (`9c757e8..3c00c56`)

| # | Phase / Milestone | Full Commit Hash | Summary |
|---|---|---|---|
| 0 | Baseline (`v1.0.0-beta.9`) | `9c757e80f37614011ff65be7826f2faaa5bb4ef9` | `chore(release): bump version to v1.0.0-beta.9 (code 10)` |
| 1 | Phase 0 | `5d032c0d569671f19951be7146a29b01508082de` | `docs(companion): add Phase 0 Islamic Daily Companion audit, architecture, and UX prototype` |
| 2 | Phase C-1 | `0b27ed2c918b2e940ec8456c4f6baf593e1ca528` | `feat(c-1): add verified offline companion datasets and ArabicTextNormalizer` |
| 3 | Phase C-1 | `7fe3b316fbfadf849d24a766e894e16cf6724a92` | `feat(c-1): add Room v3->v4 migration, companion schema, FTS4, importer, and repositories` |
| 4 | Phase C-1 | `b121808fb056788028a1de56e941ead0837ce15a` | `test(c-1): add dataset integrity, normalizer, Room migration, and companion DAO tests + C-1 report` |
| 5 | Phase C-2 | `22ecbc599d9560a81eb9251d2cfbd4e57926e841` | `feat(c-2): add Adhkar Hub, Guided Adhkar Session, Duas Hub, and After-Prayer entry point` |
| 6 | Phase C-2 | `099e184d59e30687fe702c3eeaec953b3299a65a` | `test(c-2): add Adhkar/Dua unit & instrumented UI tests, screenshots, and C-2 report` |
| 7 | Phase C-2 Gate | `92ac385d3306cd9996d2ac69327a71a78ab1262c` | `docs(c-2): record final pre-C-3 verification gate results in C-2 report` |
| 8 | Phase C-3 | `12ef23241076b0b82e2605b3d684c8b4d6e7a0f4` | `feat(c-3): add Digital Tasbih screen, presets, custom dhikr CRUD, goals, and haptics` |
| 9 | Phase C-3 | `df9adf4d695d70ec9bcce2a39bd61e48903f9624` | `test(c-3): add Tasbih unit & instrumented UI tests, screenshots, and C-3 report` |
| 10 | Phase C-4 | `c167c084b2d45628f44a48c575c92af6054bf520` | `feat(c-4): add Hadith Library, bilingual search, collection filter, detail reader, and favorites` |
| 11 | Phase C-4 | `e1f353b7aa37689a807dddc26d9071cada783b67` | `test(c-4): add Hadith unit & instrumented UI tests, screenshots, and C-4 report` |
| 12 | Phase C-5 | `2f78b79e8c8cdaba3b1e3b05653c907a73cdcf24` | `feat(c-5): add Home Companion Strip, Daily Reflection switcher, and isolated WorkManager reminders` |
| 13 | Phase C-5 | `059de75b651568994cb003f52b83254a3ce9c852` | `test(c-5): add Home Companion, Daily Reflection, and Reminder tests, screenshots, and C-5 report` |
| 14 | Phase C-6 | `b2770a19e2bace3b30d579040c554286d4d5309b` | `fix(c-6): harden R8 DAO projection keep rule, Home idle flow deduplication, and in-app license attributions` |
| 15 | Phase C-6 | `ffc821fe1ac34132a2ca1ff62559333f2c3fcd9f` | `docs(c-6): add content license audit, HONOR 600 physical-device checklist, R8 release screenshots, and C-6 report` |
| 16 | Phase C-7 (`v1.0.0-beta.10`) | `56c1154152c1387d8a75ac71e0fe28598391f64a` | `chore(release): bump version to v1.0.0-beta.10 (code 11)` |
| 17 | Post-Device Polish (`v1.0.0-beta.11`) | `3c00c56ef759cf26e7506ab0c363e80744cb765d` | `fix(wallpapers): hide audio mini-player on Wallpapers screen and bump to v1.0.0-beta.11 (code 12)` |

---

## 2. Release Changelog (`v1.0.0-beta.10` / `v1.0.0-beta.11`)

### Islamic Daily Companion Expansion (Phases C-1 through C-6)

1. **Home Companion Strip & Unified Daily Reflection (C-5)**:
   - Compact 4-destination Companion Strip on the Home screen (**Adhkar**, **Tasbih**, **Hadith**, **Duas**) while keeping the 5 bottom navigation destinations (**Home**, **Quran**, **Prayer**, **Audio**, **Wallpapers**) unchanged.
   - Adaptive layout switches from a 4-column row to a 2x2 grid at large system font scales (`fontScale >= 1.3f`).
   - Unified **Daily Reflection** card on Home switching between **Daily Ayah** (opens Mushaf reader at exact Surah & Ayah) and **Daily Hadith** (opens Hadith Detail screen), powered by deterministic epoch-day selection (`DailyReflectionSelector`).
2. **Optional, User-Controlled Reminders (C-5)**:
   - Three independent reminders in Settings (**Morning Adhkar** `07:00`, **Evening Adhkar** `17:30`, **Daily Reflection** `20:30`), all **OFF by default**.
   - Scheduled via isolated `WorkManager` periodic chains (`ayah_companion_reminder_*`) on channel `companion_reminders` (`IMPORTANCE_DEFAULT`), completely separated from exact `AlarmManager` Prayer/Adhan alarms.
3. **Adhkar Hub, Guided Sessions & Unified Duas Hub (C-2)**:
   - `16` offline Adhkar categories (`127` authenticated items from *Morning & Evening Adhkar* and *Hisn al-Muslim*).
   - Guided Adhkar Session with tap-to-count progress ring, haptic click, auto-advance to next uncompleted Dhikr, undo across auto-advance, reset confirmation dialog, daily local-date reset (`CompanionDateProvider`), and Sunnah Surah shortcuts (Surah Al-Kahf `18`, Surah Al-Mulk `67`, Surah As-Sajdah `32` -> Mushaf reader).
   - Unified Duas Hub with `40` Quranic Duas pointers (zero duplicated Quran text; resolves directly against bundled Tanzil `ayahs` table and opens Mushaf at `(surahId, startAyah)`) and Prophetic Duas, plus contextual **After-Prayer Adhkar** card on the Prayer screen.
4. **Digital Tasbih (C-3)**:
   - One-handed central counting surface with haptic tick feedback and stronger goal-completion vibration.
   - `7` seeded classical Dhikr presets + custom Dhikr creation, editing, and deletion.
   - Goal selector (`33`, `99`, `100`, `1000`, custom target, or `0 = Open`).
   - Non-resetting counter past goal completion (`33 -> 34`) with loop counter (`completedGoals`) and accurate post-goal undo (`34 -> 33 -> 32`).
5. **Hadith Library (C-4)**:
   - `6` offline collections (`202` Hadiths: `42` *Forty Hadith of Imam An-Nawawi* + `160` *HadeethEnc.com* thematic Prophetic Hadiths across Faith & Worship, Character & Mercy, Dhikr & Dua, Quran & Knowledge, and Family & Dealings).
   - Diacritic-insensitive Arabic & English search (`ArabicTextNormalizer`), collection filter chips, Favorites filter, and Hadith Detail screen with scholarly authenticity grade badge, book reference, grader attribution, dataset version, collapsible Explanation (*Sharh*), and Key Benefits (*Fawa'id*).
6. **Hardening, Migration & Wallpapers Polish (C-1, C-6, C-7)**:
   - Non-destructive Room `v3 -> v4` migration (`AyahMigrations.MIGRATION_3_4`) preserving all existing bookmarks, reading progress, and user preferences.
   - Updated bilingual in-app Licenses bottom sheet (`settings_licenses_summary`), R8 keep rule for `HadithWithUserState`, and `HomeViewModel.prayerSummaryFlow.distinctUntilChanged()`.
   - Hides the persistent Audio Mini-Player on the **Wallpapers** tab (`v1.0.0-beta.11`) based on HONOR 600 physical-device feedback.

---

## 3. Verified Release Artifacts, Signatures & Checksums

### A. APK Artifacts & SHA-256 Checksums

| Release Version | Version Code | Local APK Path | GitHub Release Asset URL | Size (Bytes) | Size (MB) | SHA-256 Checksum |
|---|---|---|---|---|---|---|
| **`v1.0.0-beta.10`** | `11` | `C:\AYAH\app\build\outputs\apk\release\Ayah-v1.0.0-beta.10-universal.apk` | `https://github.com/xAhmedAdel/Ayah-Quran/releases/download/v1.0.0-beta.10/Ayah-v1.0.0-beta.10-universal.apk` | `14,333,278` | `13.67 MB` | `353472ad9e211f5d12ba23cd9318bd7328c2c7bf466ad047213361e9e7d3a115` |
| **`v1.0.0-beta.11`** *(with Wallpapers Mini-Player fix)* | `12` | `C:\AYAH\app\build\outputs\apk\release\Ayah-v1.0.0-beta.11-universal.apk` (`app-release.apk`) | `https://github.com/xAhmedAdel/Ayah-Quran/releases/download/v1.0.0-beta.11/Ayah-v1.0.0-beta.11-universal.apk` | `14,333,278` | `13.67 MB` | `c1ea68d9c24dec1a0d3f05e79065807f5ae7df506a5645524d7febd340d2ff05` |

*(Note: The C-6 pre-bump release candidate build at `versionCode = 10` produced `14,333,278` bytes with SHA-256 `b9458eabd05d431613185742414a0d1323b643a0847538366a5cd37ef831b605`; the hash change in `beta.10` and `beta.11` is strictly due to the `versionCode`/`versionName` update in `AndroidManifest.xml` and `BuildConfig.VERSION_CODE`, with identical `14,333,278` byte size.)*

### B. APK Signature Verification (`apksigner verify --v --print-certs`)

Both `Ayah-v1.0.0-beta.10-universal.apk` and `Ayah-v1.0.0-beta.11-universal.apk` were verified with Android SDK Build-Tools `36.0.0` `apksigner`:
- **Verification Status**: `Verifies`
- **APK Signature Scheme v2**: `true`
- **Signer #1 Certificate DN**: `CN=Ayah, OU=Mobile, O=Ayah, L=Istanbul, ST=Istanbul, C=TR`
- **Signer #1 Certificate SHA-256 Digest**: `90fad949d87c3f75249998378a74e7109cd88a08446e606938263b0de9783e58`
- **Signer #1 Public Key SHA-256 Digest**: `9bab166aee7ec94269a275f34a7696606c37edfa265b414e1ba090332df5226d`
- **Key Algorithm / Size**: `RSA` / `4096 bits`

---

## 4. Test & Verification Results

1. **JVM Unit Test Suite (`.\gradlew :app:testDebugUnitTest`)**:
   - **Total**: `209`
   - **Passed**: `209`
   - **Failed**: `0`
   - **Skipped**: `0`
2. **Android Instrumented Test Suite (`.\gradlew :app:connectedDebugAndroidTest` on `ayah_avd` API 36)**:
   - **Total**: `78`
   - **Passed**: `77`
   - **Failed**: `0`
   - **Skipped**: `1` (`PrayerPermissionsInstrumentedTest.notificationDenialDisablesPostingPath`, pre-existing `SDK_INT < 33` skip)
3. **R8 / ProGuard & Resource Shrinking Verification (`.\gradlew :app:assembleRelease`)**:
   - `isMinifyEnabled = true`, `isShrinkResources = true`, `:app:lintVitalRelease` passed with `0` errors.
   - Installed and smoke-tested on `ayah_avd` (`emulator-5554`) and on the physical **HONOR 600** device.
4. **Physical-Device Verification (`HONOR 600`)**:
   - Verified by project owner across fresh install / in-app update, Quran & 604-page Mushaf, Prayer Times & Adhan, Audio recitation, Wallpapers studio, Home Companion Strip, Adhkar guided sessions, Duas Hub, Digital Tasbih, Hadith Library, Daily Reflection, and Reminders.

---

## 5. Git Tags & GitHub Prerelease Publication Confirmation

- **Pushed Branch**: `main` -> `origin/main` (fast-forward push, zero history rewrite)
- **Published Tags & GitHub Prereleases**:
  1. **`v1.0.0-beta.10`**:
     - **Commit**: `56c1154152c1387d8a75ac71e0fe28598391f64a`
     - **Release URL**: `https://github.com/xAhmedAdel/Ayah-Quran/releases/tag/v1.0.0-beta.10`
     - **Prerelease Flag**: `isPrerelease = true`
     - **Attached Asset**: `Ayah-v1.0.0-beta.10-universal.apk` (`14,333,278` bytes, SHA-256 `353472ad9e211f5d12ba23cd9318bd7328c2c7bf466ad047213361e9e7d3a115`)
  2. **`v1.0.0-beta.11`** *(On-device Wallpapers Mini-Player polish)*:
     - **Commit**: `3c00c56ef759cf26e7506ab0c363e80744cb765d`
     - **Release URL**: `https://github.com/xAhmedAdel/Ayah-Quran/releases/tag/v1.0.0-beta.11`
     - **Prerelease Flag**: `isPrerelease = true`
     - **Attached Asset**: `Ayah-v1.0.0-beta.11-universal.apk` (`14,333,278` bytes, SHA-256 `c1ea68d9c24dec1a0d3f05e79065807f5ae7df506a5645524d7febd340d2ff05`)

---

## 6. Known Limitations & Content/License Restrictions

1. **Arabic-First Scope on Non-Morning/Evening *Hisn al-Muslim* Chapters & *An-Nawawi's Forty***:
   - Morning & Evening Adhkar (`50` items), all `5` HadeethEnc thematic collections (`160` Hadiths), and all `40` Quranic Dua titles include verified English translations. The `14` supplementary *Hisn al-Muslim* chapters (`77` items) and the `42` Hadiths of *An-Nawawi* (`nawawi40`) ship with verified classical Arabic matn and bilingual category/collection headers (`textEn = null`) to avoid unverified or copyright-restricted third-party English translations.
2. **Free, Non-Commercial Redistribution Requirement**:
   - Under Sheikh Sa'id ibn Ali ibn Wahf al-Qahtani's *Hisn al-Muslim* redistribution terms and **HadeethEnc.com** attribution terms (documented in [CONTENT-LICENSE-AUDIT.md](file:///c:/AYAH/docs/ISLAMIC-COMPANION-EXPANSION/CONTENT-LICENSE-AUDIT.md)), Ayah's religious content must remain free of charge, unmodified, and free of commercial paywalls or ad networks.
3. **Development vs. Production Play Store Keystore**:
   - As noted in `app/build.gradle.kts`, current GitHub beta releases are signed with the project's configured release keystore (`CN=Ayah, OU=Mobile, O=Ayah, L=Istanbul, ST=Istanbul, C=TR`) so in-app updates install seamlessly across beta builds. Before any future Google Play Store submission, review `docs/reports/RELEASE-CHECKLIST.md`.
