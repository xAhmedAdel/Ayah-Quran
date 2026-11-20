# AYAH — Phase C-1 Implementation Report: Data Foundation & Room v3 -> v4 Migration

## 1. Scope Completed (Phase C-1 ONLY)

Phase C-1 delivers the verified offline data foundation for the Islamic Daily Companion expansion without modifying any UI screens, navigation, Quran text/mapping, prayer/Adhan scheduling, Media3 playback, Wallpaper rendering, or app version.

- **Verified Offline JSON Assets (`app/src/main/assets/companion/`)**:
  - `quranic_duas.json` (11.9 KB): **40 canonical Quranic Duas** stored strictly as `(surahId, startAyah, endAyah)` pointers joined at runtime against Ayah's verified Tanzil Uthmani `ayahs` table (zero duplicated Quran text).
  - `adhkar_hisn.json` (191.8 KB): **16 categories, 127 verified items**:
    - Morning & Evening Adhkar verbatim from `Seen-Arabic/Morning-And-Evening-Adhkar-DB` (**MIT License**, Copyright (c) 2024 Seen Arabic) — bilingual Arabic + English + virtue (`fadl`) + scholarly references + exact repeat counts.
    - After Prayer, Sleep, Waking, Wudu, Home, Mosque, Food, Travel, Anxiety/Distress/Debt, Healing, Forgiveness/Repentance, Istikhara, Weather/Everyday chapters verbatim from `asellam/HisnElMuslim` (**MIT License**, Copyright (c) 2021 Abdellah SELLAM + Sheikh Sa'id al-Qahtani free non-commercial unaltered redistribution condition) — **Arabic verbatim only, audio links excluded, zero fabricated English translations**.
    - 3 Daily Sunnah Surah links (`Al-Kahf` 18, `Al-Mulk` 67, `As-Sajdah` 32) referencing Ayah's Mushaf.
  - `hadith_starter.json` (667.7 KB): **6 collections, 202 verified Hadiths**:
    - **Forty Hadith of An-Nawawi (`nawawi40`, 42 hadiths)**: Verbatim classical Arabic from `fawazahmed0/hadith-api` (`ara-nawawi.min.json`, **The Unlicense** + Public Domain classical Arabic). No invented authenticity grades — grade field honestly states Imam An-Nawawi's embedded source attribution.
    - **HadeethEnc.com Thematic Starter (5 collections, 160 bilingual AR + EN hadiths)**: Fetched verbatim from `https://hadeethenc.com/api/v1/` across Quran & Sciences (30), Creed & Faith (30), Worship & Fiqh (30), Virtues, Manners & Dhikr (40), and Seerah & History (30) with **zero text modification**, explicit `HadeethEnc.com` attribution, scholarly grading, concise `Sharh`, and intact version tag (`hadeethenc-api-v1-2026-11-20`).
  - **Total Uncompressed Asset Footprint**: `871.4 KB` (~220 KB compressed in APK).
- **`ArabicTextNormalizer` (`core/text/ArabicTextNormalizer.kt`)**:
  - Deterministic search-only normalization: strips harakat/diacritics (`\u064B-\u065F`, `\u0670`, `\u06D6-\u06ED`), Tatweel (`\u0640`), ZWNJ/ZWJ; normalizes Alef (`أإآٱ -> ا`), Hamza carriers (`ؤ -> و`, `ئ -> ي`), Alef Maksura (`ى -> ي`), Taa Marbuta (`ة -> ه`); converts Arabic-Indic (`٠-٩`, `۰-۹`) to Western digits; strips Arabic/ASCII punctuation; collapses whitespace; lowercases Latin; exposes `buildFtsQuery()` for safe FTS4 prefix matching. Original display text is never mutated.
- **Room Database Migration `v3 -> v4` (`AyahMigrations.MIGRATION_3_4`)**:
  - Strictly non-destructive (`CREATE TABLE IF NOT EXISTS`, `CREATE INDEX IF NOT EXISTS`, `CREATE VIRTUAL TABLE IF NOT EXISTS`, `CREATE TRIGGER IF NOT EXISTS`). Zero destructive fallback; zero modification to existing 6 v3 tables (`surahs`, `ayahs`, `bookmarks`, `reading_progress`, `wallpaper_history`, `selection_history`).
  - Adds 10 tables + `hadith_fts` (`@Fts4(contentEntity = HadithEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)` + Room content-sync triggers): `dhikr_categories`, `dhikr_items`, `quranic_duas`, `dhikr_daily_progress`, `dhikr_favorites`, `tasbih_counters`, `tasbih_daily_log`, `hadith_collections`, `hadith_items`, `hadith_fts`, `hadith_user_state`.
  - Exported schema `app/schemas/com.ayah.quran.core.database.AyahDatabase/4.json`.
  - Registered via `.addMigrations(AyahMigrations.MIGRATION_3_4)` in `DatabaseModule.kt`.
- **Import Pipeline & Repositories**:
  - `CompanionContentParser` (strict validation rejecting malformed JSON, missing required fields/attribution/version, duplicate IDs, unknown FKs, invalid Surah/Ayah pointers, and manifest count mismatches).
  - `CompanionContentImporter` (idempotent DataStore version gate `companion_content_version`, single Room transaction, preserves all user tables, seeds 6 default Tasbih counters via `IGNORE`, launched asynchronously off main thread in `AyahApplication`).
  - `AdhkarRepositoryImpl`, `TasbihRepositoryImpl`, `HadithRepositoryImpl` (hybrid FTS4 `unicode61` + normalized substring fallback for Arabic attached prefixes like `بالنيات` matching `النيات`) + `CompanionModule`.

## 2. Build Configuration Note

- Added `sourceSets { getByName("androidTest").assets.srcDir("$projectDir/schemas") }` in `app/build.gradle.kts` so `MigrationTestHelper` can load exported Room schema fixtures (`3.json`, `4.json`).
- Added transitive dependency alignment constraint in `app/build.gradle.kts` (`kotlinx-serialization-core:1.8.1`, `kotlinx-serialization-json:1.8.1`) matching `androidx.room:room-migration-jvm:2.8.5`'s POM requirement to prevent `AbstractMethodError` in `MigrationTestHelper` caused by `lifecycle-viewmodel-savedstate` pulling `1.7.3`. No new third-party library was introduced.

## 3. Verification Summary

- **JVM Unit Tests (`:app:testDebugUnitTest`)**: **180/180 passed** (163 existing + 17 new C-1 tests: `ArabicTextNormalizerTest` [9], `CompanionDatasetIntegrityTest` [3], `CompanionContentParserMalformedInputTest` [5]).
- **Instrumented Android Tests (`:app:connectedDebugAndroidTest` on `ayah_avd`, API 36)**: **67 passed, 1 pre-existing API-gated skip (`PrayerPermissionsInstrumentedTest.notificationDenialDisablesPostingPath`), 0 failed** (68 total — including `AyahDatabaseMigrationTest` [1] and `CompanionDaoInstrumentedTest` [3]).
- **Debug Build (`:app:assembleDebug`)**: PASS.
- **No C-2 UI Started**: Zero UI screens, navigation routes, Home/Settings layouts, reminders, or share cards modified.
