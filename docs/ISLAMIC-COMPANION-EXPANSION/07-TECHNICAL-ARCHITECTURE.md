# 07 — Technical Architecture & Migration Plan

## 1. Architectural Principles

- **Zero Breaking Changes**: Do not alter `surahs`, `ayahs`, `bookmarks`, `reading_progress`, `wallpaper_history`, `selection_history`, `MushafText`, `PrayerTimeManager`, `AdhanPlaybackService`, or `QuranPlaybackService`.
- **Single-Module Clean Architecture Continuity**: Follow Ayah's exact package layout (`core/`, `domain/`, `data/`, `ui/`, `di/`, `background/`).
- **Zero Unnecessary Dependencies**: Everything required (`Room 2.8.5` + `FTS4` + `room-testing`, `DataStore 1.2.1`, `WorkManager 2.12.0`, `Hilt 2.58`, `Compose Material 3`, `org.json`) is **already in `gradle/libs.versions.toml`**. No new third-party libraries are needed!
- **Safe Schema Migrations**: Explicit `Migration(3, 4)` (and `Migration(4, 5)` if split by phase) registered in `DatabaseModule.kt` and tested against exported `app/schemas/.../3.json`.

---

## 2. Package Organization

```text
com.ayah.quran/
├── core/
│   ├── database/
│   │   ├── AyahDatabase.kt                (bump v3 -> v4 with explicit Migration_3_4)
│   │   ├── Migrations.kt                  (NEW: verified Migration objects)
│   │   ├── entity/companion/              (NEW: Room entities + FTS tables)
│   │   └── dao/companion/                 (NEW: AdhkarDao, TasbihDao, HadithDao)
│   ├── text/
│   │   └── ArabicTextNormalizer.kt        (NEW: pure Kotlin Arabic normalization)
│   └── share/
│       └── CompanionShareCardRenderer.kt  (NEW: Canvas PNG card generator)
├── domain/
│   ├── model/companion/                   (NEW: pure domain models)
│   ├── repository/companion/              (NEW: repository interfaces)
│   └── usecase/companion/                 (NEW: focused SRP use cases)
├── data/
│   └── companion/                         (NEW: importers, Room repos, DataStore prefs, reminder scheduler)
├── background/
│   └── CompanionReminderWorker.kt         (NEW: @HiltWorker for gentle reminders)
├── di/
│   └── CompanionModule.kt                 (NEW: Hilt bindings & DAO providers)
└── ui/
    └── companion/
        ├── adhkar/                        (AdhkarHubScreen, AdhkarSessionScreen, ViewModels)
        ├── tasbih/                        (TasbihScreen, TasbihViewModel, TasbihTapSurface)
        ├── duas/                          (DuasHubScreen, DuasViewModel)
        ├── hadith/                        (HadithLibraryScreen, HadithCollectionScreen, HadithDetailScreen, ViewModels)
        ├── reminders/                     (CompanionRemindersScreen, ViewModel)
        └── share/                         (CompanionShareCardSheet)
```

---

## 3. Unified Data & Room Schema Design (`v3 -> v4`)

### A. Adhkar & Duas Tables (Unified, Zero Duplication)

1. **`dhikr_categories`**
   - `id: String` (`@PrimaryKey`, e.g., `"morning"`, `"evening"`, `"after_prayer"`, `"sleep"`, `"waking"`, `"quranic_duas"`, `"prophetic_duas"`, `"anxiety"`, `"travel"`, `"daily_surahs"`)
   - `sectionType: String` (`ADHKAR_SESSION` | `ADHKAR_GENERAL` | `DUA_QURANIC` | `DUA_PROPHETIC` | `DUA_SITUATION` | `SUNNAH_SURAH`)
   - `titleAr: String`, `titleEn: String`
   - `sortOrder: Int`
   - `recommendedWindow: String?` (`MORNING` | `EVENING` | `AFTER_PRAYER` | `NIGHT` | `FRIDAY` | `null`)

2. **`dhikr_items`**
   - `id: String` (`@PrimaryKey`, stable slug e.g., `"hisn_morning_01"`, `"qdua_001"`)
   - `categoryId: String` (indexed FK -> `dhikr_categories.id`)
   - `itemType: String` (`DHIKR` | `QURANIC_DUA` | `PROPHETIC_DUA` | `SUNNAH_SURAH_LINK`)
   - `sortOrder: Int`
   - `titleAr: String`, `titleEn: String`
   - `textAr: String` (for `QURANIC_DUA`, joined/hydrated from `AyahEntity` via `surahId`/`startAyah`/`endAyah` so Quran text is never forked!)
   - `textEn: String?` (verified translation when licensed; null if Arabic-only)
   - `virtueAr: String?`, `virtueEn: String?` (`Fadl` / brief context when sourced)
   - `sourceAr: String`, `sourceEn: String` (exact reference citation)
   - `repeatCount: Int` (default `1`)
   - `surahId: Int?`, `startAyah: Int?`, `endAyah: Int?` (enables direct Mushaf jump for Quranic Duas & Daily Sunnah Surahs!)
   - `searchTextNormalized: String` (pre-normalized Arabic + English for fast FTS)
   - `datasetVersion: String`

3. **`dhikr_daily_progress`** (Persists user session progress across process death & restarts!)
   - Composite PK `(dayKey: String, itemId: String)` (where `dayKey = LocalDate.now().toString()`, e.g., `"2026-11-20"`)
   - `categoryId: String` (indexed)
   - `currentCount: Int`
   - `targetCount: Int`
   - `completed: Boolean`
   - `updatedAtMillis: Long`
   - *Retention hygiene*: Prune rows older than 30 days automatically during daily import/reconcile so table stays tiny.

4. **`dhikr_favorites`**
   - `itemId: String` (`@PrimaryKey`, FK -> `dhikr_items.id` CASCADE)
   - `createdAtMillis: Long`

### B. Digital Tasbih Tables

5. **`tasbih_counters`**
   - `id: Long` (`@PrimaryKey(autoGenerate = true)`)
   - `stableKey: String?` (unique nullable key for built-in presets: `"subhanallah"`, `"alhamdulillah"`, `"allahuakbar"`, `"astaghfirullah"`, `"salawat"`, `"tahlil"`)
   - `titleAr: String`, `titleEn: String`
   - `dhikrTextAr: String`
   - `targetCount: Int` (`0` = open-ended/free count, `1..10000` = target goal)
   - `currentCount: Int` (live persisted count — saved synchronously on every increment/undo via Room transaction!)
   - `completedRounds: Int` (cycles completed)
   - `lifetimeCount: Long`
   - `isCustom: Boolean`
   - `sortOrder: Int`
   - `updatedAtMillis: Long`

6. **`tasbih_daily_log`** (Optional lightweight daily aggregate — no noisy analytics bloat)
   - Composite PK `(dayKey: String, counterId: Long)`
   - `countTotal: Int`
   - `updatedAtMillis: Long`

### C. Hadith Library Tables

7. **`hadith_collections`**
   - `id: String` (`@PrimaryKey`, e.g., `"nawawi40"`, `"hadeethenc_faith"`, `"hadeethenc_worship"`)
   - `titleAr: String`, `titleEn: String`
   - `descriptionAr: String`, `descriptionEn: String`
   - `sourceAttribution: String` (e.g., `"HadeethEnc.com"`)
   - `datasetVersion: String` (preserved verbatim per HadeethEnc terms!)
   - `isBundled: Boolean`, `isDownloaded: Boolean`
   - `hadithCount: Int`
   - `sortOrder: Int`

8. **`hadith_items`**
   - `id: String` (`@PrimaryKey`, stable upstream ID e.g., `"henc_2994"` or `"nawawi_01"`)
   - `collectionId: String` (indexed FK -> `hadith_collections.id`)
   - `chapterTitleAr: String`, `chapterTitleEn: String`
   - `hadithNumber: String` (String to preserve book conventions like `"42"` or `"1/15"`)
   - `titleAr: String`, `titleEn: String`
   - `textAr: String`
   - `textEn: String?`
   - `gradeAr: String`, `gradeEn: String` (e.g., `"صحيح"` / `"Sahih"`)
   - `graderAttribution: String` (explicit scholar/source, never blank)
   - `referenceAr: String`, `referenceEn: String` (e.g., `"رواه البخاري ومسلم"`)
   - `explanationAr: String?`, `explanationEn: String?` (optional `Sharh`, strictly attributed)
   - `sourceVersion: String` (HadeethEnc version string)
   - `searchTextNormalized: String`
   - `sortOrder: Int`

9. **`hadith_fts`** (`@Fts4(contentEntity = HadithEntity::class)`)
   - Indexes `titleAr`, `titleEn`, `chapterTitleAr`, `chapterTitleEn`, `searchTextNormalized` for sub-15ms offline prefix/substring search across thousands of hadiths!

10. **`hadith_user_state`** (Favorites + Reading History in one lean table)
    - `hadithId: String` (`@PrimaryKey`, FK -> `hadith_items.id` CASCADE)
    - `isFavorite: Boolean`
    - `favoritedAtMillis: Long?`
    - `lastReadAtMillis: Long?`

---

## 4. Offline Content Import & Idempotency Strategy

Mirror Ayah's proven `QuranImporter` pattern (`data/quran/QuranImporter.kt`):
- `CompanionContentImporter` runs on `Dispatchers.IO` inside a Room `withTransaction {}` on first launch after upgrade (or whenever bundled asset `datasetVersion` increments).
- Checks stored `companion_imported_version` in `ayah_preferences` DataStore; skips immediately in `<2ms` when already up to date.
- **Preserves user tables**: Imports ONLY upsert static content (`dhikr_categories`, `dhikr_items`, `hadith_collections`, `hadith_items`) using `INSERT OR REPLACE` / `@Upsert` — NEVER touches `dhikr_daily_progress`, `dhikr_favorites`, `tasbih_counters`, or `hadith_user_state`!
- Seeds default `tasbih_counters` (`SubhanAllah 33`, `Alhamdulillah 33`, `Allahu Akbar 33`, `Astaghfirullah 100`, `Salawat 10`) using `OnConflictStrategy.IGNORE` on `stableKey` so custom user edits and counts are never overwritten.

---

## 5. Arabic Search Normalization (`ArabicTextNormalizer`)

Pure Kotlin utility in `core/text/ArabicTextNormalizer.kt` (unit-tested for edge cases):
- Strips Arabic diacritics (`\u064B-\u065F`, `\u0670`, `\u06D6-\u06ED`) and Tatweel (`\u0640`).
- Normalizes Alef variants (`أ إ آ ٱ -> ا`), Yaa/Alef Maqsura (`ى -> ي`), Hamza forms (`ؤ -> و`, `ئ -> ي`), Taa Marbuta (`ة -> ه`).
- Converts Arabic-Indic digits (`٠-٩`, `۰-۹`) to Western digits (`0-9`) for Hadith/Ayah number queries.
- Applied at import time into `searchTextNormalized` AND at query time on the user's search string.

---

## 6. Notifications & Reminders Architecture (Strictly Isolated from Prayer/Adhan)

### Hard Separation Guarantee
- **Prayer/Adhan Alarms** (`PrayerTimeManager` -> `AlarmManager` -> `PrayerNotificationsChannels.{PLAYBACK, ALERT, SILENT}` -> `AdhanPlaybackService`) remain **100% untouched**.
- **Companion Reminders** use:
  - Dedicated channel ID: `"ayah_companion_reminders"` (`IMPORTANCE_DEFAULT`, gentle platform notification sound or silent, `NotificationCompat.CATEGORY_REMINDER`).
  - Dedicated notification IDs: `7001` (Morning Adhkar), `7002` (Evening Adhkar), `7003` (Daily Hadith), `7004` (Daily Reading), `7005` (Friday Al-Kahf) — zero overlap with `PrayerNotifications` (`6001..6011`).
  - Dedicated `@HiltWorker`: `CompanionReminderWorker` scheduled via **WorkManager** (`ExistingWorkPolicy.REPLACE`, unique work name per reminder type, e.g., `"companion_reminder_morning_adhkar"`).
  - Why WorkManager instead of `AlarmManager`? Companion reminders do not require second-accurate exact alarms, do not consume `SCHEDULE_EXACT_ALARM` quota, survive Doze gracefully, and **structurally cannot race or interfere with `PrayerTimeManager`**.
  - **Smart Guards inside `CompanionReminderWorker.doWork()`**:
    1. Check `PrayerNotifications.canNotify()` — abort silently if permission revoked.
    2. Check if reminder is still enabled in DataStore.
    3. Check **Quiet Hours** (`quietStartMinute..quietEndMinute`) — skip posting if current local time falls inside quiet hours.
    4. **Completion Suppression**: For Morning/Evening Adhkar, check `dhikr_daily_progress` — if user ALREADY completed today's session, skip the redundant reminder!
    5. Deep-link `PendingIntent` into the exact destination (e.g., tapping Morning Adhkar notification opens `AdhkarSessionScreen("morning")`).
    6. Re-enqueue the next day's one-shot delayed work cleanly (single-instance `REPLACE` prevents duplicate notifications permanently).

---

## 7. DataStore Preference Keys (`ayah_preferences`)

All new keys use the `companion_` prefix inside `DataStoreModule`'s existing `ayah_preferences` file:
- `companion_content_version: Int`
- `companion_tasbih_haptic_enabled: Boolean` (default `true`)
- `companion_tasbih_sound_enabled: Boolean` (default `false`)
- `companion_tasbih_active_counter_id: Long`
- `companion_adhkar_auto_advance: Boolean` (default `true`)
- `companion_home_show_daily_hadith: Boolean` (default `true`)
- `companion_reminder_morning_enabled: Boolean` (default `false`), `_minute_of_day: Int` (default `420` = 07:00)
- `companion_reminder_evening_enabled: Boolean` (default `false`), `_minute_of_day: Int` (default `1050` = 17:30)
- `companion_reminder_hadith_enabled: Boolean` (default `false`), `_minute_of_day: Int` (default `540` = 09:00)
- `companion_reminder_reading_enabled: Boolean` (default `false`), `_minute_of_day: Int` (default `1260` = 21:00)
- `companion_reminder_kahf_enabled: Boolean` (default `false`), `_minute_of_day: Int` (default `600` = Friday 10:00)
- `companion_quiet_hours_enabled: Boolean` (default `true`), `_start_minute: Int` (`1380` = 23:00), `_end_minute: Int` (`300` = 05:00)

---

## 8. Background Safety & Process-Death Resilience

- **Tasbih Counter**: Every increment/undo writes directly to `TasbihDao` on `Dispatchers.IO` (backed by SQLite WAL) so force-close, low-memory kill, or screen lock never loses a single tap.
- **Adhkar Session**: Every tap updates `dhikr_daily_progress` immediately in Room; `HorizontalPager` current index saved via `rememberSaveable`.
- **Share Card Renderer**: Runs off the main thread (`Dispatchers.Default`), writes compressed PNG to `cacheDir/shared_cards/card.png` (auto-overwritten to prevent cache growth), exposed via existing `FileProvider` authority `${applicationId}.fileprovider` (add `<cache-path name="shared_cards" path="shared_cards/" />` to `res/xml/file_paths.xml` during Phase C-4).
