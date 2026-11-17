# Ayah — Final Release Checklist

Every item classified as:
- ✅ **Completed** — done by the Agent, verified
- 📱 **Emulator** — verified on emulator
- 📱 **Device** — requires physical device
- 🔑 **Owner** — requires owner credentials or approval
- 📋 **Play** — requires Google Play Console action

---

## Build & Signing

- [x] ✅ Release keystore generated (RSA 4096, 10 000 days)
- [x] ✅ Keystore gitignored (`keystore/`, `*.keystore`, `*.jks`)
- [x] ✅ Signing config reads from local.properties (no hardcoded secrets)
- [x] ✅ Signed APK verified (apksigner v2, 1 signer)
- [x] ✅ Signed AAB generated
- [x] ✅ SHA-256 checksums recorded
- [x] ✅ Certificate fingerprint recorded (SHA-256: 90:FA:D9:...)
- [ ] 🔑 Replace development keystore with production keystore
- [ ] 🔑 Store production keystore password securely (password manager)
- [ ] 🔑 Enroll in Google Play App Signing (upload key + app signing key)

## Build & Dependencies

- [x] ✅ assembleDebug passes
- [x] ✅ assembleRelease passes (R8 + shrinkResources)
- [x] ✅ bundleRelease passes (AAB)
- [x] ✅ No debug suffix in release build
- [x] ✅ No debug-only functionality exposed
- [x] ✅ All dependencies at latest stable versions
- [x] ✅ Version catalog verified (AGP 8.13.2, Kotlin 2.3.21, etc.)

## Quran Engine

- [x] ✅ Dataset verified: 114 surahs, 6,236 ayahs, cross-checked
- [x] ✅ Import: validate-before-write, transactional, idempotent
- [x] ✅ Integrity tests: 10 JVM tests (structure + cross-check)
- [x] ✅ No Quran text modified (byte-for-byte enforced)
- [x] ✅ Surah list: all 114 surahs, Amiri Arabic names
- [x] ✅ Surah search (Arabic/transliterated/English)
- [x] ✅ Reader: RTL Uthmani text, diacritics, no clipping
- [x] ✅ Reader: prev/next surah navigation
- [x] ✅ Bismillah display convention (dataset-derived)
- [x] ✅ Bookmarks (save/remove/list/open)
- [x] ✅ Reading position (save/restore across restart)

## Wallpaper Engine

- [x] ✅ 5 premium templates (structured data, render-verified)
- [x] ✅ WallpaperRenderer (Canvas/Bitmap, shrink-fit, safe areas)
- [x] ✅ AyahSelector (random/sequential/daily/surah/ayah + avoidance)
- [x] ✅ WallpaperManager (FLAG_SYSTEM + FLAG_LOCK)
- [x] ✅ WorkManager scheduling (unique periodic, 30m–24h)
- [x] ✅ Wallpaper history (bounded 100, thumbnails, reapply, delete)
- [x] ✅ Clear history with confirmation dialog
- [x] ✅ Apply confirmed after Android accepts (no fake success)
- [x] ✅ Preview (same renderer as final wallpaper)
- [x] 📱 Physical device: Home screen wallpaper visual
- [x] 📱 Physical device: Lock screen wallpaper visual
- [x] 📱 Physical device: Automatic wallpaper cycle timing
- [x] 📱 Physical device: Battery optimization behavior

## Audio Foundation

- [x] ✅ Media3/ExoPlayer 1.11.1 architecture
- [x] ✅ Honest empty catalog (no fabricated reciters)
- [x] ✅ Playback controls disabled without playable source
- [x] ✅ No fabricated Playing state
- [x] ✅ Repeat/speed preferences persisted (DataStore)
- [x] ✅ MediaSessionService deferred (documented rationale)
- [ ] 🔑 License a reciter's catalog (verify redistribution rights)
- [ ] 🔑 Implement non-empty AudioCatalogSource
- [ ] 🔑 Wire reciter selection to persisted selectedReciterId

## UI / UX

- [x] ✅ Home: live Continue Reading + wallpaper thumbnail card
- [x] ✅ Immersive full-screen preview (chrome hidden)
- [x] ✅ 5-tab bottom navigation with state preservation
- [x] ✅ Navigation transitions (fade + slide, reduced-motion aware)
- [x] ✅ Light / Dark / System themes (persisted)
- [x] ✅ Amiri Arabic typography
- [x] ✅ Emerald / Gold / Cream / Ink design system
- [x] ✅ Splash screen (core-splashscreen)
- [x] ✅ Adaptive launcher icon (8-point star)
- [x] ✅ FlowRow chip layout (no overflow)
- [x] ✅ History thumbnails (cached, bounded)

## Testing

- [x] ✅ 46 JVM unit tests, 0 failures
- [x] ✅ 43 instrumented tests, 0 failures
- [x] ✅ Quran integrity tests (10 JVM: structure + cross-check)
- [x] ✅ ExoPlayer state mapping tests (8 instrumented)
- [x] ✅ Compose UI tests (7: nav, sections, a11y)
- [x] ✅ DAO tests (4 instrumented)
- [x] ✅ Import tests (2 instrumented)
- [x] ✅ No tests weakened or deleted
- [ ] 📱 Physical device: full regression on 2+ devices

## Accessibility

- [x] ✅ Content descriptions on meaningful icons
- [x] ✅ Decorative icons marked `null`
- [x] ✅ Heading semantics
- [x] ✅ Status text + icon (never color alone)
- [x] ✅ Material 3 touch targets (≥48dp)
- [x] ✅ Reduced-motion support
- [x] ✅ RTL layout (Arabic)
- [ ] 📱 TalkBack walkthrough (physical device)
- [ ] ⬜ Contrast ratio measurement (tool-based audit)

## Privacy & Security

- [x] ✅ No INTERNET permission declared
- [x] ✅ No third-party SDKs
- [x] ✅ No analytics / ads / crash reporting / tracking
- [x] ✅ No secrets in git (verified with git diff --cached -S)
- [x] ✅ Privacy policy draft (docs/PRIVACY-POLICY-DRAFT.md)
- [x] ✅ Permission audit (5 permissions, all accounted for)
- [ ] 🔑 Owner: review and approve privacy policy
- [ ] 🔑 Owner: host privacy policy at a public URL
- [ ] 🔑 Owner: complete Play Console Data Safety form

## Play Store

- [x] ✅ Signed AAB (5.30 MB)
- [x] ✅ targetSdk 36 (meets Play requirements)
- [x] ✅ Store listing content drafted (EN + AR)
- [x] ✅ Store screenshots captured
- [x] ✅ Release checklist documented
- [ ] 🔑 Owner: Google Play Console developer account ($25)
- [ ] 🔑 Owner: Upload signed AAB
- [ ] 🔑 Owner: Complete Data Safety form
- [ ] 🔑 Owner: Complete content rating questionnaire
- [ ] 🔑 Owner: Set privacy policy URL
- [ ] 📋 Play: Review and approve submission
- [ ] 📋 Play: Choose distribution track (internal → closed → production)
- [ ] ⬜ Feature graphic (1024×500) — owner deliverable

## Documentation

- [x] ✅ README.md
- [x] ✅ docs/ARCHITECTURE.md
- [x] ✅ docs/QURAN_DATA.md
- [x] ✅ docs/LICENSES.md
- [x] ✅ docs/STORE-LISTING.md
- [x] ✅ docs/PRIVACY-POLICY-DRAFT.md
- [x] ✅ docs/reports/PHASE-1.md through PHASE-7.md
- [x] ✅ docs/reports/RELEASE-CHECKLIST.md
- [x] ✅ docs/reports/FINAL-RELEASE-CHECKLIST.md
- [x] ✅ docs/reports/BETA-TESTING-GUIDE.md

## Screenshots

- [x] ✅ Phase 1–4 screenshots (emulator)
- [x] ✅ Phase 5 audio tab + large font
- [x] ✅ Phase 6/7 release screenshots (beta build)
- [ ] 📱 Physical device screenshots (various screen sizes)
