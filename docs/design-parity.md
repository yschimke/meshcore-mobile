# Design parity

[design-parity](https://github.com/yschimke/design-parity) proves a UI change is
at parity with its intended design: it renders the Compose code (the
*candidate*), diffs it against a committed design *reference*, and emits a
verdict + a self-contained HTML comparison page (reference | candidate | diff).

This repo adopts it for the **Device screen** (`DeviceScreen.kt#DeviceBody`) as
the first subject, in both **light** and **dark** themes.

## What's committed

| Path | Role |
| --- | --- |
| `design/DeviceScreen.light.html` | Claude Design HTML export — the **light** reference (`DeviceBodyPreview`). Carries an `application/design-parity+json` handoff manifest (M3 light tokens + a pre-rendered PNG variant). |
| `design/DeviceScreen.dark.html` | The **dark** reference (`DeviceBodyDarkPreview`), M3 dark tokens. |
| `design/DeviceScreen.{light,dark}.png` | Pre-rendered reference images the manifests' `src` point to, so a parity run is fully offline (no headless browser at run time). |
| `design-map.json` | Correspondence: each preview-function code handle ↔ its reference, plus the `previewId` that reconciles the compose-preview render id with the handle. |
| `.design-parity.json` | Parity direction. **`code-led`** (advisory) for now — flip to `design-led` once thresholds are calibrated. |

## Render path

meshcore-mobile is **Android-only** for UI (the `:app` previews use
`androidx.compose.ui.tooling.preview.Preview`; there is no Desktop/JVM Compose
Multiplatform target). So parity uses the **Android render path** (Robolectric
native graphics, no emulator) rather than the cheaper CMP/Desktop one. Lifting a
pure composable + preview into `commonMain`/`desktopMain` would enable the
Desktop path later, but isn't required.

## Reproduce

```sh
# 1. Render the candidates to one portable preview bundle (PNG + zip polyglot).
#    Requires the Android SDK (see .claude/hooks/session-start.sh) and a
#    UTF-8 locale (the preview ids contain an em-dash).
export LANG=C.UTF-8 LC_ALL=C.UTF-8
compose-preview bundle pack --module app \
  --id "ee.schimke.meshcore.app.ui.DeviceScreenPreviewsKt.DeviceBodyPreview_Device — populated" \
  --id "ee.schimke.meshcore.app.ui.DeviceScreenPreviewsKt.DeviceBodyDarkPreview_Device — dark" \
  -o app/build/compose-previews/bundle.png

# 2. (Optional) re-render a reference PNG from its committed HTML, at the
#    candidate's pixel size (411x914 dp @ 2.625 -> 1078x2399 px):
chrome --headless=new --no-sandbox --hide-scrollbars \
  --force-device-scale-factor=2.625 --window-size=411,914 \
  --screenshot=design/DeviceScreen.light.png design/DeviceScreen.light.html

# 3. Run the parity check and open the reports.
design-parity run --repo . \
  --components "app/src/main/kotlin/ee/schimke/meshcore/app/ui/DeviceScreenPreviews.kt#DeviceBodyPreview,app/src/main/kotlin/ee/schimke/meshcore/app/ui/DeviceScreenPreviews.kt#DeviceBodyDarkPreview" \
  --candidate-bundles app/build/compose-previews/bundle.png \
  --out .design-parity/out
# -> read the markdown verdict; open
#    .design-parity/out/<component>/report.html  (reference | candidate | diff)
```

Latest run: visual diff **~10%** (light) / **~6.6%** (dark) of pixels over the
overlap — mostly typography (the branded Orbitron / Space Grotesk faces vs the
web fallback) and minor vertical drift.

## Known gaps (first adoption)

- **Bundle carries no semantics blob.** `compose-preview bundle pack` emits the
  candidate PNG but no `previews/<id>.semantics.json` (a11y tree + resolved
  fg/bg colours), so the run degrades to **visual/structural-only**: the
  contrast/a11y checks can't run and every reference token reports "missing from
  candidate". This is a compose-ai-tools renderer gap, tracked at
  [compose-ai-tools#1843](https://github.com/yschimke/compose-ai-tools/issues/1843).
- **Theme tagging / pairing (design-parity #48).** The previews theme via
  `MaterialTheme`, so `uiMode` carries the signal. `DeviceBodyDarkPreview` sets
  `uiMode = NIGHT_YES`, so its candidate resolves to `theme: dark` and pairs
  cleanly with the dark reference image. `DeviceBodyPreview` leaves `uiMode`
  unset (→ no theme), so the **light** reference image is intentionally left
  theme-agnostic to pair by state. A `_Light` preview name or an explicit theme
  hint would let the light side carry `theme: light` too.

## Scaling further

Each new screen is one preview + one HTML reference + one `design-map.json`
entry. Keep subjects **static and deterministic** (preview-parameter providers
with sample state — no live transport / clock / network), and add one variant at
a time so candidate↔reference *pairing* is never debugged alongside content
drift.
