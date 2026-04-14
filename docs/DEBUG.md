# Debug adb surface

Debug builds ship two adb-reachable entry points for exercising the app
without the UI:

- A **`dumpsys` interface** on `MeshcoreConnectionService` for introspection
  and radio commands (requires an active connection — i.e. the foreground
  service must be running).
- **Broadcast receivers** for triggering connect / disconnect / forget
  outside the UI (work whether the service is running or not).

Release builds do not register the init provider or the receivers and
`dump()` returns a short placeholder, so the surface is strictly
debug-only.

All destructive or stateful operations are gated by allowlists in
[`DebugAllowlists.kt`](../app/src/debug/kotlin/ee/schimke/meshcore/app/debug/DebugAllowlists.kt).
Defaults are conservative — broaden locally, don't commit broadening.

## Quick start

```sh
# Launch + connect (no UI interaction)
adb shell am start -n ee.schimke.meshcore/ee.schimke.meshcore.app.MainActivity
adb shell am broadcast -p ee.schimke.meshcore \
    -a ee.schimke.meshcore.DEBUG_CONNECT \
    --es ble C7:8D:8C:45:5F:78 --es label Kodu

# Wait ~10s for fetchAndPersist, then inspect
adb shell "dumpsys -t 15 activity service MeshcoreConnectionService"
adb shell "dumpsys -t 15 activity service MeshcoreConnectionService --help"
```

Throughout this doc the service component is referenced by its short
name `MeshcoreConnectionService`; the full component is
`ee.schimke.meshcore/ee.schimke.meshcore.app.service.MeshcoreConnectionService`.

Use `dumpsys -t <seconds>` to lift the default 10 s dumpsys watchdog
when running mesh operations. Internal per-verb timeouts stay under
6 s so a timeout prints cleanly instead of the caller seeing
`IOException: Timeout`.

## dumpsys verbs

All of the verbs below are passed as positional arguments after the
service name, e.g.

```
adb shell "dumpsys activity service MeshcoreConnectionService --rooms"
```

### Read-only

| Verb | Description |
|------|-------------|
| *(no args)* | Connection state + self / device / radio / battery / counts |
| `--contacts` | All contacts |
| `--rooms`, `--repeaters`, `--chats`, `--sensors` | Typed contact lists |
| `--contact <q>` | Full detail for one contact (name match first, then pubkey prefix) |
| `--channels` | Channel list |
| `--channel-messages <idx> [N]` | Last N messages on channel *idx* from the Room DB (default 20) |
| `--messages <contact>` | Direct messages cached for *contact* (live client state, not DB) |
| `--events [N]` | Last N `MeshEvent`s from the in-process ring buffer (default 20) |
| `--saved` | All devices in the Room DB, with favorite flag |
| `--help` | Verb reference |

### Gated — radio / mesh actions

Each verb checks a specific allowlist before transmitting. Refused
calls print the exact entry that's missing.

| Verb | Allowlist | Notes |
|------|-----------|-------|
| `--login <q> [pw]` | `DebugAllowlists.contacts` (contact name) | Rooms/repeaters only. Prints elapsed ms and `LoginSuccess` / `LoginFail`. |
| `--send-direct <q> <text…>` | `DebugAllowlists.contacts` | Remaining argv is joined with spaces, so unquoted text works. |
| `--send-channel <name-or-idx> <text…>` | `DebugAllowlists.sendChannelNames` | Accepts channel name (case-insensitive) or numeric index. |
| `--send-advert [--flood]` | *(none — safe to send)* | Flood mode clears path caches across the mesh. |
| `--set-advert-name <name>` | `DebugAllowlists.selfNames` | Renames the local radio. |
| `--reboot --confirm REBOOT_YES` | confirmation token | Device disconnects; reconnect via `DEBUG_CONNECT` when it comes back. |
| `--sync` | *(none)* | Drains queued device messages. |

### Notes on quoting

`adb shell` concatenates argv with spaces and the remote `sh` re-tokenises,
so names with spaces need double quoting:

```sh
adb shell "dumpsys activity service MeshcoreConnectionService --login 'Kodu Room' hunter2"
```

When a contact or channel name is awkward to quote, use its pubkey
prefix (hex) or numeric index instead — no quoting needed.

## Broadcasts

### `DEBUG_CONNECT` — start a connection

Gated by `DebugAllowlists.bleIdentifiers` or `DebugAllowlists.tcpTargets`
(as `"host:port"`).

```sh
# BLE
adb shell am broadcast -p ee.schimke.meshcore \
    -a ee.schimke.meshcore.DEBUG_CONNECT \
    --es ble C7:8D:8C:45:5F:78 --es label "Kodu"

# TCP
adb shell am broadcast -p ee.schimke.meshcore \
    -a ee.schimke.meshcore.DEBUG_CONNECT \
    --es tcp 192.168.1.42 --ei port 5000
```

Progress shows up under logcat tags `DebugConnect` and `MeshConnect`.

### `DEBUG_DISCONNECT` — cancel the active connection

```sh
adb shell am broadcast -p ee.schimke.meshcore \
    -a ee.schimke.meshcore.DEBUG_DISCONNECT
```

Equivalent to tapping the notification's Disconnect action.

### `DEBUG_FORGET` — remove a saved device

```sh
adb shell am broadcast -p ee.schimke.meshcore \
    -a ee.schimke.meshcore.DEBUG_FORGET \
    --es id "ble:C7:8D:8C:45:5F:78"
```

Use `--saved` (dumpsys) to see valid IDs.

## Architecture notes

- The debug code lives in `app/src/debug/kotlin/.../debug/` and is
  wired up through a tiny `DebugBridge` interface in main that
  production never references.
- [`DebugInit`](../app/src/debug/kotlin/ee/schimke/meshcore/app/debug/DebugInit.kt)
  is a no-op ContentProvider declared only in the debug manifest. It
  installs `DebugBridge.instance` before `Application.onCreate` runs.
- [`DebugEventBuffer`](../app/src/debug/kotlin/ee/schimke/meshcore/app/debug/DebugEventBuffer.kt)
  subscribes to `MeshCoreClient.events` whenever a connection is
  active; `--events` reads from its 100-entry ring buffer.
- `--login` / `--send-*` each run their suspend work on
  `Dispatchers.Default` inside a bounded `withTimeout`, so a hung
  radio never takes the whole dumpsys down with it.

## Adding a new verb

1. If read-only, add the handler to
   [`DebugDumpRead.kt`](../app/src/debug/kotlin/ee/schimke/meshcore/app/debug/DebugDumpRead.kt).
2. If it mutates state or transmits, add it to
   [`DebugDumpActions.kt`](../app/src/debug/kotlin/ee/schimke/meshcore/app/debug/DebugDumpActions.kt)
   and gate it against a new or existing entry in `DebugAllowlists`.
3. Wire the arg in the `when` in
   [`DebugDump.kt`](../app/src/debug/kotlin/ee/schimke/meshcore/app/debug/DebugDump.kt).
4. Add the row to the help block in the same file and to the table
   above.
