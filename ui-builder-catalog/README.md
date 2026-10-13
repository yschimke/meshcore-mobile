# MeshCore typed component catalog

This opt-in module wires 19 `:meshcore-components` composables into the typed UI-builder adapter
API. It is excluded from normal application builds unless a pinned renderer SDK checkout is supplied. Each
definition publishes its own ID and properties/slots, and the registry invokes the actual component
with compiled calls. Every property has an insertion default taken from the library's own preview
fixtures (`MeshcoreSamples`: the `node-peak` device, alice / bob-repeater / common-room /
soil-sensor-1, the General / Emergency channels, the alice chat), so a freshly placed component looks
like the app's previews.

| Component | Adapter ID | Properties | Slots | Events |
| --- | --- | --- | --- | --- |
| `ChatInput` | `meshcore-mobile/chat-input` | `value` (two-way via `onValueChange`), `enabled`, `placeholder` | — | `send` |
| `ChatBubble` | `meshcore-mobile/chat-bubble` | `message` (`ChatMessage`) | — | `retry` |
| `ChatMessageList` | `meshcore-mobile/chat-message-list` | `messages` (list of `ChatMessage`) | — | `retry` (`(ChatMessage)`) |
| `ChatBody` | `meshcore-mobile/chat-body` | `title`, `subtitle` (nullable), `messages`, `draft` (two-way via `onDraftChange`), `inputEnabled`, `inputPlaceholder` | — (`actions` keeps its default) | `send`, `back`, `retry` (`(ChatMessage)`) |
| `TcpConnectPanel` | `meshcore-mobile/tcp-connect` | `busy`, `defaultHost`, `defaultPort` | — | `connect` (`(String, Int)`) |
| `MeshcoreTheme` | `meshcore-mobile/theme` | `darkTheme` | `content` | — |
| `DeviceSummaryCard` | `meshcore-mobile/device-summary` | `self` (`SelfInfo?`), `radio` (`RadioSettings?`), `battery` (`BatteryInfo?`) | — | — |
| `RadioLine` | `meshcore-mobile/radio-line` | `radio` (`RadioSettings`) | — | — |
| `BatterySection` | `meshcore-mobile/battery` | `battery` (`BatteryInfo`) | — | — |
| `ContactRow` | `meshcore-mobile/contact-row` | `contact` (`Contact`) | — | `click` |
| `ContactList` | `meshcore-mobile/contact-list` | `contacts` (list of `Contact`) | — | `contactClick` (`(Contact)`) |
| `ContactListEmpty` | `meshcore-mobile/empty-contacts` | — | — | — |
| `ChannelRow` | `meshcore-mobile/channel-row` | `channel` (`ChannelInfo`) | — | `click` |
| `ChannelList` | `meshcore-mobile/channel-list` | `channels` (list of `ChannelInfo`) | — | `channelClick` (`(ChannelInfo)`) |
| `DeviceSettingsBody` | `meshcore-mobile/device-settings` | `state` (union `DeviceSettingsUiState`: `discovering` / `error` / `ready`) | — | `toggleBuzzer` (`(Boolean)`), `back` |
| `ThemePickerDialog` | `meshcore-mobile/theme-picker` | `current` (`ThemeSettings`, enums `ThemeMode` / `ThemePalette`), `dynamicColorAvailable` | — | `modeSelect` (`(ThemeMode)`), `paletteSelect` (`(ThemePalette)`), `dismiss` |
| `LoginDialog` | `meshcore-mobile/login-dialog` | `contactName`, `contactType`, `state` (union `LoginDialogState`: `prompting` / `authenticating`) | — | `login` (`(String)`), `dismiss` |
| `DeviceStatusView` | `meshcore-mobile/device-status` | `title`, `status` (union `DeviceConnectStatus`: `connecting` only) | — | `cancel`, `openThemePicker` |
| `DeviceBody` | `meshcore-mobile/device-body` | `self`, `battery`, `radio` (nullable), `contacts`, `contactsLoading`, `contactsRefreshing`, `channels`, `contactedKeys`, `contactedChannelIndices`, `sectionStates` (`SectionStates`), `lastMessage` (nullable union `LastMessageInfo`: `dm` / `channel`), `warnings` (list of `String`) | — | `disconnect`, `sectionExpandedChange` / `sectionShowAllChange` (`(Section, Boolean)`), `lastMessageClick`, `contactClick`, `channelClick`, `dismissWarning` (payload), `commandsClick`, `settingsClick`, `openThemePicker` |

### Model codecs

The adapters take the real `meshcore-core` and component models through the typed API's codecs
(`MeshcoreCodecs.kt`); stored values nest the document's wrappers, and the builder edits them
structurally.

- `RadioSettings`: all four `Int` fields required. `ChannelInfo`: a required `index` and a `name`
  defaulting to empty; the pre-shared key is never displayed, so it is not a field and keeps the
  class's empty default.
- `SelfInfo`: `name`, `publicKey` and `radio` required, the advert/power/location/policy fields
  defaulted (the coordinates are `Double`s). `BatteryInfo`: `millivolts` required, the `Long`
  storage sizes defaulted to zero.
- `Contact`: `name` and `publicKey` required; `type` (`ContactType` members), `pathLength`, `path`,
  `flags`, the two `Instant`s (ISO-8601 text) and the `Double` coordinates defaulted.
- `ChatMessage`: `id`, `text` and `timestamp` (`Instant`) required; `senderName` and `snr` nullable,
  `isMine` and `status` (`MessageStatus` members) defaulted.
- `PublicKey` is parsed hex text exported as `PublicKey.fromHex("…")`. A `ByteString` (a contact's
  route) is parsed hex text through the small public `byteStringOfHex` helper added to
  `meshcore-core` for this purpose (`ByteString.toHex()` is its inverse).
- `AdapterValueCodecs.enumValue` decodes the enum member, including inside object fields.
  `AdapterValueCodecs.set` edits `contactedKeys` and `contactedChannelIndices` as lists and refuses duplicates.
- Unions: `DeviceSettingsUiState` (a `discovering` singleton, `error`, `ready`), `LoginDialogState`
  (`prompting`, an `authenticating` singleton), `LastMessageInfo` (`dm`, `channel`) and
  `DeviceConnectStatus`, which holds only `connecting`: `DeviceConnectStatus.Failed(cause:
  Throwable)` carries an exception, which no stored value can describe, so it is not a case and the
  failure card cannot be placed.

Top-level nullable properties default to null where the component's own parameter does (a stored
null is the property left unset): a new `DeviceSummaryCard` is the placeholder card shown before a
device reports itself until the design fills in its details, and `ChatBody.subtitle` and
`DeviceBody.lastMessage` start empty. `DeviceBody`'s `self`, `battery` and `radio` are required
nullable parameters, so they insert as the sample device.

### Canvas behaviour

- Every event is always passed, so optional handlers (`ContactRow.onClick`, `ChatBubble.onRetry`, the
  lists' click and retry callbacks) are non-null on the canvas: rows draw enabled and a failed own
  message reads "Failed — tap to retry". Payload callbacks run the design's action and drop their
  arguments, as the SDK does.
- The lazy lists (`ChannelList`, `ContactList`, `ChatMessageList`) keep their `LazyListState`
  default and are bounded to 480 dp, and the scaffold screens (`ChatBody`, `DeviceSettingsBody`,
  `DeviceStatusView`, `DeviceBody`) to 800 dp: a document column measures children with unbounded
  height. Each list draws the first row of a repeated key (channel index, contact key, message id)
  rather than failing composition.
- `MeshcoreTheme` and the two dialogs take no modifier, so their adapters wrap the call in a
  constraint-transparent `Box` carrying the node's own. The dialogs open over the canvas as they do
  in the app.
- `DeviceStatusView` pins its card's clock 7 s after the stored `startedAtMs`, as the app's
  `DeviceStatusConnectingPreview` does, so a design draws one deterministic moment of the attempt;
  an exported call keeps the component's real clock.
- This catalog declares no `exportAs` projections, so payload events, the set codec and the
  `listOf(…)` export lowering are not wired for source export here.

### Not adapted

The eligible public composables are the 21 in `commonMain` (desktop adds none of its own). Two are
left out:

- `RemoteComposeCard(bytes: ByteArray, …)`: its input is a binary RemoteCompose wire document, which
  is not authorable as a property value; a design has nothing meaningful to type into it.
- `MeshcoreClientApp`: the stateful application root (it owns transports, a manager and navigation),
  not a reusable component.

The `androidMain` BLE/USB components (`BleScannerContent`, `ScanStatusBar`, `BlePermissionPanel`,
`BleDeviceList`, `BlePairingTipBanner`, `UsbPortsPanel`, `UsbPortCard`) are Android-only and out of
scope for this desktop catalog.

Check out compose-ai-tools and compose-ui-builder beside this repository at the immutable revisions
in `.github/workflows/typed-catalog.yml`. The workflow validates the same source integration on
catalog/component/dependency changes. Existing public preview publication remains on released tools.

```sh
scripts/validate-typed-catalog.sh
```

The script accepts optional tools and builder checkout paths. It scans current compiled app classes,
generates only explicitly selected records, checks JSON readback and stale-record refusal, then
compares each native component with its typed adapter pixel for pixel (`MeshcoreTheme` with a dark
theme around light surroundings, so the decoded property and the `content` slot both show; every
other adapter from stored values that differ from its defaults, then dispatching its events,
payload callbacks included), edits
`ChatInput`'s actual text field and dispatches Send through the SDK. A separate builder process reads the resulting pair through
`PublishedUiBuilderCatalog` with no app implementation on its classpath. PNG comparisons are saved
under `ui-builder-catalog/build/renders`; neither rendering test connects to a real device or server.

The script also packages `build/distributions/meshcore-mobile-jvm-catalog.zip` as a native-host
validation artifact: the generated pair, tracked designs and adapter/dependency JARs. An independent
`:ui-builder-catalog-consumer` links only the released builder service, composes the actual published
pair (including the declared structural helpers), verifies the component/runtime claims and seed
paths, and checks that no app implementation is on its classpath.

To generate the standard pair directly:

```sh
./gradlew -PcomposeUiBuilderDir=../compose-ui-builder :ui-builder-catalog:exportTypedCatalog
```

`chat-input.uid` exercises two-way state and Send. `device-overview.uid` places the theme and device
summary together; `component-sheet.uid` supplies the stored contact, message, battery and union
values. `scripts/typed-renderer-smoke.py` loads the packaged Wasm runtime in a sandboxed Chromium
iframe, renders both ChatInput and the complete sheet, checks node/text measurements and clicks the
actual Send button. Send updates preview state only. The full sheet opens both real app dialogs,
so they overlap in its screenshot.

The former upstream JVM fixture assumed every component was native-only and no helpers were
published. The consumer here validates the app's actual delivery contract instead. General IDE
installation UI and omitted-property source-export parity remain separate work.

## Delivery and browser runtime

The catalog uses the released discovery API 2.54.0 and the renderer SDK from the builder's
`v3.111.0` source checkout (the SDK is not a Maven artifact). Supply
`-PcomposeUiBuilderDir=/path/to/compose-ui-builder` to include the catalog and renderer modules.

`design-artifacts.yml` is the publication path: it publishes the matching `ui-builder.json`,
`ui-builder/components.json`, three seed designs, and `ui-builder/runtime.zip` to
`yschimke/meshcore-mobile-out` beside the sticker sheet. `typed-catalog.yml` validates the JVM
host and builds the Wasm package; it no longer creates a separate experimental prerelease.

The browser compiles the same `MeshcoreCatalog`, codecs and samples against the real Wasm
`:meshcore-components` variant. Its registry is hosted by `CanvasDocumentHost` and the sandbox
protocol, with ordinary Column and Text adapters for the seeds. No editor code ships in the ZIP.
The runtime ID includes the complete asset-tree digest, so changed bytes cannot reuse an ID.
MeshCore's existing Wasm font implementations currently use platform generic families; native
parity tests use the branded desktop faces. The two platforms therefore share components and
values but do not promise identical font pixels.
