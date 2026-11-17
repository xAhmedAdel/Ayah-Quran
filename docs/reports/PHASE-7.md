# Ayah — Phase 7 Report (Beta Release, Final QA & Launch Readiness)

Date: 2026-11-17 · Status: **beta release candidate pushed to GitHub —
pending physical-device testing and owner actions**

## 1. Release artifacts

| Artifact | Size | SHA-256 |
|---|---|---|
| AAB (`app-release.aab`) | 5.30 MB | `639574514bd1f40efe7bf3eae71ba9da4fa9a1834956ead56f8f5c6962ca7fa7` |
| APK (`app-release.apk`) | 4.19 MB | `9765d4cf4eba775b1fe6080f34a1e49d2b4bb46cba0ea9b0d28e8a1b8b65862d` |

| Field | Value |
|---|---|
| applicationId | `com.ayah.quran` |
| versionCode | 2 |
| versionName | 1.0.0-beta.1 |
| minSdk | 29 (Android 10) |
| targetSdk | 36 |
| Signature | v2 scheme, RSA 4096, verified with apksigner |

Certificate SHA-256 fingerprint:
`90:FA:D9:49:D8:7C:3F:75:24:99:98:37:8A:74:E7:10:9C:D8:8A:08:44:6E:60:69:38:26:3B:0D:E9:78:3E:58`

## 2. Signing verification

| Scheme | Verified |
|---|---|
| v1 (JAR) | false |
| **v2 (APK Signature Scheme v2)** | **true** |
| v3 | false |
| v4 | false |

- Development keystore used (gitignored, credentials in local.properties).
- **No secrets committed to git**: verified with `git diff --cached -S`
  for credential values; the build script reads from Properties at build
  time; `local.properties` and `keystore/` are gitignored.
- Production upload key: the owner must generate a separate production
  keystore before Play submission. This development keystore is for
  beta/CI only.
- **Upload key vs app-signing key**: the upload key signs the AAB/APK
  before upload to Play. Google Play then re-signs with the app-signing
  key (which Play manages). The owner enrolls in Play App Signing on
  first AAB upload — the upload key certificate fingerprint
  (`90:FA:D9:...`) is what Play records as the upload certificate.

## 3. Git secrets audit

| Check | Result |
|---|---|
| Keystore files tracked? | ✅ No (gitignored) |
| local.properties tracked? | ✅ No (gitignored) |
| Credential values in staged content? | ✅ No (verified with `git diff --cached -S`) |
| `.gitignore` covers keystore/, *.keystore, *.jks, local.properties | ✅ |
| Git repository | ✅ https://github.com/xAhmedAdel/Ayah-Quran (main + tag v1.0.0-beta.1) |

## 4. Regression results

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
| No fabricated playback | ✅ | UI test asserts zero Playing while idle |

## 5. Emulator verification (beta build)

| Check | Status |
|---|---|
| Signed beta APK installs | ✅ |
| versionCode=2, versionName=1.0.0-beta.1 confirmed | ✅ |
| Launches (topResumedActivity) | ✅ |
| All 5 tabs navigate | ✅ |
| 5 beta screenshots captured | ✅ (`beta-*.png`) |

## 6. Physical device testing

**NOT PERFORMED** — no physical device available in this environment.
The complete 22-item checklist is in `RELEASE-CHECKLIST.md`.

## 7. Beta testing guide

`docs/reports/BETA-TESTING-GUIDE.md` — installation instructions,
feature test plan, known limitations, and a structured bug-report
template. No analytics or tracking SDKs added.

## 8. Privacy & store review

- Privacy policy draft: `docs/PRIVACY-POLICY-DRAFT.md` (owner must
  review, approve, host, and reference in Play Console).
- Manifest audit: 5 permissions (SET_WALLPAPER + 4 WorkManager-infrastructure),
  none dangerous, no runtime prompts, no INTERNET.
- No third-party SDKs, no analytics, no ads, no crash reporting.
- Data Safety form: "No data collected, no data shared" — supported by
  the manifest audit (no INTERNET permission) and source code review.

## 9. Store assets status

| Asset | Status |
|---|---|
| App icon (adaptive) | ✅ |
| Phone screenshots | ✅ 7 release + 5 beta |
| Feature graphic (1024×500) | ⬜ Owner |
| English listing | ✅ Draft (`docs/STORE-LISTING.md`) |
| Arabic listing | ✅ Draft (`docs/STORE-LISTING.md`) |
| Privacy policy URL | 🔑 Owner |

## 10. Known limitations

1. No physical device testing performed.
2. Development keystore used (owner replaces for production).
3. Privacy policy is a draft (owner approves and hosts).
4. Feature graphic not generated.
5. Audio catalog empty (by design).
6. TalkBack walkthrough requires physical device.
7. Playback start verification limited on headless emulators.

## 11. Final release recommendation

**CONDITIONAL GO** — the application is functionally complete, stable,
and secure. The beta build is ready for distribution to testers. Public
Play Store submission requires:
1. Physical-device testing (checklist provided).
2. Production keystore (replaces development keystore).
3. Owner-approved privacy policy hosted publicly.
4. Feature graphic.
5. Play Console developer account.

## 12. Repository

GitHub: https://github.com/xAhmedAdel/Ayah-Quran
Branch: `main`
Tag: `v1.0.0-beta.1`
Commits: `ac3053a` (initial), `a0a7c3f` (Phase 7 docs)
