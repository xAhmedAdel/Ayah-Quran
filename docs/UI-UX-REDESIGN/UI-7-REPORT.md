# AYAH — UI-7 Report: Prayer Times, Prayer Settings, Onboarding & Adhan Library

## 1. Scope Completed

Phase UI-7 redesigns the entire **Prayer & Adhan** flow (`PrayerTimesScreen`, `PrayerTimesViewModel`, `PrayerSettingsScreen`, `PermissionsDashboardSection`, `PrayerOnboardingScreen`, `AdhanVoiceScreen`, `AdhanVoiceViewModel`, `AdhanLibraryScreen`) into Direction D (**Al-Mushaf Al-Hadith**), fixing every audited duplication, copy defect, and layout wall while preserving offline `adhan-java` calculations, exact alarm scheduling, `ON_RESUME` permission verification, and Adhan-wins playback priority.

### Prayer Times (`PrayerTimesScreen` + `PrayerTimesViewModel`)
- **Locale-Aware Hijri & Gregorian Dates**: Fixed hardcoded `" AH"` suffix in Arabic (`" هـ"` in AR / `" AH"` in EN) and explicit `Locale` formatting for both Hijri and Gregorian dates.
- **Sanctuary Hero Card (`AyahHeroCard`)**: Deep Emerald card with gold hairline, tappable City · Country pill opening `CityPickerDialog`, localized Hijri & Gregorian dates, next prayer in Amiri `34sp`, BiDi-isolated tabular countdown `HH:MM:SS`, next prayer time, and Adhan Active/Muted badge.
- **Structured Timetable Card**: Highlights the upcoming prayer row (`primaryContainer` tint + `prayer_next` gold badge), shows trailing Adhan mode icon on every row, and opens a quick `AyahSelectionSheet` on row tap to switch that prayer's `AdhanMode`. Sunrise remains clearly separated with `prayer_sunrise_note`.
- **Conditional Reliability Banner**: Uses `AyahBannerStrip(WARNING)` when `!exactAlarmGranted`.

### Prayer Settings & Reliability Dashboard (`PrayerSettingsScreen` + `PermissionsDashboardSection`)
- **Eliminated 2 Duplicated Permission Cards & Stale Copy**: Consolidated all 3 previously overlapping permission sections into a single unified `PermissionsDashboardSection` under `prayer_settings_advanced`, removed the stale `"لا توجد تسجيلات أذان مرخّصة بعد"` copy, and permanently eliminated the denied-notification `"ممنوح"` button bug.
- **Calm Modal Bottom Sheets**: Replaced the 10-chip vertical Calculation Method wall, 3-chip High-Latitude wall, 5 inline adjustment steppers, and 20 squeezed per-prayer Adhan FilterChips with compact `AyahSettingRow`s opening `AyahSelectionSheet` and `AdjustmentsSheet`, plus exposed `setHijriAdjustment` (`-2..+2`).
- **Collapsible Diagnostics Accordion**: Houses onboarding shortcut, battery settings, test notification, test Adhan, and 60-second diagnostic alarm cleanly behind a single expandable row.

### Prayer Onboarding, Adhan Voices & Adhan Library
- **Prayer Onboarding (`PrayerOnboardingScreen`)**: Added `AyahStepProgress` (5 steps), replaced `RadioButtonUnchecked` circles with numbered circular step badges (`1..5`) and verified checkmarks, kept `ON_RESUME` verification and `VendorGuidanceDialog` (fixing `"لل تطبيق"` -> `"للتطبيق"`).
- **Adhan Voices (`AdhanVoiceScreen` + `AdhanVoiceViewModel`)**: Added `"Apply one voice to all prayers"` (`selectVoiceForAll`) card, single grouped 5-prayer card, and inline Play/Pause preview button per prayer.
- **Adhan Library (`AdhanLibraryScreen`)**: Replaced raw text input with `AyahSearchBar`, kept single-line `LazyRow` category chips, upgraded recording cards to `AyahSurfaceCard`, and **removed the Delete icon from un-downloaded remote recordings**.

## 2. Files Changed

- `app/src/main/java/com/ayah/quran/ui/prayer/PrayerTimesViewModel.kt`
- `app/src/main/java/com/ayah/quran/ui/prayer/PrayerTimesScreen.kt`
- `app/src/main/java/com/ayah/quran/ui/prayer/PrayerSettingsScreen.kt`
- `app/src/main/java/com/ayah/quran/ui/prayer/PermissionsDashboardSection.kt`
- `app/src/main/java/com/ayah/quran/ui/prayer/PrayerOnboardingScreen.kt`
- `app/src/main/java/com/ayah/quran/ui/prayer/AdhanVoiceViewModel.kt`
- `app/src/main/java/com/ayah/quran/ui/prayer/AdhanVoiceScreen.kt`
- `app/src/main/java/com/ayah/quran/ui/prayer/AdhanLibraryScreen.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-ar/strings.xml`

## 3. Verification

- `:app:assembleDebug` — PASS
- `:app:testDebugUnitTest` — **163/163 passed**
- `:app:connectedDebugAndroidTest` (`PrayerTimesUiTest` on `ayah_avd`, API 36) — **3/3 passed**

## 4. Visual Verification Screenshots (`docs/UI-UX-REDESIGN/screenshots/ui-7/`)

1. `01_prayer_times_ar_light.png` — Prayer Times AR Light (Sanctuary Hero card, live countdown, structured timetable with upcoming prayer highlight and Adhan mode icons).
2. `02_prayer_settings_ar_light.png` — Prayer Settings AR Light (grouped Location, Calculation sheets, Hijri adjustment, Adhan modes).
3. `03_prayer_settings_reliability_ar_light.png` — Unified Permissions & Reliability Dashboard + collapsible Diagnostics accordion.
4. `04_prayer_times_ar_dark.png` — Prayer Times AR Dark (Obsidian Moss dark mode).
