# Ayah — Phase 6 Report (Release Preparation & Production Readiness)

Date: 2026-11-17 · Status: **release-ready pending physical-device testing
and owner actions**

## 1. Release signing

### Configuration
- Development keystore generated: `keystore/ayah-release.keystore` (RSA
  4096, SHA384withRSA, 10 000-day validity, alias `ayah-release`).
- Keystore directory and credentials are **gitignored** (`.gitignore`
  includes `keystore/`, `*.keystore`, `*.jks`, `keystore.properties`).
- Credentials stored in `local.properties` (gitignored) under
  `ayah.release.*` keys.
- `app/build.gradle.kts` reads credentials from `local.properties` using
  `rootProject.file()` (resolves correctly from the `:app` module).
- If no keystore is configured, the release build falls back to unsigned.
- **The development keystore is for testing/CI only.** The owner must
  generate a production keystore with a strong password before Play
  submission. The keystore backup procedure is documented in
  RELEASE-CHECKLIST.md.

### Verification
| Artifact | Size | Signing verified |
|---|---|---|
| `app-release.apk` | 4.19 MB | ✅ apksigner v2 scheme, 1 signer |
| `app-release.aab` | 5.30 MB | ✅ signed (AABs are re-signed by Google Play) |
| `mapping.txt` | 39.9 MB | ✅ preserved for crash de-obfuscation |

## 2. Version information

| Field | Value |
|---|---|
| applicationId | `com.ayah.quran` |
| versionCode | 1 |
| versionName | 0.1.0 |
| minSdk | 29 (Android 10) |
| targetSdk | 36 |
| compileSdk | 36 |
| R8 minify | enabled |
| Resource shrinking | enabled |

## 3. Regression results

| Suite | Tests | Result |
|---|---|---|
| `testDebugUnitTest` | 46 | ✅ 0 failures |
| `connectedDebugAndroidTest` | 43 | ✅ 0 failures |
| Total | **89** | ✅ **0 failures** |
| Quran dataset integrity | ✅ | 114/6236/30/604, cross-check identical |
| No Quran text modified | ✅ | byte-for-byte verbatim enforced |
| Room migrations | ✅ | v3 destructive (pre-release policy) |
| Wallpaper history bounded | ✅ | 100-entry ring, trimmed on insert |
| Scheduling correct | ✅ | UPDATE policy, battery-not-low |
| No fabricated states | ✅ | UI tests assert zero Playing while idle |

## 4. Device testing matrix

No physical device was available in this environment. All testing was
performed on `ayah_avd` (Pixel 6, API 36, x86_64).

**Physical-device testing is a REQUIRED owner action before submission.**
The complete checklist is in RELEASE-CHECKLIST.md.

Recommended test devices (when available):
| Priority | Device | Android | OEM skin |
|---|---|---|---|
| 1 | Any Samsung Galaxy | 13–14 | One UI |
| 2 | Any Xiaomi/Redmi | 12–14 | MIUI |
| 3 | Google Pixel | 14–16 | Stock |

## 5. Accessibility findings

| Finding | Status |
|---|---|
| Content descriptions on meaningful icons | ✅ Fixed |
| Decorative icons marked `null` | ✅ Fixed |
| Heading semantics on section titles | ✅ Fixed |
| Status text + icon (never color alone) | ✅ Fixed |
| Bottom-nav descriptions on unmerged tree | ✅ Fixed (Phase 4) |
| Large font 2.0× verified | 📱 Emulator ✓ / Device pending |
| TalkBack walkthrough | 📱 Device pending |
| Contrast audit with tool | ⬜ Pending (manual measurement recommended) |
| Touch targets ≥ 48dp | ✅ Material 3 default |

## 6. Performance findings

| Measurement | Result | Notes |
|---|---|---|
| Cold-start (emulator) | ~2s | Import + first render; physical timing needed |
| Import (6,236 verses) | ~1.2s | Room transaction, validated before write |
| Wallpaper render (720×1520) | <100ms | Canvas + StaticLayout |
| Wallpaper render (1080×2400) | <200ms | Screen-dims apply |
| Thumbnail cache | bounded 20 | Per-entry id, in-memory |
| History bounded | 100 entries | Trimmed on insert |
| Main-thread blocking | None | Import/render/scheduling off main |
| Memory leaks | None observed | Logcat clean after force-stop |
| WorkManager overhead | Minimal | Battery-not-low, 60-min interval default |
| Recomposition audit | Acceptable | StateFlow + distinctUntilChanged on hot paths |

## 7. Permission audit

| Permission | Source | Feature | Runtime prompt? |
|---|---|---|---|
| `SET_WALLPAPER` | Manifest (explicit) | Wallpaper engine | No (normal) |
| `FOREGROUND_SERVICE` | WorkManager manifest merge | WorkManager | No (normal) |
| `RECEIVE_BOOT_COMPLETED` | WorkManager manifest merge | Scheduled work across reboot | No (normal) |
| `ACCESS_NETWORK_STATE` | WorkManager manifest merge | Constraint checking | No (normal) |
| `WAKE_LOCK` | WorkManager manifest merge | Work execution wake lock | No (normal) |

No dangerous permissions are declared. No runtime permission prompts are
required. No INTERNET permission is declared (the app is fully offline).

## 8. Privacy review

**The app collects, stores, transmits, and shares zero user data.**

Evidence:
1. **No INTERNET permission** is declared in the merged manifest — the app
   physically cannot communicate with any server.
2. **No third-party SDKs** are included (no analytics, no advertising, no
   crash reporting, no tracking).
3. **All data storage is local** (Room + DataStore + cache files).
4. **The only bundled network endpoint reference** is the documentation
   attribution URL (tanzil.net) — displayed as text, never accessed.
5. **The Play Console Data Safety form** should declare "No data collected,
   no data shared" with high confidence — supported by the manifest audit
   and source code review above.

A draft privacy policy reflecting this behavior is in
`docs/PRIVACY-POLICY-DRAFT.md`. The owner must review, approve, host
publicly, and match the Play Console Data Safety form.

## 9. Play Store readiness

| Item | Status | Notes |
|---|---|---|
| Signed AAB ready | ✅ | 5.30 MB |
| targetSdk meets Play requirements | ✅ | targetSdk 36 (2026 requirement) |
| App signing | ✅ | Dev keystore (owner replaces for production) |
| Privacy policy URL | 🔑 Owner | Draft in `docs/PRIVACY-POLICY-DRAFT.md` |
| Data Safety form | 📋 Play | "No data collected" — evidence above |
| Content rating | 📋 Play | Expected Everyone (PEGI 3) |
| Store listing content | ✅ Draft | `docs/STORE-LISTING.md` |
| Store screenshots | ✅ Captured | 7 release screenshots |
| Feature graphic (1024×500) | ⬜ Owner | Design using Emerald/Gold branding |
| Physical device testing | 🔑 Owner | REQUIRED before submission |
| Production keystore | 🔑 Owner | Replace development keystore |

## 10. Store assets status

| Asset | Status | File/Notes |
|---|---|---|
| App icon (adaptive) | ✅ | `ic_launcher.xml` (8-point star, gold on emerald) |
| Phone screenshots | ✅ Captured | 7 release screenshots (1080×2400) |
| Feature graphic (1024×500) | ⬜ Owner | Design needed |
| English title | ✅ Draft | "Ayah: Quran & Live Wallpapers" |
| English short desc | ✅ Draft | "Read the Holy Quran offline with beautiful Uthmani text and Quranic live wallpapers." |
| English full desc | ✅ Draft | `docs/STORE-LISTING.md` |
| Arabic title | ✅ Draft | "آية: القرآن الكريم والخلفيات المتغيرة" |
| Arabic short desc | ✅ Draft | `docs/STORE-LISTING.md` |
| Arabic full desc | ✅ Draft | `docs/STORE-LISTING.md` |
| Category | ✅ Draft | Books & Reference |
| Keywords | ✅ Draft | `docs/STORE-LISTING.md` |

## 11. Known limitations

1. No physical device testing performed (no device available in this
   environment) — the complete checklist is provided for the owner.
2. The development keystore must be replaced with a production keystore.
3. The privacy policy is a draft — the owner must review, approve, host,
   and reference in the Play Console.
4. The feature graphic (1024×500) is not generated — the owner should
   create it using the existing branding.
5. TalkBack walkthrough and contrast-tool audit require physical hardware.
6. Playback start verification is limited on headless emulators (READY but
   not PLAYING when the audio sink can't start). Physical device check
   needed.
7. The audio catalog is empty (no licensed recitation assets yet) — by
   design; the architecture is ready.

## 12. Owner actions required before submission

1. Generate a production keystore (strong password, stored securely).
2. Update `local.properties` with production signing credentials.
3. Perform physical-device testing using the checklist.
4. Review, approve, and host the privacy policy.
5. Create a 1024×500 feature graphic.
6. Create a Google Play Console developer account ($25 one-time).
7. Upload the signed AAB, complete Data Safety, content rating, and
   store listing.
8. Choose a distribution track (internal → closed → production).
