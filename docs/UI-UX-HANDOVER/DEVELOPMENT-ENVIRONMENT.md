# Development Environment (verified 2026-11-19)

All values below were read from the actual machine/repository. Anything
not verifiable is marked UNKNOWN.

## Machine

| Item | Value |
|---|---|
| OS | Windows 10/11 (build 26300 reported by shell; exact edition UNKNOWN) |
| Shell used by the previous agent | Git Bash (POSIX) — PowerShell commands below are equivalents |
| JDK | OpenJDK 21.0.11 LTS (on PATH) |
| Android SDK | `C:\Users\sokar\AppData\Local\Android\Sdk` |
| Emulator binary | `C:\Users\sokar\AppData\Local\Android\Sdk\emulator\emulator.exe` |
| ADB | `C:\Users\sokar\AppData\Local\Android\Sdk\platform-tools\adb.exe` |
| Android Studio | UNKNOWN (not verified; builds ran via Gradle CLI) |
| GitHub CLI | `gh` authenticated as `xAhmedAdel` |

## Repository

| Item | Value |
|---|---|
| Location | `C:\AYAH` |
| Remote | `https://github.com/xAhmedAdel/Ayah-Quran.git` |
| Branch | `main` |
| Latest commit at handover | `3d4cb56` — "Phase 17 slice 3: automatic mushaf page navigation during recitation" |

## Project versions (from `gradle/libs.versions.toml` + `app/build.gradle.kts`)

| Item | Value |
|---|---|
| AGP | 8.13.2 |
| Kotlin | 2.3.21 |
| KSP | 2.3.14 |
| Gradle wrapper | 8.13 |
| Compose BOM | 2026.06.00 |
| Material 3 | via BOM |
| Media3 | 1.11.1 (exoplayer + session) |
| Hilt | 2.58 |
| Activity Compose | 1.12.4 |
| compileSdk / targetSdk | 36 |
| minSdk | 29 (Android 10) |
| versionCode / versionName | 7 / 1.0.0-beta.6 |
| applicationId | `com.ayah.quran` (debug adds `.debug` suffix) |
| Java target | 17 (toolchain runs on JDK 21) |
| R8/minify | enabled for release |

⚠️ Known version constraint: newer AGP/Kotlin than the pinned matrix has
broken builds before — do not bump versions as part of a UI redesign.

## Emulator

| Item | Value |
|---|---|
| AVD name | `ayah_avd` |
| Android | SDK built for x86_64, API 36 (Android 16) |
| Resolution | 1080×2400 |
| Density | ~420dpi (Pixel-class) |
| Language of system UI | varies; the APP is Arabic-first regardless |

## Exact commands (PowerShell)

```powershell
# repo
cd C:\AYAH
git status

# build
.\gradlew :app:assembleDebug          # debug APK → app\build\outputs\apk\debug\app-debug.apk
.\gradlew :app:assembleRelease        # signed release (dev keystore from local.properties)

# tests
.\gradlew :app:testDebugUnitTest             # JVM suite (148 tests)
.\gradlew :app:connectedDebugAndroidTest     # instrumented suite (58 tests; needs emulator)
                                             # NOTE: the suite UNINSTALLS the debug app at the end

# devices / emulators
C:\Users\sokar\AppData\Local\Android\Sdk\platform-tools\adb.exe devices
C:\Users\sokar\AppData\Local\Android\Sdk\emulator\emulator.exe -list-avds
C:\Users\sokar\AppData\Local\Android\Sdk\emulator\emulator.exe -avd ayah_avd

# app lifecycle
$adb = "C:\Users\sokar\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
& $adb shell am start -n com.ayah.quran.debug/com.ayah.quran.MainActivity
& $adb shell am force-stop com.ayah.quran.debug
& $adb uninstall com.ayah.quran.debug          # clean state (Quran DB re-imports on next launch)

# screenshots
& $adb exec-out screencap -p > screenshot.png
& $adb shell screencap -p /sdcard/s.png ; & $adb pull /sdcard/s.png .

# UI inspection
& $adb shell uiautomator dump /sdcard/dump.xml ; & $adb pull /sdcard/dump.xml .
# ⚠ Git Bash escapes /sdcard → use //sdcard inside Git Bash

# logs
& $adb logcat -d --pid=<pid>          # app-only logs
& $adb logcat -d -s QuranAudio:*      # app-specific tags:
#   QuranAudio (downloads), AdhanDownload, AppUpdate, PrayerAlarm,
#   MediaSessionService / ExoPlayerImplInternal (playback errors)

# app data reset (keeps app installed)
& $adb shell pm clear com.ayah.quran.debug
```

## Emulator quirks learned the hard way

- After `connectedDebugAndroidTest`, **the debug app is uninstalled** —
  reinstall before any manual session.
- `input text` cannot type Arabic; drive Arabic UI by coordinates from
  `uiautomator dump` instead.
- `uiautomator` attribute order puts `enabled`/`clickable` BEFORE `bounds`
  — write regexes accordingly.
- Screenshots may capture the launcher if `am start` races — verify the
  foreground package from the dump before saving.
- `/sdcard/...` paths get Git-Bash path-mangled (`C:/Program Files/Git/...`);
  double the slash (`//sdcard/...`) inside Git Bash.
