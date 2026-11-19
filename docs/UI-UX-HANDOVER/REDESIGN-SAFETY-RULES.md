# Redesign Safety Rules (binding)

## You MAY redesign

Layouts, visual hierarchy, colors, typography, icons, cards, navigation
UI, buttons, animations, visual states, spacing, component styling,
screen compositions, empty/loading/error presentations, the design-system
layer itself (`core/designsystem/`), and — carefully — UI architecture
(e.g., converting a `Column+verticalScroll` list to `LazyColumn`).

## You MUST preserve (verified behaviors)

**Quran integrity**
- Tanzil Uthmani text (`assets/quran/quran-uthmani.txt`) — byte-identical
- The 604-page mapping and `PageForAyahUseCase` resolution
- Surah boundaries, juz metadata, global ayah numbering (Hafs 1..6236)
- `MushafText`'s AnnotatedString + character-range tap mapping

**Audio**
- Per-ayah playlist playback (`ExoPlayerAyahPlaylistPlayer` + QuranPlaybackService)
- MediaSession notification/lock-screen controls, background/screen-off playback
- Audio focus behavior (calls pause+resume), adhan-wins conflict policy
- Downloads (per-ayah files, resumable n/total), offline playback paths
- Playback restoration (resume from saved ayah — never auto-play)
- Sleep timers (after-ayah / after-surah / minutes), repeat ayah/range
- Reader sync: highlight + auto page-follow + continuous auto-scroll

**Prayers & adhan**
- Calculation stack (adhan-java, offline), city data, adjustments
- Exact-alarm scheduling, boot re-arm, channels, notifications
- AdhanPlaybackService + adhan-wins priority over Quran audio
- Honest permission statuses (never fake "مفعّل")

**Data & platform**
- Room schema/migrations, DataStore keys, SharedPreferences restore keys
- Wallpapers engine (renderer, rotation WorkManager, flag combos)
- Localization (Arabic default + English switch), RTL correctness
- In-app updater (GitHub Releases → OkHttp → FileProvider → installer)
- All existing permissions and their onboarding/settings flows

## Hard prohibitions

1. No mock/placeholder data replacing working flows.
2. No decorative buttons without real behavior.
3. No removal of a feature because the new design finds it inconvenient.
4. No breaking offline functionality (every feature must work offline
   except downloads/update checks, which degrade honestly).
5. No Quran-text or page-mapping edits (see QURAN-READER.md).
6. No version bumps of AGP/Kotlin/Compose as a side effect of redesign.
7. Do not delete or weaken the instrumented tests; update their matchers
   when you change strings/content-descs, and keep the
   `clearRestore()` @Before hooks.
8. Do not drop honest empty/error states — they are a product value.
9. Arabic-first RTL must hold on every new screen (test Arabic FIRST).
10. New dependencies require justification (Compose/M3 first).

## Redesign verification checklist (per screen)

Build debug → install → walk the screen in Arabic AND English → exercise
every interaction the old screen had → check instrumented suite → only
then move on. Reference: NEXT-AGENT-WORKFLOW.md.
