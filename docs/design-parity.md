# Design parity

[design-parity](https://github.com/yschimke/design-parity) proves a UI change is
at parity with its intended design: it renders the Compose code (the
*candidate*), diffs it against a committed design *reference*, and emits a
verdict + a self-contained HTML comparison page (reference | candidate | diff).

This repo adopts it for the **Device screen** (`DeviceScreen.kt#DeviceBody`), its
**cached (offline)** state (`CachedDeviceScreen` — `DeviceBody` with the
"Cached data" warning banner), and the **chat screens** — contact (1:1), channel
(group), and device commands, all sharing the stateless `ChatBody` — and the
**Device Settings** screen (`DeviceSettingsScreen` → stateless `DeviceSettingsBody`,
its discovered buzzer-toggle state) — each in both **light** and **dark** themes.
All render on the CMP desktop backend from `:meshcore-components` `commonMain`.

## What's committed

| Path | Role |
| --- | --- |
| `design/DeviceScreen.{light,dark}.html` | Claude Design HTML export — the Device screen references (`DeviceBodyPreview` / `DeviceBodyDarkPreview`), the single source of truth for each variant. Each carries an `application/design-parity+json` handoff manifest (M3 tokens + the `src` of its reference PNG). |
| `design/CachedDevice.{light,dark}.html` | The cached (offline) Device references (`CachedDeviceBodyPreview` etc.) — the Device reference plus the `tertiaryContainer` "Cached data" warning banner. |
| `design/ContactChat.{light,dark}.html`, `design/ChannelChat.{light,dark}.html`, `design/Commands.{light,dark}.html` | The chat screen references (`ContactChatPreview` etc.), authored from the MeshCore M3 scheme; `design/gen_chat_refs.py` is the re-runnable generator that emits them. |
| `design/DeviceSettings.{light,dark}.html` | The Device Settings references (`DeviceSettingsPreview` etc.) — the discovered buzzer-toggle state; `design/gen_settings_refs.py` is the generator. |
| `design/meshcore.tokens.json` | The MeshCore design-system tokens as a committed [W3C DTCG](https://tr.designtokens.org/) document — the artifact a Claude Code [`/design-sync`](#synced-design-system-tokens) run materialises from the Claude Design system (M3 colour roles + shape radii + spacing). Mirrors `meshcore-components/.../theme/MeshcoreTokens.kt`. |
| `design-map.json` | Correspondence: each preview-function code handle ↔ its reference — either a `figma:` node in [the design file](#the-figma-file-design-led-references) or a committed HTML export — plus the `previewId` that reconciles the compose-preview render id with the handle, plus the `tokensFile` that points every component at the synced `design/meshcore.tokens.json` spec tokens. |
| `.design-parity.json` | Parity direction. **`code-led`** (advisory) for now — flip to `design-led` once thresholds are calibrated. |

## The Figma file (design-led references)

The **[MeshCore Mobile design file](https://www.figma.com/design/gYzowY4cQ7rNr2gYoco1M6)**
(`fileKey` `gYzowY4cQ7rNr2gYoco1M6`) is the emerging primary reference. It is
**seeded from the published editable figma-SVGs** on the
[`design-artifacts/meshcore-mobile`](https://github.com/yschimke/meshcore-mobile/tree/design-artifacts/meshcore-mobile)
branch (`figma/<slug>.svg` — the "ideal render" as native Figma vectors, not
raster), then **refined by hand in Figma**. Code is judged against it; the
committed HTML references stay as the lower-precedence fallback for the variants
Figma doesn't cover yet.

One page per screen, plus foundations:

| Page | id | Contents |
| --- | --- | --- |
| Themes | `47:2` | The four theme foundation sheets + the **"MeshCore tokens"** variable collection (`light`/`dark` modes, 34 variables) generated from `design/meshcore-theme-colors.json`. This is for **designers working in Figma** — something to bind fills and radii to while refining the screens. It is *not* a CI input; see the note on variables below. |
| Components | `47:3` | The component sticker sheet, grouped into sections by family (buttons, selection controls, chips, cards, text fields, contact rows, scanner panels …). |
| Device | `47:4` | Seven state sections: loading, status connecting, status failed, no contacts, low battery, many contacts, and **cached (offline)**. |
| Contact chat | `47:6` | `chat-contact`. |
| Channel chat | `47:7` | `chat-channel`. |
| Commands | `47:8` | `chat-commands`. |
| Device settings | `47:9` | `settings-ready`. |

Cached device is **not** a separate page: `CachedDeviceBodyPreview` renders the
same stateless `DeviceBody` with the "Cached data" warning banner, and
`catalog.spec.json` already groups `Device/Cached` inside the Device group — so it
is the seventh state section on the Device page.

### Page layout

Each page is a vertical stack of **sections**, one per major state, spaced 3 200 px
apart. Inside a section sits a wrapping auto-layout of captioned **variant cells**,
one per (theme, size, locale) combination that has a render:

```
SECTION "Low battery"
└─ FRAME "variants"  (HORIZONTAL, layoutWrap WRAP)
   ├─ FRAME "light · compact"   → the editable vector (figma/<slug>.svg)
   └─ FRAME "dark · compact"    → a PNG (images/<slug>/…dark__compact.png)
```

### Variant coverage (and why it's uneven)

Cells are vectors where a vector exists and PNGs otherwise, because
compose-ai-tools' `figma-svg-emit.mjs` deliberately emits **one figma-SVG per
component function, preferring the light variant** — "a single deterministic
sticker per component". Everything else on the artifacts branch is raster:

| Axis | Coverage |
| --- | --- |
| State | Full — every state has its own section. |
| Theme | `light` vector + `dark` PNG for the six Device states. No dark render at all for cached device, the chat screens or settings. |
| Locale | `device-cached` has de/ja/ar, `settings-ready` has de. Nothing else — each locale variant needs its own hand-written `@Preview`. |
| Size | Only `compact` (412 dp). `catalog.spec.json` declares a single breakpoint. |

Filling the matrix out (large phone, tablet landscape, dark everywhere, the full
locale set) needs three things that don't exist yet: a per-variant figma-SVG export
upstream, extra `breakpoints` in `catalog.spec.json`, and locale `@Preview`
functions for the remaining screens.

Entries in `design-map.json` that resolve against it carry `"source": "figma"`
and a `"ref": "figma:gYzowY4cQ7rNr2gYoco1M6/<nodeId>"`;
[`@design-parity/adapter-figma`](https://github.com/yschimke/design-parity/tree/main/packages/adapters/figma)
fetches the node structure and renders the reference image over the Figma REST
API. CI needs a **`FIGMA_TOKEN`** repo secret — a read-only PAT with
**`file_content:read`** ("read the contents of and render images from files",
which covers both `GET /v1/files/:key/nodes` and `GET /v1/images`). Without it
the `figma` entries fail to resolve while the `claude-design` ones keep working.

**Variables are not readable from CI.** The adapter would also like
`GET /v1/files/:key/variables/local`, but Figma's Variables REST API is
**Enterprise-only** — on a Pro plan the `file_variables:read` scope isn't even
offered when minting a token, and the adapter
[degrades gracefully to structure-only tokens](https://github.com/yschimke/design-parity/tree/main/packages/adapters/figma#what-it-does).
This costs the parity check nothing: every `design-map.json` entry already
carries `"tokensFile": "design/meshcore.tokens.json"`, so **spec tokens come from
the committed DTCG document, not from Figma**. Moving a reference to Figma
changes where the *image* comes from; the *tokens* stay on disk.

Note the PAT is scoped to the whole **account**, not to this file — it inherits
access to every file the owner can see. Keep it read-only and give it an expiry;
the current token expires **2026-10-20**, tracked at
[#259](https://github.com/yschimke/meshcore-mobile/issues/259). When it lapses,
only the `figma` entries break while the `claude-design` ones keep rendering —
a failure that reads as healthier than it is.

### Seeding the file (local only)

Seeding is a **local runbook**, not something the cloud agent can do: the
`use_figma` plugin sandbox has no `fetch()` and no `figma.createImageAsync(url)`,
so `figma.createNodeFromSvg` needs the SVG text embedded in the call — which
means reading it from a checkout of the artifacts branch:

```sh
git fetch origin design-artifacts/meshcore-mobile
git worktree add /tmp/mesh-figma-svgs origin/design-artifacts/meshcore-mobile
```

Three gotchas, all hit during the first seed:

- **`use_figma` `code` is capped at 50 000 chars.** Every screen SVG fits; of the
  components only `template-appscaffold` (64 KB) doesn't, and it is seeded as a
  PNG via `upload_assets` instead.
- **Long literals in `code` are silently truncated** well below that cap — one
  13 022-char script arrived as 6 102 chars with no error and no exception, which
  would have imported corrupt data while still looking successful. Guard every
  call that carries a large literal: compute the string's length and an FNV-1a
  hash locally, recompute both inside the script, and `throw` on mismatch.
  `use_figma` is atomic, so a tripped guard writes nothing.
- **Some SVGs reference raster sidecars** (`<slug>.figma-raster/NNN.png` — e.g.
  the chat message-input field, the loading spinner). Relative `href`s don't
  resolve inside `createNodeFromSvg`, so those areas import blank. Inlining them
  as `data:` URIs works in principle but is exactly the truncation trap above.
  Instead rewrite each `<image>` to a same-geometry placeholder `<rect>`, import,
  then fill each rect via `upload_assets` with `nodeId` + `scaleMode: "FILL"`.

### Known reference gaps

The catalog carries the `:app` Device **state** variants, not the
`:meshcore-components` parity subjects, and its figma-SVGs are **light only**. So
five subjects have Figma nodes today (cached device, the three chat screens,
device settings — all light); the seven others (`DeviceBodyPreview` and every
`*DarkPreview`) still resolve to their HTML reference. Closing that gap means
adding those previews to `catalog.spec.json` so the next `design-artifacts` run
publishes figma-SVGs for them.

The seeded renders also **include the synthetic OS status bar**, which the HTML
references deliberately omit (see [Render path](#render-path)). Until the two
agree, the `figma`-sourced entries carry that offset as visual diff — tolerable
only because the direction is still `code-led` (advisory).

The reference PNGs the manifests' `src` point to (`design/*.png`) are **generated
from the HTML, not committed** (they would drift from it) — gitignored and
rendered on demand (see below). The current renders, plus the candidate bundle
and `report.html` triptychs, live on the
[`design-parity/main`](https://github.com/yschimke/meshcore-mobile/tree/design-parity/main)
branch, regenerated on every push to `main`.

## Synced design-system tokens (`/design-sync`)

Each `design-map.json` entry carries a **`tokensFile`** pointing at
`design/meshcore.tokens.json` — the MeshCore design system as a committed
[W3C DTCG](https://tr.designtokens.org/) document. This is the artifact a
[Claude Code `/design-sync`](https://github.com/yschimke/design-parity/blob/main/docs/claude-design-sync-impact.md)
run materialises from the Claude Design system: the M3 colour roles, shape
radii, and spacing, on disk and deterministic. design-parity loads it as each
component's **spec tokens** (`@design-parity/adapter-claude-design` ≥ 0.1.18
consumes `/design-sync` token artifacts) and runs them through the
token-compliance diff against the candidate's resolved tokens — so the
references no longer report "missing from candidate" for tokens (the
[first-adoption gap](#known-gaps-first-adoption) for the token half).

Because Claude Design exposes **no read API**, the file is committed, not fetched
at run time — `/design-sync` produces it, design-parity enforces it. It is the
**single source of truth** with `MeshcoreTokens.kt`: change one, change the
other (the colours here are the light scheme — the canonical design-system
values). Pointing every entry at the one file keeps the design system in one
place; the loader caches it, so the 12 entries cost one parse.

### Uploading back to Claude Design (the reverse direction)

design-parity is **read-only on Claude Design**: it resolves a committed
reference and diffs it, and ships **no `claude-design` canvas writer** (see
[design-parity's reconciliation note](https://github.com/yschimke/design-parity/blob/main/docs/claude-design-sync-impact.md#follow-ups)).
Getting what you built *into* Claude Design is **`/design-sync`'s** job — an
**interactive, human-run** Claude Code skill, deliberately off the unattended
Action path. So the round trip is: `/design-sync` (human, in a terminal) seeds
`design/meshcore.tokens.json`; this workflow (CI) then enforces it on every PR.

The push-back can't run in an unattended cloud agent session: `/design-sync`
isn't installed there (it lands in new local sessions; `/update` if absent), and
there is no Claude Design write API or credential to call. To upload, run
`/design-sync` yourself from a local Claude Code session with Claude Design beta
access.

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

This was unblocked by **compose-ai-tools 0.15.x**: 0.15.1 (#1846) resolves the
renderer in the consumer's Compose graph so Skiko stays coherent with CMP 1.11
(on 0.15.0 the desktop render failed with a Skiko `UnsatisfiedLinkError`,
[#1844](https://github.com/yschimke/compose-ai-tools/issues/1844)), and 0.15.2
auto-injects the `ee.schimke.composeai.preview` plugin into
`com.android.kotlin.multiplatform.library` modules so `:meshcore-components`
needs no explicit `plugins {}` entry.
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
#    at the candidate's pixel size (411x914 dp @ 2.625 -> 1078x2399 px). Install
#    the bundled branded faces first so Chrome resolves the families the HTML
#    names (else it falls back to a system sans and the type drifts vs the
#    candidate, which loads the same .ttf):
mkdir -p "$HOME/.local/share/fonts"
cp meshcore-components/src/desktopMain/resources/fonts/*.ttf "$HOME/.local/share/fonts/"
fc-cache -f
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

References intentionally omit the OS **status bar**: the candidate renders on the
CMP desktop backend with no system chrome (`showSystemUi` is an Android-tooling
hint the Skiko render path ignores), so a status bar in the reference offset every
row by its height and dominated the diff. The references draw the screen's own top
app bar as the first element (y=0), matching the candidate; the system status bar
is OS chrome, not part of the component under test.

This is an **interim** measure: once the desktop renderer learns to draw synthetic
system bars for `showSystemUi = true`
([compose-ai-tools#1930](https://github.com/yschimke/compose-ai-tools/issues/1930),
porting the Android renderer's `SystemBarsFrame`), the candidate will carry a
status bar of its own and these references can restore the realistic phone framing.

The references render the **branded faces** (Space Grotesk / Orbitron / JetBrains
Mono): the workflow installs the bundled `.ttf` — the same ones the desktop
candidate loads — into fontconfig before the Chrome render, so both sides draw the
real type by family name. Without it Chrome falls back to a system sans, which was
the dominant residual after the status bar.

Earlier run (before dropping the status bar): visual diff **~11%** (light) /
**~6%** (dark) of pixels over the overlap — the bulk of it that systemic vertical
offset, then typography until the fonts were installed.

## Continuous artifacts (`design-parity/main` branch)

`.github/workflows/design-parity.yml` regenerates the artifacts on every push to
`main` and force-pushes them to the long-lived **`design-parity/main`** branch
(the candidate bundle, the `report.html` triptychs, and a `SOURCE_COMMIT`
stamp). That branch always reflects the current parity state of `main` without
committing generated PNGs/HTML onto `main` itself — browse it to see the latest
reference | candidate | diff without re-rendering locally.

The workflow drives the render from `design-map.json` (so it can't drift from
it): it installs the released `compose-preview` CLI, packs the candidate bundle,
runs the published `design-parity` CLI (pinned in
[`design-parity.yml`](../.github/workflows/design-parity.yml), which carries the
rationale for the current version), and publishes
the output. The permanent-branch push is still the interim, hand-rolled version
of a pattern design-parity's Action should own —
[design-parity#56](https://github.com/yschimke/design-parity/issues/56) (modeled
on compose-ai-tools' `apply` baseline branch).

## The same references on the preview server

`design-map.json` now feeds a second consumer. The `Publish design references`
step of compose-ai-tools' `design-artifacts-reusable.yml` reads it during the
catalog render and writes a `compose-preview-references/v1` manifest to
`references/` on the [`design-artifacts/meshcore-mobile`](https://github.com/yschimke/meshcore-mobile/tree/design-artifacts/meshcore-mobile)
delivery branch — which turns on the **PNG ↔ Design reference** lane at
<https://preview.coo.ee/meshcore-mobile/compare> and the Reference / Diff /
Actual page behind it. Before that producer existed the server had read that
manifest for some time but nothing wrote one, so the lane was absent on every
published catalog.

Two things follow from this that are worth knowing when editing `design-map.json`:

- **An entry only reaches the server if `catalog.spec.json` publishes the same
  `@Preview` function.** The join is by function name. `DeviceBodyPreview` /
  `DeviceBodyDarkPreview` (`:meshcore-components`) map to nothing today, because
  the catalog's Device screens come from `:app`'s `DeviceScreenPreviewsKt`
  instead — those two are parity-run-only, and the export says so with a warning
  rather than failing.
- **The `figma:` entries need the `FIGMA_TOKEN` secret** to be published, which
  `design-artifacts.yml` passes through. Without it the five light variants are
  skipped with a warning and only the committed HTML dark mocks appear on the
  server. Note the ordering constraint if the reusable-workflow pin ever moves:
  the caller pins `@main`, and GitHub rejects a named secret the called workflow
  doesn't declare, so the pinned ref must always carry the `figma_token`
  declaration.

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
