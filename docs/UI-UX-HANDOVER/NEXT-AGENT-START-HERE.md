# NEXT AGENT — START HERE

You are taking over the **UI/UX redesign** of Ayah, a production Quran +
wallpapers Android app (Kotlin / Jetpack Compose / Material 3). The app is
fully functional across 17 development phases. Your job is visual/UX
redesign **while preserving every behavior**.

## 1. What this app is

- Arabic-first Quran app (Arabic is the DEFAULT locale; English switchable
  in-app — the activity recreates on switch).
- Five bottom tabs (RTL order right→left): الرئيسية (Home) · القرآن (Quran)
  · الصلوات (Prayers) · الخلفيات (Wallpapers) · الصوت (Audio) · الإعدادات
  (Settings).
- Working features you MUST keep working: mushaf page reader (authentic
  604-page mapping), continuous reader, per-ayah recitation playback with
  background Media3 service + notification + lock-screen controls, per-ayah
  offline downloads, ayah highlight + auto page-follow, sleep timers,
  repeat modes, prayer times with adhan (adhan ALWAYS pre-empts Quran
  audio), adhan voice library with downloads, wallpaper engine with
  rotation, permissions onboarding, in-app updater via GitHub Releases.

## 2. First 30 minutes

```powershell
cd C:\AYAH
git status                                # clean tree expected
.\gradlew :app:assembleDebug              # must succeed (~2 min)
C:\Users\sokar\AppData\Local\Android\Sdk\platform-tools\adb.exe devices
# start the emulator if not running:
C:\Users\sokar\AppData\Local\Android\Sdk\emulator\emulator.exe -avd ayah_avd &
C:\Users\sokar\AppData\Local\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
C:\Users\sokar\AppData\Local\Android\Sdk\platform-tools\adb.exe shell am start -n com.ayah.quran.debug/com.ayah.quran.MainActivity
```

Then read, in order: `DEVELOPMENT-ENVIRONMENT.md`,
`SCREEN-INVENTORY.md`, `REDESIGN-SAFETY-RULES.md`.

## 3. The three landmines (read before touching anything)

1. **Quran reader** — the text rendering, 604-page mapping and ayah
   coordinates are load-bearing. Redesign chrome around them, never the
   text pipeline. See `QURAN-READER.md`.
2. **Audio state plumbing** — UI screens are thin over
   `ExoPlayerAyahPlaylistPlayer` (MediaController client) +
   `QuranAudioDownloadRepository`. Multiple UI bugs in the past were
   "dead flows" — a screen collecting a locally-created StateFlow instead
   of the real one. When you rewire screens, keep collecting the SAME
   flows. See `AUDIO-PLAYER.md`.
3. **Instrumented tests assert the CURRENT UI** —
   `AudioTabUiTest`, `MushafPageReaderUiTest`, `AppUiTest`,
   `PrayerPermissionsInstrumentedTest` match real strings and content-descs.
   If you change visible strings or content-descs, update these tests in
   the same change. They also reset persisted playback state in `@Before`
   (keep that hook).

## 4. Non-negotiables

- Arabic-first RTL: every screen must work in Arabic by default.
- Never modify: Quran text assets (`assets/quran/`), the 604-page mapping
  (`PageForAyahUseCase` path), prayer calculation, adhan scheduling/priority.
- No fake UI: every button must do something real. Honest empty/error
  states are a project value (see `EmptyState` usage).
- Full rule list: `REDESIGN-SAFETY-RULES.md`.

## 5. Where the UI lives

All screens: `app/src/main/java/com/ayah/quran/ui/**` (38 Kotlin files).
Design system: `app/src/main/java/com/ayah/quran/core/designsystem/`
(theme: Color/Type/Spacing/Theme; components: AyahCard, SectionCard,
EmptyState, ConfirmDialog). Navigation: `ui/navigation/AyahApp.kt`.
Strings: `res/values/strings.xml` (English) + `res/values-ar/strings.xml`
(Arabic, primary).
