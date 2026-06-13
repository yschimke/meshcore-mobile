# MeshCore wire protocol

Informal but complete reference for the binary protocol spoken between a
companion app and a MeshCore radio, as implemented by `meshcore-core`.
It is the prose companion to the source of truth:

| Concern | File |
|---|---|
| Command / response / push opcodes | [`protocol/Codes.kt`](../meshcore-core/src/commonMain/kotlin/ee/schimke/meshcore/core/protocol/Codes.kt) |
| Outbound frame builders | [`protocol/Frames.kt`](../meshcore-core/src/commonMain/kotlin/ee/schimke/meshcore/core/protocol/Frames.kt) |
| Inbound frame parsers | [`protocol/Parsers.kt`](../meshcore-core/src/commonMain/kotlin/ee/schimke/meshcore/core/protocol/Parsers.kt) |
| Stream framing (USB/TCP) | [`protocol/StreamFrameCodec.kt`](../meshcore-core/src/commonMain/kotlin/ee/schimke/meshcore/core/protocol/StreamFrameCodec.kt) |
| Constants | [`protocol/Constants.kt`](../meshcore-core/src/commonMain/kotlin/ee/schimke/meshcore/core/protocol/Constants.kt) |

If this document and the parser ever disagree, **the parser wins** — fix
this document.

> **No correlation IDs.** The protocol has no request/response message
> IDs. Replies are matched to requests *by frame type only*. The client
> serialises commands behind a mutex and waits for the first reply of the
> expected type, which works because companion sessions are effectively
> single-threaded, but it cannot disambiguate two in-flight requests that
> expect the same reply type. Adding correlation IDs is a firmware-level
> change tracked separately (issue 90, out of scope).

---

## 1. Conventions

- **Endianness:** all multi-byte integers are **little-endian**.
- **Strings:** UTF-8. Two encodings appear:
  - *C-string* — bytes followed by a single `0x00` terminator. Used for
    the trailing field of a frame (`readCStringRemaining`) and for some
    embedded fields.
  - *Fixed C-string* — a fixed-width field, NUL-padded; the string is the
    bytes up to the first `0x00` (`readCStringFixed`).
- **Public keys:** 32 bytes (`PUB_KEY_SIZE`). A **prefix** is the first 6
  bytes (`PUB_KEY_PREFIX_SIZE`), used to address contacts compactly.
- **Timestamps:** unsigned 32-bit little-endian Unix epoch **seconds**.
- **Lat/Lon:** signed 32-bit little-endian, scaled by `1_000_000`
  (degrees × 1e6).

### Sizes (`MeshCoreConstants`)

| Name | Value | Meaning |
|---|---|---|
| `PUB_KEY_SIZE` | 32 | Full public key |
| `PUB_KEY_PREFIX_SIZE` | 6 | Addressing prefix |
| `MAX_PATH_SIZE` | 64 | Routing path field width |
| `MAX_NAME_SIZE` | 32 | Contact/advert name field width |
| `MAX_FRAME_SIZE` | 172 | Max stream payload |
| `MAX_TEXT_PAYLOAD_BYTES` | 160 | Max outbound message text |
| `APP_PROTOCOL_VERSION` | 3 | Sent in `AppStart` |

---

## 2. Framing

### 2.1 Frame payload

Every frame — in either direction — is:

```
[code:u8][body…]
```

`code` selects the command/response/push; `body` is opcode-specific
(sections 4–6). A frame with an empty body is just the single `code` byte.

### 2.2 Transport framing

How that payload is delimited on the wire depends on the link:

- **BLE (Nordic UART Service):** each GATT write (app→device) and each
  notification (device→app) carries exactly one complete frame payload.
  No extra framing — see `BLE_*_UUID` in `Constants.kt`.
- **Stream transports (USB CDC-ACM, TCP):** payloads are length-prefixed
  (`StreamFrameCodec`):

  ```
  [start:u8][len:u16le][payload… (len bytes)]
  ```

  | `start` | Direction |
  |---|---|
  | `0x3C` (`STREAM_TX_START`) | App → Device |
  | `0x3E` (`STREAM_RX_START`) | Device → App |

  `len` is the payload length and must be ≤ `MAX_FRAME_SIZE` (172); the
  decoder resyncs by dropping a byte on a bad start marker or an oversized
  length, so junk between frames is tolerated.

---

## 3. Opcode summary

Opcodes are a single byte. Commands and responses share the low range and
are disambiguated by direction; pushes have the top bit set (`0x80+`).

- **Commands** (App → Device): `CommandCode`
- **Responses** (Device → App, synchronous reply to a command):
  `ResponseCode`
- **Pushes** (Device → App, asynchronous/unsolicited): `PushCode`

Any frame whose code is unknown, or whose body fails to parse, is surfaced
to consumers as `MeshEvent.Raw(code, body)` rather than dropped. The
client logs these as warnings (unexpected-frame detection).

---

## 4. Commands (App → Device)

Codes from `CommandCode`. The **Body** column describes bytes *after* the
opcode. Rows marked *(defined, not emitted)* have an opcode reserved in
`CommandCode` but no builder in `Frames.kt` yet.

| Code | Name | Body |
|---|---|---|
| `0x01` | `AppStart` | `[ver:u8][reserved×6 = 0][appName: c-string]` |
| `0x02` | `SendTextMessage` | `[txt_type:u8][attempt:u8][timestamp:u32le][dest_prefix×6][text: c-string]` |
| `0x03` | `SendChannelTextMessage` | `[txt_type:u8][channel_idx:u8][timestamp:u32le][text: c-string]` |
| `0x04` | `GetContacts` | *(empty)* or `[since:u32le]` for a delta query |
| `0x05` | `GetDeviceTime` | *(empty)* |
| `0x06` | `SetDeviceTime` | `[epoch:u32le]` |
| `0x07` | `SendSelfAdvert` | `[flood:u8]` (1 = flood, 0 = zero-hop) |
| `0x08` | `SetAdvertName` | `[name bytes]` (raw, truncated to `MAX_NAME_SIZE − 1`; no terminator) |
| `0x09` | `AddUpdateContact` | *(defined, not emitted)* |
| `0x0A` | `SyncNextMessage` | *(empty)* — pull one queued message |
| `0x0B` | `SetRadioParams` | `[freq:u32le][bw:u32le][sf:u8][cr:u8]` (Hz, Hz, spreading factor, coding rate) |
| `0x0C` | `SetRadioTxPower` | `[dbm:i8]` |
| `0x0D` | `ResetPath` | *(defined, not emitted)* |
| `0x0E` | `SetAdvertLatLon` | `[lat:i32le][lon:i32le]` (degrees × 1e6) |
| `0x0F` | `RemoveContact` | *(defined, not emitted)* |
| `0x10` | `ShareContact` | *(defined, not emitted)* |
| `0x11` | `ExportContact` | *(defined, not emitted)* |
| `0x12` | `ImportContact` | *(defined, not emitted)* |
| `0x13` | `Reboot` | *(empty)* |
| `0x14` | `GetBatteryAndStorage` | *(empty)* |
| `0x16` | `DeviceQuery` | *(empty)* — request `DeviceInfo` |
| `0x1A` | `SendLogin` | `[recipient pubkey×32][password: c-string]` |
| `0x1D` | `Logout` | *(defined, not emitted)* |
| `0x1E` | `GetContactByKey` | *(defined, not emitted)* |
| `0x1F` | `GetChannel` | `[channel_idx:u8]` |
| `0x20` | `SetChannel` | `[channel_idx:u8][name×32 NUL-padded][psk×16]` |
| `0x33` | `FactoryReset` | *(defined, not emitted)* |
| `0x39` | `GetRadioSettings` | *(empty)* |

`txt_type` is a `TextType`: `0x00` Plain, `0x01` CliData.

---

## 5. Responses (Device → App)

Codes from `ResponseCode`. Rows marked *(passed through as `Raw`)* are
recognised opcodes that have no dedicated parser; the client receives them
as `MeshEvent.Raw`.

| Code | Name | Body |
|---|---|---|
| `0x00` | `Ok` | *(empty)* |
| `0x01` | `Err` | `[error_code:u8]` (optional; absent ⇒ 0) |
| `0x02` | `ContactsStart` | *(empty)* — begins a contact list |
| `0x03` | `Contact` | see §5.1 |
| `0x04` | `EndOfContacts` | *(empty)* — ends a contact list |
| `0x05` | `SelfInfo` | see §5.2 |
| `0x06` | `Sent` | `[is_flood:u8][ack_hash:u32le][est_timeout:u32le]` |
| `0x07` | `ContactMessageV1` | *(legacy; passed through as `Raw`)* |
| `0x08` | `ChannelMessageV1` | *(legacy; passed through as `Raw`)* |
| `0x09` | `CurrentTime` | `[epoch:u32le]` |
| `0x0A` | `NoMoreMessages` | *(empty)* — message queue drained |
| `0x0B` | `ExportContact` | *(passed through as `Raw`)* |
| `0x0C` | `BatteryAndStorage` | `[millivolts:u16le][storage_used_kb:u32le][storage_total_kb:u32le]` |
| `0x0D` | `DeviceInfo` | `[proto_ver:u8][max_contacts:u8 (×2)][max_channels:u8]` |
| `0x0E` | `PrivateKey` | *(passed through as `Raw`)* |
| `0x0F` | `Disabled` | *(passed through as `Raw`)* |
| `0x10` | `ContactMessageV3` | see §5.3 |
| `0x11` | `ChannelMessageV3` | see §5.4 |
| `0x12` | `ChannelInfo` | `[idx:u8][name×32 fixed c-string][psk×16]` |
| `0x19` | `RadioSettings` | `[freq:i32le][bw:i32le][sf:u8][cr:u8]` |

> **`DeviceInfo.max_contacts`** is transmitted as a single byte and the
> parser multiplies it by 2 to get the real capacity.

### 5.1 `Contact` (`0x03`)

```
[pubkey×32]
[type:u8]                 # ContactType: 1 CHAT, 2 REPEATER, 3 ROOM, 4 SENSOR
[flags:u8]
[path_len:u8]            # 0xFF ⇒ "no path" (decoded as -1)
[path×64]               # only the first path_len bytes are meaningful
[name×32]               # fixed c-string (NUL-padded)
[last_advert:u32le]     # epoch seconds
[lat:i32le]             # × 1e6
[lon:i32le]             # × 1e6
[last_mod:u32le]        # epoch seconds
```

### 5.2 `SelfInfo` (`0x05`)

```
[adv_type:u8]
[tx_power:i8]
[max_power:i8]
[pubkey×32]
[lat:i32le]             # × 1e6
[lon:i32le]             # × 1e6
[multi_acks:u8]
[advert_location_policy:u8]
[telemetry_flags:u8]
[manual_add_contacts:u8]
[freq:i32le]            # Hz
[bw:i32le]              # Hz
[sf:u8]                 # spreading factor
[cr:u8]                 # coding rate
[name: c-string]        # runs to end of frame
```

`SelfInfo` is the handshake acknowledgement: the client's `start()` blocks
until this arrives.

### 5.3 `ContactMessageV3` (`0x10`)

```
[snr:i8]
[reserved:u16]          # 2 bytes, skipped
[sender_prefix×6]
[path_len:u8]           # 0xFF ⇒ -1
[txt_type:u8]           # TextType
[timestamp:u32le]
[text: c-string]        # runs to end of frame
```

### 5.4 `ChannelMessageV3` (`0x11`)

```
[snr:i8]
[reserved:u16]          # 2 bytes, skipped
[channel_idx:u8]
[path_len:u8]           # 0xFF ⇒ -1
[txt_type:u8]           # TextType
[timestamp:u32le]
[text: c-string]        # runs to end of frame
```

---

## 6. Pushes (Device → App, asynchronous)

Codes from `PushCode` — top bit set. Rows marked *(passed through as
`Raw`)* are recognised but not yet modelled.

| Code | Name | Body |
|---|---|---|
| `0x80` | `Advert` | `[pubkey×32]` |
| `0x81` | `PathUpdated` | `[pubkey×32]` |
| `0x82` | `SendConfirmed` | `[ack_hash:u32le][round_trip:u32le]` |
| `0x83` | `MessagesWaiting` | *(empty)* — prompt to `SyncNextMessage` |
| `0x84` | `RawData` | *(passed through as `Raw`)* |
| `0x85` | `LoginSuccess` | `[permissions:u8 (bit 0 = is_admin)][pubkey_prefix×6]` |
| `0x86` | `LoginFail` | `[reserved:u8][pubkey_prefix×6]` |
| `0x87` | `StatusResponse` | *(passed through as `Raw`)* |
| `0x88` | `LogRxData` | *(passed through as `Raw`)* |
| `0x89` | `TraceData` | *(passed through as `Raw`)* |
| `0x8A` | `NewAdvert` | `[pubkey×32]` |

---

## 7. Typical exchanges

**Handshake** (`MeshCoreClient.start`):

```
App → AppStart, DeviceQuery, GetBatteryAndStorage, GetRadioSettings
Dev → SelfInfo            (unblocks start)
Dev → DeviceInfo, BatteryAndStorage, RadioSettings   (async, fill StateFlows)
```

**Fetch contacts** (`getContacts`):

```
App → GetContacts [since?]
Dev → ContactsStart
Dev → Contact × N
Dev → EndOfContacts      (unblocks the call)
```

**Send a direct message** (`sendText`):

```
App → SendTextMessage
Dev → Sent               (ack hash + estimated timeout)
Dev → SendConfirmed      (later push, when the ack returns from the mesh)
```

**Login to a repeater/room** (`login`):

```
App → SendLogin
Dev → LoginSuccess | LoginFail   (push, matched by pubkey prefix)
```

**Drain the queue** (`syncMessages`): repeat `SyncNextMessage` until
`NoMoreMessages`; each iteration yields a `ContactMessageV3` /
`ChannelMessageV3`.
