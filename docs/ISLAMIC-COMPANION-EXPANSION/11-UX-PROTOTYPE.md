# 11 — Interactive UX Prototype Guide

## 1. Prototype Location

An interactive, standalone HTML prototype illustrating the proposed **Islamic Daily Companion Expansion** in Ayah's **Direction D (Al-Mushaf Al-Hadith)** visual language has been created at:

- [companion-expansion-prototype.html](prototypes/companion-expansion-prototype.html)

Open it directly in any modern browser (Chrome, Edge, Firefox) — zero server or build step required.

---

## 2. What the Prototype Demonstrates

The prototype runs inside an interactive mobile phone frame (`390 × 844`) with live top-bar controls to switch:
- **Language & Direction**: **Arabic RTL (`العربية`)** and **English LTR (`English`)**.
- **Theme**: **Warm Parchment Light** (`#F6F2E9` / `#0F3D32` / `#C4A052`) and **Obsidian Moss Dark** (`#0C1210` / `#131B17` / `#D6B66B`).

### Interactive Screens Included

1. **Home (Expanded — Calm Sanctuary)**
   - Preserves existing Continue Reading Hero + Live Next Prayer card + 5-tab bottom navigation (`Home · Quran · Prayer · Audio · Wallpapers`).
   - Shows the new **4-Tile Companion Strip** (`Adhkar` with live Morning progress badge, `Tasbih`, `Hadith`, `Duas`).
   - Shows the calm **Daily Reflection Card** with working `Daily Ayah | Daily Hadith` toggle (zero extra Home bloat).

2. **Adhkar Hub (`الأذكار`)**
   - Live diacritic-insensitive Arabic & English search bar.
   - Time-of-day **Morning Adhkar Hero Card** (`AyahHeroCard`) with live progress reflecting taps made in the Guided Session.
   - Core Daily Sessions 2-column grid (`Morning`, `Evening`, `After Prayer`, `Before Sleep`, `Waking Up`).
   - **Daily Sunnah Surahs** (`Al-Kahf`, `Al-Mulk`, `As-Sajdah`) with clear Mushaf deep-link indicator.

3. **Guided Adhkar Session (`جلسة أذكار الصباح`)**
   - Working multi-step session with top progress bar (`1 / 4`, etc.), Amiri Arabic text, verified source citation, expandable virtue/translation disclosure, and **live persistent counter**!
   - Large one-handed lower tap button with **Undo (`-1`)**, **Prev / Next**, **Reset confirmation**, and automatic smooth advance upon completion.
   - Closing and reopening preserves progress (demonstrating the exact fix over the reference repo's ephemeral drawer!).

4. **One-Handed Digital Tasbih (`المسبحة`)**
   - Top active Dhikr preset selector (`SubhanAllah 33`, `Alhamdulillah 33`, `Allahu Akbar 33`, `Astaghfirullah 100`, `Salawat 10`) + working **`+ Custom Dhikr`** modal.
   - Large lower-half tactile counting pad with live SVG circular progress ring, tabular counter, cycle counter, **Undo (`-1`)**, **Next Preset** cycle, and confirmation-guarded **Reset**.

5. **Duas Hub (`الأدعية`)**
   - Working 3-segment switcher: **Quranic Duas** (showing `Surah:Ayah` badge + `Open in Mushaf` action), **Prophetic Duas**, and **By Situation** (filterable by Anxiety, Gratitude, Forgiveness, Travel).
   - Working favorite toggle and live search.

6. **Hadith Library & Hadith Detail (`الحديث النبوي`)**
   - Search bar, `Collections | Saved` filter, Daily Hadith highlight, and bundled starter collections (`40 Hadith An-Nawawi`, `HadeethEnc Thematic Chapters`).
   - Tapping any Hadith opens **Hadith Detail** showing Amiri Arabic Matn, English translation, **explicit Authenticity Grade + Grader Attribution + HadeethEnc Version**, collapsible `Sharh` (Explanation), Favorite toggle, Copy, and **Share Card Preview Modal** (switching between Sanctuary Emerald & Warm Parchment card styles).

---

## 3. Prototype Content Disclaimer

All sample Arabic and English entries inside `companion-expansion-prototype.html` are **illustrative prototype samples** demonstrating layout, RTL/LTR typography, attribution formatting, and interactive flows only. Production implementation in Phase C-1 will import exclusively from frozen, license-verified, integrity-tested datasets documented in `06-CONTENT-SOURCES-AND-LICENSES.md`.

The HTML prototype is strictly a visual/UX review artifact and is **not** a replacement for native Jetpack Compose implementation.
