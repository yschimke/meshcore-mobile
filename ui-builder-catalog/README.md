# MeshCore typed component catalog

This opt-in module wires six `:meshcore-components` composables into the typed UI-builder adapter
API. It is excluded from normal application builds while the API and bridge are unreleased. Each
definition publishes its own ID and properties/slots, and the registry invokes the actual component
with compiled calls.

| Component | Adapter ID | Properties | Slots | Events |
| --- | --- | --- | --- | --- |
| `ChatInput` | `meshcore-mobile/chat-input` | `value` (two-way via `onValueChange`), `enabled`, `placeholder` | — | `send` |
| `ContactListEmpty` | `meshcore-mobile/empty-contacts` | — | — | — |
| `MeshcoreTheme` | `meshcore-mobile/theme` | `darkTheme` | `content` | — |
| `DeviceSummaryCard` | `meshcore-mobile/device-summary` | — | — | — |
| `RadioLine` | `meshcore-mobile/radio-line` | `radio` (object: `RadioSettings`) | — | — |
| `ChannelList` | `meshcore-mobile/channel-list` | `channels` (list of `ChannelInfo` objects) | — | — |

`MeshcoreTheme` scopes the app's color scheme and type to whatever the design places in `content`;
it takes no modifier, so its adapter wraps the call in a constraint-transparent `Box` carrying the
node's own. `DeviceSummaryCard` is placed with every device detail null: the placeholder card shown
before a device reports itself.

`RadioLine` and `ChannelList` take the real `meshcore-core` models through the typed API's list and
object codecs (compose-ai-tools 2.49.0). `RadioSettingsCodec` names
`ee.schimke.meshcore.core.model.RadioSettings` with all four `Int` fields required;
`ChannelInfoCodec` names `ChannelInfo` with a required `index` and a `name` defaulting to empty (the
row then shows its index label). A channel's pre-shared key is never displayed, so it is not a
field: `ChannelInfo.psk` defaults to an empty `ByteString`, which keeps `ChannelInfo(index = …,
name = …)` a valid constructor call. Stored values nest the document's wrappers,
`{"type": "list", "values": [{"type": "object", "fields": {"index": {"type": "int", "value": 0}}}]}`,
and the builder edits them structurally. `RadioLine` takes no modifier, so like `MeshcoreTheme` its
adapter wraps the call in a `Box`. This catalog declares no `exportAs` projections, so the
`listOf(ChannelInfo(…))` export lowering is not wired here. Components that need a model with fields
these codecs cannot hold (`Contact`'s keys and instants, `ChatMessage`'s timestamp) or a
multi-argument callback (`TcpConnectPanel`) remain unadapted.

Check out compose-ai-tools and compose-ui-builder beside this repository at the immutable revisions
in `.github/workflows/typed-catalog.yml`. The workflow validates the same source integration on
catalog/component/dependency changes. Existing public preview publication remains on released tools.

```sh
scripts/validate-typed-catalog.sh
```

The script accepts optional tools and builder checkout paths. It scans current compiled app classes,
generates only explicitly selected records, checks JSON readback and stale-record refusal, then
compares each native component with its typed adapter pixel for pixel (`MeshcoreTheme` with a dark
theme around light surroundings, so the decoded property and the `content` slot both show), edits
`ChatInput`'s actual text field and dispatches Send through the SDK. A separate builder process reads the resulting pair through
`PublishedUiBuilderCatalog` with no app implementation on its classpath. PNG comparisons are saved
under `ui-builder-catalog/build/renders`; neither rendering test connects to a real device or server.

The script also packages `build/distributions/meshcore-mobile-jvm-catalog.zip`: the generated pair,
the tracked designs under `designs/`, and the adapter and runtime dependency JARs in `lib/`.
`chat-input.uid` drives the external host test below; `device-overview.uid` places the theme,
device summary, radio line, channel list and empty contact list together, and the contract test
decodes its stored radio object and channel list through the published adapters. The contract test checks that every tracked design
places only published adapters and the host's `layout/column` / `m3/text` builtins. A third,
separate builder process compiles without Meshcore classes, installs those JARs with a dedicated
class loader, resolves the catalog provider through the JVM SDK service entry point, and renders
the saved design with `UiBuilderSurface`. The test edits `enabled` and `placeholder` through the
editor reducer, types into the actual ChatInput, checks its two-way state against a sibling text
node, and clicks Send to execute the design's event action. It then saves and reopens the edited
`.uid`, checking that property edits persist and interaction state resets. It saves screenshots and
the edited `.uid` under the builder's `ui-builder/build/typed-catalog/e2e`; CI uploads these and the ZIP.
Send writes preview state only; it does not send a radio message.

To replay a packaged artifact independently, unzip it into a directory and run in the matching
builder checkout (no app checkout or source dependency is needed by this command):

```sh
./gradlew -PtypedAdapterCatalog=true -PlocalBuilds=tools \
  -PlocalBuild.tools=../compose-ai-tools -PtypedCatalogJvmBundle=/absolute/path/to/unzipped-bundle \
  :ui-builder:jvmTest --tests '*ExternalJvmCatalogEndToEndTest'
```

Only install a bundle whose code you trust. JVM providers execute in the host process. The test
keeps the loader open for the lifetime of the rendered composition and closes it afterward.

To generate the standard pair directly:

```sh
./gradlew -PtypedAdapterCatalog=true -PlocalBuilds=tools \
  -PlocalBuild.tools=../compose-ai-tools -PuiBuilderSource=../compose-ui-builder \
  :ui-builder-catalog:exportTypedCatalog
```

Output: `ui-builder-catalog/build/catalog/components.json` and `ui-builder.json`. The JVM export
entry point scans the current compiled classes. Task-owned previous output is
replaced on regeneration; the underlying pair writer refuses arbitrary existing destinations.

To stage this in a catalog delivery generation, copy both files together and set the delivery
`catalog.json` fields `componentsFile` and `uiBuilderFile` to those relative paths. Do not combine
records from one source revision with policy from another. Keep the executable renderer from the
same revision with the catalog when installing it in a native host. CI uploads the generated pair
and comparison renders as review artifacts; this module does not push a delivery branch.

These app libraries currently target Android/JVM, not Wasm. Records explicitly declare `nativeOnly`,
so a browser editor must show its existing native placeholder until a compatible runtime is
packaged. This is a JVM bundle/host proof; desktop editor installation UI, IDE integration, public
artifact resolution and preview-server discovery remain separate work. The existing sticker-sheet catalog and
its live preview bundles are not replaced by this opt-in proof. General event capabilities and
omitted-property default/export parity retain the limits documented in the authoring API.

The committed `evidence/` images show the direct native `ChatInput` call and its typed adapter from
the parity test. Regenerate them from `build/renders` after component changes; CI uploads current
renders for every adapted component (`native-NAME.png` / `typed-NAME.png`).

### Publish a validated initial generation

A manual dispatch can publish the tested ZIP and standard catalog pair as a GitHub prerelease:

```sh
gh workflow run typed-catalog.yml --ref YOUR_SOURCE_BRANCH -f publish=true
```

The separate publication job runs only after validation succeeds, reuses its uploaded artifact,
and records full app/tools/builder commits and SHA-256 hashes in `bundle.json`. The prerelease tag
is `typed-catalog-preview-FULL_SOURCE_SHA`; an existing generation is never replaced. Normal PR
runs have read-only permissions, and production preview delivery branches remain unchanged.

From the matching UI Builder checkout, download the exact published generation into a new directory:

```sh
scripts/download-typed-catalog.sh yschimke/meshcore-mobile FULL_SOURCE_SHA jvm-linux-x64 /tmp/published-mesh
```

Run `ExternalJvmCatalogEndToEndTest` with `-PtypedCatalogJvmBundle=/tmp/published-mesh` to repeat the
proof using downloaded artifacts. The installer refuses a mismatched source, runtime, checksum,
or catalog pair. This initial executable archive targets Linux x64; it is an explicit trusted-code
opt-in, not a browser renderer or a general desktop installation UI.
