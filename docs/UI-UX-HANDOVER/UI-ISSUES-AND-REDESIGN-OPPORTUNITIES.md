# UI Issues & Redesign Opportunities (objective audit, emulator + code)

Severity: 🔴 high (blocks/annoys users) · 🟡 medium (visible roughness) ·
🟢 low (polish). All findings from the actual emulator tour and code; none
are speculative.

## Home (الرئيسية)

| Issue | Severity | Type | Direction |
|---|---|---|---|
| Dashboard is card-stack heavy: onboarding card, continue-reading, wallpapers hint stacked with no hierarchy | 🟡 | visual | priority card + condensed secondary cards |
| "افتح أي سورة" helper card duplicates the Quran tab entry | 🟢 | clutter | merge into continue-reading card |
| Continue-reading card shows full "سورة X · الآية Y من Z" only after a position exists; empty state is plain | 🟢 | polish | hero-ize continue reading with mushaf preview art |

## Quran list

| Issue | Severity | Type | Direction |
|---|---|---|---|
| Flat text list, no juz/page metadata visible until tap | 🟡 | hierarchy | add page-count/juz chips or mushaf-page preview |
| Search field styling is bare | 🟢 | polish | outlined search with clear button |

## Mushaf reader (functional core — chrome only)

| Issue | Severity | Type | Direction |
|---|---|---|---|
| Top bar has 5+ icon actions (back/bookmark/play/continuous/auto…) — icons-only, discoverability low | 🟡 | usability | group secondary actions into overflow/bottom bar |
| Highlight color contrast on busy mushaf pages | 🟡 | visual | translucent wash + accent border |
| No page-transition preview of neighbors (loads ±2 only) | 🟢 | perf-ux | acceptable; prefetch window could widen on fast devices |

## Audio tab

| Issue | Severity | Type | Direction |
|---|---|---|---|
| 114 rows rendered in a plain Column inside verticalScroll (no lazy virtualization; all rows composed) | 🔴 | functional-perf | convert to LazyColumn (pure UI change) |
| Reciter chips row overflows without hint that it scrolls | 🟡 | usability | edge-fade gradient + scroll indicator |
| Download progress shows "n/total" text but no per-row bar | 🟢 | polish | thin LinearProgressIndicator under the row |
| Legacy Phase-5 playback panel still rendered below the real catalog (state rows duplicated) | 🔴 | inconsistency | remove/merge the legacy panel (it never activates) |

## Now Playing / mini player

| Issue | Severity | Type | Direction |
|---|---|---|---|
| No seek bar on Now Playing (position APIs exist: `positionMs/durationMs`) | 🟡 | functional | add Slider wired to controller position |
| Sleep-timer active state has no countdown display | 🟢 | polish | badge with remaining time |
| Mini player restore hint text is plain | 🟢 | polish | accent + artwork placeholder |

## Prayer times / settings

| Issue | Severity | Type | Direction |
|---|---|---|---|
| Settings screen is ONE long scroll with 8+ sections (location, methods, adjustments, adhan, files, dashboard, diagnostics, advanced) | 🟡 | hierarchy | tabbed/sub-screen settings |
| Next-prayer countdown not highlighted as hero element | 🟡 | hierarchy | hero card with time-to-next |
| Onboarding is a long single scroll of 5 steps | 🟢 | polish | pager-style steps |

## Localization/RTL

| Issue | Severity | Type | Direction |
|---|---|---|---|
| Mixed Arabic-Indic and Western digits in some rows (e.g. "آية 3" vs "3:30") | 🟢 | consistency | localize digit sets |
| Some English strings are literal translations ("وضع النص المتغير") | 🟢 | polish | copy pass |

## Known fixed during slice work (do not reintroduce)

- Vertical chip squeeze on real devices (use LazyRow for chip rows).
- Non-scrolling fixed-card Columns overflow on smaller/zoomed screens
  (always `verticalScroll` fixed lists).
- Dead flows: UI collecting locally-created StateFlows → screens appear
  frozen. Always consume the ViewModel/repository flow instances.
- res/raw audio compression (noCompress) — never add audio to res/raw
  without updating noCompress.

## Functional (non-UI) issues tracked elsewhere

Not part of the redesign: MagicOS alarm delivery limitation (external),
page-mode auto page-turn absent in continuous mode's mushaf jump (by
design), wallpaper engine edge cases documented in
`wallpaper-media3-gotchas`.
