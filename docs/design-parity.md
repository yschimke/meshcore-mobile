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
| `design/DeviceScreen.light.html` | Claude Design HTML export — the **light** reference (`DeviceBodyPreview`), and the single source of truth for that variant. Carries an `application/design-parity+json` handoff manifest (M3 light tokens + the `src` of its reference PNG). |
| `design/DeviceScreen.dark.html` | The **dark** reference (`DeviceBodyDarkPreview`), M3 dark tokens. |
| `design-map.json` | Correspondence: each preview-function code handle ↔ its reference, plus the `previewId` that reconciles the compose-preview render id with the handle. |
| `.design-parity.json` | Parity direction. **`code-led`** (advisory) for now — flip to `design-led` once thresholds are calibrated. |

The reference PNGs the manifests' `src` point to (`design/*.png`) are **generated
from the HTML, not committed** (they would drift from it) — gitignored and
rendered on demand (see below). The current renders, plus the candidate bundle
and `report.html` triptychs, live on the
[`design-parity/main`](https://github.com/yschimke/meshcore-mobile/tree/design-parity/main)
branch, regenerated on every push to `main`.

## Render path

Parity renders on the **CMP desktop (Skiko) backend**. The presentational
`DeviceBody` (and its leaf cards, theme, `Section`/`Dimens`, and the two parity
`@Preview`s) live in **`:meshcore-components` `commonMain`** with a
`jvm("desktop")` target, so the candidate renders off-Android — no Robolectric,
no emulator. `bundle pack --module meshcore-components` reports `backend=desktop`.

Making the Device screen render off-Android required:
- **Vendored icons** — `material-icons-extended` is Android-only on CMP, so the
  20 Material `Rounded` icons the shared composables use are copied into
  `commonMain` `MeshIcons.kt`, generated faithfully from the real androidx
  `ImageVector`s by `app/src/test/.../IconExtractorTest.kt` (re-runnable).
- **Multiplatform fonts** — the branded faces are wired `expect`/`actual`:
  Android keeps the downloadable Google Fonts provider; desktop loads bundled
  `.ttf` (`:meshcore-components/src/desktopMain/resources/fonts`).
- **`:meshcore-components` applies `id("ee.schimke.composeai.preview")`** — the
  CLI can't auto-inject into a `com.android.kotlin.multiplatform.library` module.

This was unblocked by **compose-ai-tools 0.15.1** (#1846 resolves the renderer in
the consumer's Compose graph so Skiko stays coherent with CMP 1.11); on 0.15.0
the desktop render failed with a Skiko `UnsatisfiedLinkError`
([compose-ai-tools#1844](https://github.com/yschimke/compose-ai-tools/issues/1844)).
The stateful `DeviceScreen`/`ConnectedDevice` wrappers (transport, ViewModel)
stay in `:app`; only the pure presentational subtree moved.

## Reproduce

```sh
# 1. Render the candidates to one portable preview bundle (CMP desktop backend).
#    UTF-8 locale needed (the preview ids contain an em-dash).
export LANG=C.UTF-8 LC_ALL=C.UTF-8
compose-preview bundle pack --module meshcore-components \
  --id "ee.schimke.meshcore.components.ui.DeviceBodyPreviewsKt.DeviceBodyPreview_Device — populated" \
  --id "ee.schimke.meshcore.components.ui.DeviceBodyPreviewsKt.DeviceBodyDarkPreview_Device — dark" \
  -o build/design-parity/bundle.png

# 2. Render the reference PNGs from the HTML (required — they're not committed),
#    at the candidate's pixel size (411x914 dp @ 2.625 -> 1078x2399 px):
for ref in $(jq -r '.components[].ref' design-map.json); do
  chrome --headless=new --no-sandbox --hide-scrollbars \
    --force-device-scale-factor=2.625 --window-size=411,914 \
    --screenshot="${ref%.html}.png" "$ref"
done

# 3. Run the parity check and open the reports.
design-parity run --repo . \
  --components "meshcore-components/src/commonMain/kotlin/ee/schimke/meshcore/components/ui/DeviceBodyPreviews.kt#DeviceBodyPreview,meshcore-components/src/commonMain/kotlin/ee/schimke/meshcore/components/ui/DeviceBodyPreviews.kt#DeviceBodyDarkPreview" \
  --candidate-bundles build/design-parity/bundle.png \
  --out .design-parity/out
# -> read the markdown verdict; open
#    .design-parity/out/<component>/report.html  (reference | candidate | diff)
```

Latest run (desktop backend): visual diff **~11%** (light) / **~6%** (dark) of
pixels over the overlap — mostly typography and minor vertical drift.

## Continuous artifacts (`design-parity/main` branch)

`.github/workflows/design-parity.yml` regenerates the artifacts on every push to
`main` and force-pushes them to the long-lived **`design-parity/main`** branch
(the candidate bundle, the `report.html` triptychs, and a `SOURCE_COMMIT`
stamp). That branch always reflects the current parity state of `main` without
committing generated PNGs/HTML onto `main` itself — browse it to see the latest
reference | candidate | diff without re-rendering locally.

The workflow drives the render from `design-map.json` (so it can't drift from
it): it installs the released `compose-preview` CLI, packs the candidate bundle,
runs `design-parity run`, and publishes the output. It is the interim,
hand-rolled version of a pattern design-parity's Action should own —
[design-parity#56](https://github.com/yschimke/design-parity/issues/56) (modeled
on compose-ai-tools' `apply` baseline branch). Until that lands, design-parity is
built from a pinned checkout because it isn't published and its `dist/` isn't
committed.

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
