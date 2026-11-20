# AYAH — Physical Device Validation Checklist (HONOR 600 / MagicOS)

- **Target Physical Device**: **HONOR 600** (MagicOS / Android)
- **Target Build**: `v1.0.0-beta.10` (`versionCode = 11`) & `v1.0.0-beta.11` (`versionCode = 12`, package `com.ayah.quran`)
- **Validation Status**: **PASSED on HONOR 600 (2026-11-20)** — Physical-device validation completed by project owner with all tested features functioning as expected and zero blocking issues (plus Wallpapers tab Audio Mini-Player hidden in `v1.0.0-beta.11` per physical-device visual review).
- **Honesty Note**: All automated unit tests (`209/209`) and Android instrumented tests (`77/78 passed, 1 API-gated skip`) were verified on the `ayah_avd` Android 16 (API 36) emulator, followed by manual physical-device validation on the **HONOR 600**.

---

## 1. Fresh Install

- [ ] Uninstall any older `com.ayah.quran` build from the HONOR 600 (or test both an upgrade over `v1.0.0-beta.9` and a clean install).
- [ ] Install the signed APK via ADB (`adb install -r app-debug.apk` or signed release APK) or on-device package installer.
- [ ] Verify installation succeeds with zero Play Protect or MagicOS package installer errors.
- [ ] Verify app icon and label (`آية` in Arabic locale / `Ayah` in English locale) appear cleanly on the HONOR launcher.

## 2. First Launch & Onboarding

- [ ] Launch Ayah for the first time with Airplane Mode **ON** (verify 100% offline first-run experience).
- [ ] Verify initial Quran database import (`6,236` Ayahs, `114` Surahs) and Companion database import (`16` Adhkar categories, `127` Adhkar items, `40` Quranic Duas, `6` Hadith collections, `202` Hadiths, `7` Tasbih presets) complete smoothly without UI freeze or ANR.
- [ ] Complete or dismiss the initial onboarding flow.
- [ ] Verify **zero** unexpected runtime permission popups (`POST_NOTIFICATIONS` or `ACCESS_COARSE_LOCATION`) appear unprompted on cold startup.

## 3. Quran Reading & 604-Page Mushaf

- [ ] Open the **Quran** tab -> verify all `114` Surahs and `30` Juz tabs load instantaneously.
- [ ] Search for a Surah and an Ayah query with and without diacritics (e.g. `الحمد لله` or `الرحمن`) -> verify instant results.
- [ ] Open Surah Al-Fatiha (Page 1) and swipe through several pages in **Mushaf Mode** (`604` pages total) -> verify smooth 60fps/120fps paging on the HONOR 600 display, crisp Amiri Uthmani rendering, and zero clipped verse markers.
- [ ] Switch to **Continuous Reading Mode**, add a bookmark on an Ayah, adjust reader font size (`A-` / `A+`), and verify the **Continue Reading** card on the Home screen updates to reflect the last-read Surah/Ayah/Page.

## 4. Prayer Times & Adhan

- [ ] Open the **Prayer** tab -> select a city offline (e.g., Cairo / Makkah) or test one-shot **Use my location** (`ACCESS_COARSE_LOCATION`).
- [ ] Verify all 5 daily prayer times + Sunrise and countdown timer render accurately on both the Prayer tab and the Home screen Next Prayer card.
- [ ] Verify the contextual **After-Prayer Adhkar** card appears on the Prayer screen and opens the After-Prayer Adhkar session when tapped.
- [ ] Enable Adhan notification for an upcoming prayer (or trigger the voice preview) -> lock the HONOR 600 screen and verify `AdhanPlaybackService` wakes the device, plays the selected Adhan voice cleanly through the speaker, and stops immediately when **Stop** is tapped on the notification.

## 5. Audio Playback in Background (Media3)

- [ ] Disable Airplane Mode (connect to Wi-Fi) and start Quran audio recitation from the **Audio** tab or Mushaf reader.
- [ ] Verify the **Mini-Player** appears above the bottom navigation bar and updates playback progress.
- [ ] Press the HONOR 600 Home button and lock the screen -> verify background recitation continues uninterrupted and lock-screen / notification shade Media3 controls (Play/Pause, Next/Previous Ayah, Stop) respond accurately.

## 6. Adhkar Guided Session

- [ ] From the Home screen **Companion Strip**, tap **Adhkar** (`الأذكار`).
- [ ] Open **Morning Adhkar** (`أذكار الصباح`) -> tap the central repeat counter button repeatedly on the first Dhikr.
- [ ] Feel the physical haptic click on each tap and confirm auto-advance moves to the next uncompleted Dhikr when the target count is reached.
- [ ] Tap **Undo** -> verify the count decrements (and returns to the previous card if auto-advanced).
- [ ] Tap **Reset** -> verify the confirmation dialog appears before clearing session progress.
- [ ] Return to **Morning Adhkar** and tap the **Surah Al-Kahf / Al-Mulk / As-Sajdah** Sunnah shortcut in the relevant category -> verify it opens the existing Mushaf reader directly to the target Surah without affecting Dhikr repeat counts.

## 7. Tasbih Counting & Persistence

- [ ] From the Home screen **Companion Strip**, tap **Tasbih** (`التسبيح`).
- [ ] Tap the central counting surface one-handed with your thumb -> verify physical haptic ticks on each increment and the stronger physical vibration on reaching the `33` target.
- [ ] Keep tapping past `33` (`34`, `35`...) -> verify the counter does **not** wipe to `0` at goal completion and records completed loops (`1`, `2`...).
- [ ] Tap **Undo** across the goal boundary (`34 -> 33 -> 32`) -> verify both `currentCount` and `completedGoals` decrement accurately.
- [ ] Create a **Custom Dhikr** (e.g., `سبحان الله وبحمده سبحان الله العظيم`, target `100`), increment it 15 times, swipe Ayah away from Recents, reopen Ayah -> verify the active preset, count (`15`), and today's total (`Today: ...`) are preserved.

## 8. Hadith Search & Favorites

- [ ] From the Home screen **Companion Strip**, tap **Hadith** (`الحديث`).
- [ ] Filter by collection chips (**An-Nawawi's Forty**, **Faith & Worship**, **Character & Mercy**, etc.).
- [ ] Search in Arabic without harakat (e.g. `انما الاعمال بالنيات` or `الدين النصيحة`) and in English (e.g. `intentions` or `mercy`) -> verify instant offline search results.
- [ ] Open a Hadith detail screen -> verify full Arabic matn, scholarly authenticity grade badge (`صحيح` / `Sahih`), book reference, grader attribution (`HadeethEnc.com`), dataset version (`hadeethenc-api-v1-2026-11-20`), collapsible **Explanation (Sharh)**, and **Key Benefits (Fawa'id)**.
- [ ] Tap the **Favorite** bookmark icon and **Copy** button -> verify the Hadith appears under the **Favorites** chip and copies cleanly to the system clipboard with full attribution.

## 9. Daily Reflection (Daily Ayah / Daily Hadith)

- [ ] Return to the **Home** screen and locate the **Daily Reflection** card.
- [ ] Verify the segmented switcher toggles smoothly between **Daily Ayah** (`آية اليوم`) and **Daily Hadith** (`حديث اليوم`).
- [ ] In **Daily Ayah** mode, tap the primary action -> verify it opens the Mushaf reader at that exact Surah and Ayah.
- [ ] In **Daily Hadith** mode, tap the primary action -> verify it opens the Hadith Detail screen for that exact Hadith ID.

## 10. Reminder Permission & Scheduling

- [ ] Open **Settings** -> scroll to **Adhkar & Reflection Reminders** (`تذكيرات الأذكار والتأمل اليومي`).
- [ ] Verify all 3 reminders (**Morning Adhkar**, **Evening Adhkar**, **Daily Reflection**) are **OFF** by default.
- [ ] Toggle **Morning Adhkar Reminder** ON -> verify Android's notification permission prompt appears (if not yet granted), configure the reminder time to 2 minutes in the future, and wait for the notification to fire.
- [ ] Tap the fired notification -> verify Ayah deep-links directly into the **Morning Adhkar** guided session.

## 11. Wallpaper Apply & Automation

- [ ] Open the **Wallpapers** tab -> customize a Quranic wallpaper template and preview it.
- [ ] Tap **Apply Wallpaper** -> verify the wallpaper applies cleanly to the HONOR 600 Home/Lock screen via Android `WallpaperManager`.

## 12. Force-Stop & Relaunch Persistence

- [ ] Open Android System Settings -> Apps -> **Ayah** -> tap **Force stop**.
- [ ] Relaunch Ayah -> verify:
  - Last-read Quran Surah/Ayah/Page is preserved.
  - Today's Adhkar session counts are preserved.
  - Active Tasbih preset, session count, and today's total count are preserved.
  - Saved Quran bookmarks, Favorite Duas, and Favorite Hadiths are preserved.
  - Selected Daily Reflection mode (`Daily Ayah` / `Daily Hadith`) and reminder toggles/times are preserved.

## 13. Device Reboot Persistence

- [ ] With at least one Prayer alarm enabled and one Companion Reminder enabled, **reboot the HONOR 600**.
- [ ] After unlocking the device, verify `PrayerSystemReceiver` and `CompanionReminderSystemReceiver` execute cleanly on `BOOT_COMPLETED` without crash notifications, and upcoming prayer alarms + companion reminders remain scheduled.

## 14. HONOR MagicOS Battery Optimization & Background Restrictions

- [ ] Open **Settings -> Prayer Settings -> Battery / Background Reliability** inside Ayah -> verify the vendor guidance banner detects HONOR/Huawei MagicOS and provides actionable guidance.
- [ ] On the HONOR 600, open **System Settings -> Battery -> App launch** -> locate **Ayah**:
  - Disable **Manage automatically** and ensure **Auto-launch**, **Secondary launch**, and **Run in background** are enabled so MagicOS does not kill scheduled Adhan alarms or WorkManager companion reminders overnight.
- [ ] Verify **Special access -> Alarms & reminders** is granted for `com.ayah.quran`.

## 15. Localization (Arabic RTL / English LTR), Theme (Light / Dark), & Large Font Scale (`1.5x`)

- [ ] In **Settings**, switch Language between **Arabic (RTL)** and **English (LTR)** -> verify every screen (Home, Quran, Prayer, Audio, Wallpapers, Adhkar Hub/Session, Duas Hub, Tasbih, Hadith Library/Detail, Settings, and Licenses sheet) flips layout direction cleanly with zero mixed-language chrome or mirrored fractions (`0 / 26`).
- [ ] Switch Theme between **Light (Warm Parchment)** and **Dark (Obsidian Moss)** -> verify high contrast and emerald/gold accents across all screens.
- [ ] In HONOR 600 System Settings -> **Display & brightness -> Text size**, set font scale to **Largest (`1.3x`–`1.5x`)** -> verify:
  - Home Companion Strip automatically switches from a 4-column row to a clean 2x2 grid with zero clipped labels.
  - Adhkar, Tasbih, and Hadith Arabic texts remain fully scrollable and legible with zero overlapping buttons.
  - Settings -> Licenses bottom sheet scrolls cleanly and displays all Quran, Adhkar, Hisn al-Muslim, An-Nawawi, and HadeethEnc.com attributions.
