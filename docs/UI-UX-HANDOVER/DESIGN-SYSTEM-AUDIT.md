# Design System Audit (actual values from code)

Everything below is read from
`app/src/main/java/com/ayah/quran/core/designsystem/theme/` — this IS the
current design system. Any redesign should replace values here (or create a
new theme layer), not scatter new literals.

## A. Colors (`theme/Color.kt`)

Brand palette (emerald + gold on cream/ink):

| Token | HEX | Role |
|---|---|---|
| Emerald | `#153D32` | primary base (light) |
| EmeraldDeep | `#0E2B23` | dark backgrounds |
| EmeraldBright | `#1E5A49` | dark primary |
| Gold | `#D5B56A` | secondary/accents, gold headers |
| GoldSoft | `#E8D3A2` | soft gold |
| GoldDeep | `#A88C4C` | deep gold / light secondary |
| Cream | `#F7F2E8` | light surface base |
| CreamBright | `#FCF9F2` | light surface bright |
| Ink | `#111714` | dark background |
| InkSurface | `#161D19` | dark surface |
| InkSurfaceVariant | `#1D2621` | dark surface variant |
| TextPrimaryDark | `#F5F2EA` | dark-theme text |
| TextSecondaryDark | `#C0C9C3` | dark-theme secondary text |
| EmeraldContainerLight | `#D2E3DB` | primaryContainer (light) |
| GoldContainerLight | `#F1E3C4` | secondaryContainer (light) |
| TextPrimaryLight | `#17201B` | light text |
| TextSecondaryLight | `#47514B` | light secondary text |
| OutlineLight | `#7A857E` / OutlineDark `#8A938D` | outlines |

Material scheme mapping (`AyahLightColors` / `AyahDarkColors`):
- Light: cream surfaceContainer family `#FFFFFF` / `#FBF7ED` / `#F5F1E6` /
  `#EFEBDF` / `#EAE5D6`; surfaceVariant `#E4DED0`; onSecondaryContainer
  `#4A3B15`.
- Dark: surfaceContainer family `#0D1310` / `#161D19` / `#1A211D` /
  `#202824` / `#262E29`; primaryContainer `#23463A`; secondaryContainer
  `#4A3D1E`; outlineVariant `#3A423D`.
- ⚠️ The full surfaceContainer* ramp is REQUIRED — M3 falls back to
  neutral gray without it (past bug, see
  `compose-m3-theme-gotchas` knowledge).
- Dark mode follows system (`ThemeMode.SYSTEM` default) + in-app
  light/dark/system selector. No custom gradients found.

## B. Typography (`theme/Type.kt`)

- **Quran/Arabic display font**: Amiri — `res/font/amiri_regular.ttf`,
  `res/font/amiri_bold.ttf` → `QuranTextFontFamily`. Used for surah names,
  wordmark, mushaf text (via `MushafText`), prayer headers.
- Everything else: `FontFamily.Default` (system Roboto) through the M3
  `Typography` defaults (`AyahTypography`); no custom display styles beyond
  `AyahWordmarkStyle`.
- Sizes are set inline per-screen where customized (e.g. surah header
  34.sp, mushaf text `fontScale * 26.sp` — user-adjustable 0.5×–2.0×).
- RTL: Arabic default; text alignment set per-screen (mushaf text centered
  justification via `TextAlign.Justify` for ayah text).

## C. Spacing & layout (`theme/Spacing.kt`)

`AyahSpacing`: xs=4dp, s=8dp, m=16dp, l=24dp, xl=32dp, xxl=48dp,
card=20dp, sheet=28dp, pill=100dp (corner shape radii use these too).
Cards: `AyahCard` (elevated surface) with `padding(AyahSpacing.m)`
internally; sections use `Arrangement.spacedBy(AyahSpacing.m)`.

Insets: standard Scaffold padding; the reader hides the bottom bar
(immersive); status bar via default M3 TopAppBar handling. No custom
safe-area code.

## D. Component inventory → see `UI-COMPONENT-INVENTORY.md`
