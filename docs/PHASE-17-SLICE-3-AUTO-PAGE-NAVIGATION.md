# Phase 17 — Slice 3: Automatic Mushaf Page Navigation During Recitation

**Status**: implemented, emulator-verified, physical-device testing pending
**Build**: v1.0.0-beta.6 (versionCode 7)
**Preserves**: Slice 1 (catalog/downloads) and Slice 2 (service/background/
mini-player/reader-actions) untouched except the additive follow loop below.

---

## 1. Root cause of the missing synchronization

The mushaf pager moved ONLY on user gestures, the jump dialog, and the
initial `startPage` resolution. Nothing connected the recited ayah
(`AyahPlaybackState`, whose playlist transitions ARE the ayah sync) to the
displayed page. The highlight already followed the recited ayah (slice 2),
but a highlight on an off-screen page is invisible — hence the report.

## 2. Implementation

- **`MushafFollowPolicy`** (`ui/reader/`, pure, 19 JVM tests): decides
  navigate/stay from (displayed page, recited page, recited ayah key,
  last-followed key, user-selection flag). Mapping stays 100% in
  `PageForAyahUseCase` — the authentic 604-page source of truth is REUSED,
  never recalculated; no page boundaries are fabricated.
- **`MushafPageViewModel.evaluateRecitationFollow(displayedPage)`**:
  resolves the recited ayah's page through the mapping, runs the policy,
  records the followed key. Exposes `recitedAyah` and
  `userSelectionActive` flows for the screen effect.
- **`MushafPageReaderScreen`**: `LaunchedEffect(recitedAyah, settledPage,
  userSelectionActive)` → on a policy-approved target: `ensureLoaded`
  (prevents blank-page flash) then `pagerState.animateScrollToPage` — one
  smooth animation per recited-ayah advance.

## 3. Synchronization policy (spec §3/§4)

1. Follow only while a recitation session is active (paused included — a
   seek while paused is explicit intent).
2. Navigate only when the displayed page differs from the recited ayah's
   page.
3. **One navigation per recited ayah**: the policy tracks the last ayah it
   navigated for; while the SAME ayah recites/repeats, manual page
   browsing is never yanked back. Following resumes exactly when playback
   ADVANCES to a new ayah whose page differs from the displayed one.
4. If the user manually lands on the recited page → stable (same-page
   no-op) and the key is adopted, so later browsing away is not undone for
   the same ayah.
5. A live user ayah-selection (tap) pauses following while it lasts; the
   selection EXPIRES when playback advances to a different ayah — the
   intent applied to the ayah they tapped, then recitation takes back the
   reader.

### Bug found during verification (and fixed)

The first wiring used `highlight != null` as "user selection active". The
navigation-arg highlight ("continue reading" → `argSurahId/argAyah`) is
NEVER cleared — it permanently suppressed following (emulator caught this:
ayah 13 advanced, page stayed 562). Fixed by tracking the user's
selection separately (`_userSelection`), set only by real taps and
expired on ayah advance; the arg-highlight remains display-only.

## 4. Playback state handling (spec §5)

Play / pause / resume / next / previous / seek / reciter switch /
repeat-one / repeat-range / sleep-after-ayah / sleep-after-surah / sleep
minutes / restore-resume / surah switch all funnel through the SAME
`AyahPlaybackState` transitions — the follow loop reacts to the recited
ayah key only, so every path above works identically. Restore-resume
navigates on its first evaluation (no fabricated timestamps; ayah-level
sync only, as in slices 1–2).

## 5. Reader modes (spec §6)

- **Mushaf page mode**: automatic flipping (this slice).
- **Continuous reader**: slice-2 auto-scroll unchanged (same mechanism,
  same tests, no regression).

## 6. Emulator verification (ayah_avd, API 36)

Recited سورة الملك (30 ayahs, pages 562–564) from ayah 1:

| Scenario | Result |
|---|---|
| Ayah progression within one page (1→12) | ✅ stays on 562, no flicker |
| Page-boundary advance (ayah 13 → page 563) | ✅ automatic flip 562→563 |
| Multi-page seek while reciting | ✅ navigates to recited page |
| Manual flip away (2 pages) while same ayah recites | ✅ NO yank-back |
| Follow resumes when playback advances | ✅ ayah 28 → page 564 |
| Loop prevention (re-evaluations, same ayah) | ✅ page stable |
| Highlight keeps following | ✅ recited ayah highlighted on each page |
| Mapping correctness | ✅ flip exactly at the authentic 562/563 boundary |

Continuous-reader auto-scroll regression: covered by the unchanged
slice-2 code path + suites below.

## 7. Physical device (PENDING — owner, HONOR 600 / MagicOS)

- [ ] Page turns during screen-off listening → screen on shows right page
- [ ] Smoothness of `animateScrollToPage` on the real device (animation
      duration/perception can only be judged physically)
- [ ] Battery impact of the follow loop during long sessions
- [ ] MagicOS background restrictions interplay (service already survives)

## 8. Automated suites

- JVM: **148 tests, 0 failures** (adds 19 `MushafFollowPolicy` tests:
  same-page, next-page, multi-page, surah transitions, juz transitions,
  seek, previous-ayah across boundary, repeat-one, repeat-range across
  boundary, pause/resume, restore, manual navigation, loop prevention,
  user-selection expiry, degraded inputs).
- Instrumented: full suite run at commit (see commit message).

## 9. Known limitations

- Page-turn uses the shared pager animator; a 10-page jump animates
  through intermediate pages (still a single smooth motion).
- Following requires the reader to be OPEN (by design — the reader is the
  visual surface; background listening needs no page logic).
