# MeshCore adaptive design workbench

Seven primary Android routes have editable UI Builder `.uid` references and independently
written Compose previews. These are **stock Material 3 design proposals**, not a rewrite of
production navigation or the MeshCore branded theme. The candidate renderer never reads UID
layouts or reference PNGs. Fixtures are English sample data; callbacks are tested, but do not
connect a radio, change a real setting, or send a real message.

| Route | UID design | Production screen | Compose proposal |
| --- | --- | --- | --- |
| Connect / scanner | [scanner.uid](ui-builder/designs/scanner.uid) | [ScannerScreen](../app/src/main/kotlin/ee/schimke/meshcore/app/ScannerScreen.kt) | [KeyScreens](src/main/kotlin/ee/schimke/adaptivepilot/KeyScreens.kt) |
| Device overview | [device.uid](ui-builder/designs/device.uid) | [DeviceScreen](../app/src/main/kotlin/ee/schimke/meshcore/app/DeviceScreen.kt) | KeyScreens |
| Contact chat | [contacts.uid](ui-builder/designs/contacts.uid) | [ContactChatScreen](../app/src/main/kotlin/ee/schimke/meshcore/app/ContactChatScreen.kt) | [ContactBrowser](src/main/kotlin/ee/schimke/adaptivepilot/ContactBrowser.kt) |
| Channel chat | [channels.uid](ui-builder/designs/channels.uid) | [ChannelChatScreen](../app/src/main/kotlin/ee/schimke/meshcore/app/ChannelChatScreen.kt) | KeyScreens |
| Commands | [commands.uid](ui-builder/designs/commands.uid) | [CommandsScreen](../app/src/main/kotlin/ee/schimke/meshcore/app/CommandsScreen.kt) | KeyScreens |
| Device settings | [settings.uid](ui-builder/designs/settings.uid) | [DeviceSettingsScreen](../app/src/main/kotlin/ee/schimke/meshcore/app/DeviceSettingsScreen.kt) | KeyScreens |
| Cached / offline device | [cached.uid](ui-builder/designs/cached.uid) | [CachedDeviceScreen](../app/src/main/kotlin/ee/schimke/meshcore/app/CachedDeviceScreen.kt) | KeyScreens |

Licenses and theme-picker overlays are outside this primary-route suite. Production permission
dialogs, transport lifecycle, loading, delivery acknowledgements/retry, keyboard visibility and
screen-reader traversal still need device validation. This workbench is not evidence of parity
with the current production screen bodies.

## Adaptive matrix and interaction

[`references.json`](references.json) defines **60 comparisons**: seven routes × phone/tablet ×
list/detail × light/dark, plus four TCP connection-failure captures. Phone is 412 × 720dp;
tablet is 840 × 720dp. At 2px/dp the largest image is 1680 × 1440px, below the 1800px cap.
Tablet detail selects a second destination, including unavailable settings and saved contacts;
it is not a duplicate of tablet list. Structural alternate states are separate editable
`*-secondary.uid` snapshots; connection failure is `scanner-failure.uid`.

All screens use `currentWindowAdaptiveInfoV2()` and standard `ListDetailPaneScaffold` sizing.
Phones show one pane; tablets show overview and detail together. Selection survives resizing.
There are no content-box size classes or effects forcing pane widths. Compose tests cover
839/840dp, narrowed tablet content, navigation, blank-send protection, draft preservation on
resize, destination-aware send callbacks, read-only cached data and supported-settings controls.

[ScreenPreviews](src/main/kotlin/ee/schimke/adaptivepilot/ScreenPreviews.kt) and
[KeyScreenPreviews](src/main/kotlin/ee/schimke/adaptivepilot/KeyScreenPreviews.kt) supply IDE handles
for the captured phone/tablet light/dark states. The exported catalog source link goes to the
independent proposal; the table above links the real Android destination.

UID buttons are layout specimens. Use Compose previews/tests for behavior. The UI Builder's
editing canvas unfolds panes; choose **Device view** to see adaptive layout at the capture size.
Download editor changes, review and commit the UID before replacing the reference baseline.

## Local workaround, PR diffs and publication

```sh
./gradlew -PadaptiveUidPilot=true :adaptive-uid-pilot:ktfmtCheck \
  :adaptive-uid-pilot:test :adaptive-uid-pilot:renderPilot
```

Candidates are in `build/pilot/previews/`. Render committed UID references with the released
server CLI and matching renderer bundle/component record:

```sh
node ../compose-preview-server/scripts/ui-builder/publish-references.mjs \
  --root . --plan adaptive-uid-pilot/references.json \
  --out adaptive-uid-pilot/build/pilot --revision "$(git rev-parse HEAD)" \
  --renderer /path/to/compose-preview-server \
  --catalog /path/to/ui-builder-renderer.bundle.png \
  --components m3-catalog=/path/to/m3-catalog-components-v1.json
python3 ../design-parity/scripts/uid/evidence.py \
  --plan adaptive-uid-pilot/references.json --root adaptive-uid-pilot/build/pilot
python3 ../design-parity/scripts/uid/package.py --pilot-directory adaptive-uid-pilot \
  --output adaptive-uid-pilot/build/key-screens.zip
```

Use a clean reference output directory per revision. The publisher verifies committed input
bytes and records immutable source/render hashes. `build/pilot/index.html` provides Reference /
Diff / Actual without the hosted deployment. The PR's `Adaptive UID pilot` run uploads
`adaptive-uid-evidence` (including that HTML) and the validated comparison bundle. Missing or
wrong-size evidence fails; pixel differences remain advisory. Declared distinct captures must
actually differ in both candidate and reference images, including every light/dark pair.

For the real compare page and UID editor, serve the bundle locally:

```sh
/path/to/compose-preview-server serve --host 127.0.0.1 --port 18081 \
  --bundle key-screens=adaptive-uid-pilot/build/key-screens.zip \
  --ui-builder-dir /path/to/server-distribution/ui-builder
```

Use its authenticated browse link and `/key-screens/compare/channels-840-detail-dark`.
Local serving is a workaround when the hosted deployment is unavailable; it does not publish.
The existing Design Artifacts workflow exports these screens and UID references into
[the existing MeshCore catalog](https://preview.coo.ee/meshcore-mobile/) after merge. No new
catalog or publisher is introduced. Exported source/provenance metadata connects comparison,
code, UID editor and draft issue links. Review a draft issue before filing it.

## Advisory AI review

The trusted `Adaptive UID design audit` workflow uses `OPENROUTER_API_KEY`, the shared
compose-ai-tools audit engine and a $0.25 cap. It fetches canonical
[m3-catalog guidelines](https://github.com/yschimke/m3-catalog/blob/e588d36f971616e0bb6374d40b57e50abd7384d5/ui-builder.guidelines.json)
with a verified digest; no guidelines copy is kept here. Reports preserve rules, engine prompt,
model/cost records and annotated images. Same-repository runs only; PR code is never executed
with the key. Findings are advisory, not a gate or an automatic issue filing mechanism.

The trusted workflow intentionally uses the **default-branch capture plan**. Before this suite
is merged, its automatic audit covers the existing eight contact captures, not all 60 new
captures. Independent agent review of the full local matrix supplements that review; it must
not be reported as a completed 60-capture OpenRouter CI audit. Once the expanded plan reaches
the default branch, the workflow can audit the new subjects within its cap; a budget-limited
or failed audit is incomplete. Images alone cannot establish behavior or accessibility semantics.

Reusable workflow pins own renderer setup, pixel evidence, packaging and trusted audits. This
module owns route proposals, UID states, capture bindings and interactions. It is excluded from
the default Gradle project set unless `-PadaptiveUidPilot=true` is supplied.
