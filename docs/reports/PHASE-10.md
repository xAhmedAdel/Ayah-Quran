# Phase 10 — Full Arabic & English Localization

**Status: COMPLETE** — Arabic-default bilingual app with instant in-app language switching. Quran text remains Uthmani Arabic in every locale. Full regression green on the final code: **49 JVM + 43 instrumented tests, 0 failures; debug and signed release builds pass.**

---

## What Was Built

### 1. Arabic-first default with per-app language switching

- `values/` remains the English fallback; new `values-ar/strings.xml` carries the full Arabic translation of **every** user-facing string (nav, home, reader, wallpapers, audio, settings, dialogs, import states).
- **Arabic is the default on fresh install** (spec requirement) — the system language is deliberately not consulted when no preference is stored.
- New in-app **Language setting** (Settings → اللغة / Language) with exactly the spec'd options: **العربية (Arabic)** and **English**. Option labels are intentionally identical in both locales so users can always find their language.
- `AndroidManifest.xml` declares `android:localeConfig="@xml/locales_config"` (API 33+ per-app language metadata; `res/xml/locales_config.xml` lists `ar`, `en`).

### 2. Locale application architecture (`core/locale/`)

- **`AppLocale`** — single source of truth for the persisted language tag:
  - `wrap(base)` reads the tag and wraps the context via `createConfigurationContext` + `setLocales` (which also derives RTL layout direction for Arabic) and sets `Locale.setDefault`.
  - `isArabic` lets UI code pick presentation-only data (surah display names).
  - Storage: SharedPreferences (`ayah_sync_prefs` / `app_language_tag`) because `attachBaseContext` runs before any async store can be read.
- **Dual persistence** in `DataStoreUserPreferencesRepository`: every language write goes to DataStore (for the Settings UI / Flow observers) *and* SharedPreferences (for the synchronous attach-time read). Keys live in `AppLocale`, not duplicated.
- **`AyahApplication.attachBaseContext`** wraps the process base context; **`MainActivity.attachBaseContext`** wraps again per activity creation so a language change applies on the very next `recreate()` — no process restart needed.
- **Domain layer**: `UserPreferencesRepository.languageTag: Flow<String?>` + `setLanguageTag(tag)`; thin use cases `ObserveAppLanguageUseCase` / `SetAppLanguageUseCase` (the setter rejects unsupported tags); `SettingsViewModel` exposes `languageTag` (null storage resolved to the Arabic default) and a one-shot `languageApplied` event; `SettingsScreen` recreates the activity when it fires.

### 3. Beyond strings: full UI localization pass

An audit of on-screen text found several hardcoded-English gaps, all fixed:

| Gap | Fix |
|---|---|
| Surah list/reader/home showed transliterated names only | `localizedDisplayName()` (`core/locale/SurahName.kt`) — Arabic name from the verified dataset in Arabic UI, transliteration otherwise. Domain + DAO-entity overloads. |
| `reader_surah_format`, `home_continue_reading_format` were `translatable="false"` | Now translated ("سورة %1$s", "سورة %1$s · الآية %2$d من %3$d"). |
| Revelation type "Meccan/Medinan" + `surah_meta_format` / `reader_meta_format` hardcoded | `revelationLabel()` resource mapping; formats translated ("مكية · %1$d آية · الجزء …"). |
| Wallpaper template names (`Emerald Luxury`, …) hardcoded in template data | `WallpaperTemplateLabels.kt` maps template ids → localized resources; chips and the home wallpaper card both use it. Card state now carries `templateId`, not an English label. |
| Interval labels `"30 m"` / `"12 h"` / `"… min"` baked into strings | `wallpapers_interval_minutes` / `_hours` formats; `wallpapers_auto_every_format` and `home_wallpaper_automation_on` composed from them. |
| Home automation/format strings contained English words ("applied", "Auto-updates on · every…") | Translated ("طُبِّقت %2$s", "التحديث التلقائي مفعّل · كل %1$d دقيقة (تقريباً)"). |
| Audio repeat chips `"Off"/"One"/"All"` hardcoded | `audio_repeat_off/one/all` resources ("بدون / مفرد / الكل"). |

**Quranic integrity untouched:** localization changes presentation chrome only. The Uthmani dataset, its SHA-256 verification, bismillah conventions, and ayah markers are exactly as in Phases 2–9 (re-verified visually in the Arabic reader screenshot).

### 4. Tests made locale-agnostic (required by Arabic-first default)

With Arabic as default, UI tests asserting hardcoded English literals could not pass. Both end-to-end suites (`AppUiTest`, `AudioTabUiTest`) now resolve every user-visible string through the locale-wrapped activity (`composeRule.activity.getString(...)`) and compute surah names via `AppLocale.isArabic`. They pass in **either** app language. `SettingsViewModelTest` gained three language-chain tests (null→Arabic default, stored tag emission, persist + recreate signal) — 49 JVM tests total.

---

## Verification (all on final code)

| Check | Result |
|---|---|
| `testDebugUnitTest` | **49 tests, 0 failures** |
| `connectedDebugAndroidTest` (ayah_avd, API 36) | **43 tests, 0 failures** |
| `assembleDebug` / `assembleRelease` | both pass |
| Fresh install (`pm clear` → launch) | launches fully in **Arabic**, RTL layout mirrored (verified via UI dump + screenshot) |
| In-app switch العربية → English | instant UI flip after radio tap — no restart (screenshot 05) |
| Quran list / reader in Arabic | Arabic surah names, "مكية · ٧ آية · الجزء 1", Uthmani text intact |
| Font scale 2.0× | tested in **both** languages (screenshots 08, 09); text scales and wraps gracefully, nothing clipped |

### Screenshots (`docs/screenshots/phase10/`)

01 home (Arabic default, RTL) · 02 Quran list (Arabic names) · 03 mushaf reader (Arabic) · 04 settings (Arabic, language section) · 05 settings (English after in-app switch) · 06 home (English) · 07 Quran list (English) · 08 home English @2.0× · 09 home Arabic @2.0×

---

## Known Limitations (honest notes)

- **Application-context resources** keep the language they were created with until the next process restart; all *activity* UI (everything the user interacts with) updates immediately via `recreate()`. No current screen renders through application-context strings.
- **System-level per-app language** (Android 13+ Settings → Apps → Ayah): declared support via `localeConfig`, but the in-app setting is the supported path; the system override competes with the app's own stored tag (the stored tag wins on next attach).
- At 2.0× font scale, bottom-nav labels wrap to two lines ("Wallpa/pers", "الإعدادا/ت") — standard Material label wrapping, text never clips.
- Bidi rendering of the version string inside the Arabic About row shows bidi reordering of the LTR version code — expected Unicode behavior, not a defect.

## Rules Compliance

- No Quranic text modified, fabricated, or re-translated — the app *displays* the verified Uthmani dataset only; Arabic UI strings are interface text, never Quran.
- No fake states introduced; language persistence uses the same honest DataStore architecture.
- All existing features preserved (full 43-test instrumented regression covers reader, wallpapers, audio honesty states, bookmarks, history).
