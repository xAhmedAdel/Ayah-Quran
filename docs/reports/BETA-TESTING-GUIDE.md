# Ayah — Beta Testing Guide

Thank you for testing Ayah! This guide explains how to install the beta
build, what to test, and how to report issues.

## Installation

1. Copy `app-release.apk` to your Android device (via USB, email, or cloud).
2. On your device, open the APK file. If prompted, allow "Install unknown
   apps" for the file manager you're using.
3. Tap **Install**.
4. Open **Ayah** from your home screen.

Requirements: Android 10 or later (API 29+).

## What to test

### Quran reading

- Open the **Quran** tab.
- Verify all 114 surahs appear in the list.
- Tap a surah to open the reader.
- Check that Arabic text renders correctly (right-to-left, diacritics).
- Scroll through a long surah (e.g., Al-Baqarah) — verify no clipping.
- Use the **previous/next** surah arrows at the top.
- Tap the **bookmark** icon next to an ayah, then check the **Bookmarks**
  section from Home.
- Close the app, reopen it, and check that **Continue Reading** on Home
  shows your last position.

### Quran search

- In the Quran tab, use the **Search surah** field.
- Search by Arabic name (e.g., البقرة), transliterated name (e.g., Baqara),
  and English name (e.g., Cow).
- Verify results match.

### Wallpapers

- Open the **Wallpapers** tab.
- Verify the preview renders a Quranic verse.
- Tap **Shuffle verse** to change the verse.
- Switch between all **five templates** (Emerald Luxury, Minimal Cream,
  Midnight Gold, Islamic Architecture, Geometric Art) and verify the
  preview changes.
- Adjust the **font size** slider and verify the preview updates.
- Change the **text position** (Top/Center/Bottom).
- Tap **Apply wallpaper** and verify your Home/Lock screen changes.
- Enable **Automatic updates** and select an interval.
- Verify the schedule status is shown.
- Check the **History** section and try **Reapply** and **Delete**.
- Try **Clear history** (a confirmation dialog should appear).

### Themes

- Go to **Settings** → **Appearance**.
- Switch between **Light**, **Dark**, and **Follow system**.
- Verify all screens update correctly.
- Close and reopen the app — verify your theme persisted.

### Audio

- Open the **Audio** tab.
- Verify the honest message: "No licensed recitations are available yet."
- Verify playback controls are disabled.
- Verify no fake playing state is shown.

### Accessibility

- Enable **TalkBack** (Settings → Accessibility → TalkBack) and navigate
  the app. Verify all interactive elements have descriptions.
- Set **font size** to maximum (Settings → Display → Font size) and
  verify all screens scale without breaking.

### Stability

- Background the app and reopen it.
- Rotate the device.
- Force-stop the app and reopen it.
- Repeat these on each major screen.

## Known limitations (beta 1)

- Audio recitation is not yet available (the architecture is in place;
  licensed catalogs will come in a future release).
- Lock-screen wallpaper visual confirmation is limited on some devices.
- Physical-device compatibility with OEM skins (Samsung One UI, Xiaomi
  MIUI) has not been tested.

## Bug report template

Copy this template and fill it in:

```
**Device model:**
**Android version:**
**OEM skin (if Samsung/Xiaomi/etc.):**

**What were you doing?**
(step-by-step: 1. Opened Quran tab, 2. Tapped Al-Baqarah, ...)

**Expected behavior:**
(what you expected to see)

**Actual behavior:**
(what actually happened)

**Screenshots:**
(if possible — power+volume-down simultaneously)
```

Send bug reports to: [owner's email — to be provided]

## Privacy

Ayah does not collect, store, transmit, or share any personal data.
The app operates fully offline. See `docs/PRIVACY-POLICY-DRAFT.md` for
details.
