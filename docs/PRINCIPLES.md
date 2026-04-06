# Meshcore Project Principles

This document describes the design philosophy, architecture, and goals
of the Meshcore project. It complements `docs/STYLEGUIDE.md` (visual
language) and focuses on the structural and product decisions that
shape how we build.

---

## 1. KMP library first, apps second

The core value of this project is a **Kotlin Multiplatform library**
that any app can use to talk to MeshCore radios. The Android app is an
important consumer of that library, but it is not the library.

### Module layering

```
meshcore-core          Pure Kotlin. Protocol, models, client, manager.
meshcore-data          Room database + repository. Offline cache layer.
meshcore-components    Reusable Compose UI (cards, chat bubbles, lists).
meshcore-transport-*   One module per physical link (BLE, TCP, USB).
meshcore-devices-proto Proto3 definitions for DataStore serialization.
───────────────────────────────────────────────────────────────────────
app                    Android sample app — consumes all of the above.
meshcore-cli           JVM CLI client (Clikt) — consumes core + TCP.
meshcore-tui           JVM terminal dashboard (Mordant) — same.
```

Every module below the line depends only on modules above it. The
library modules never import from `app`.

### The Transport contract

The entire multi-transport story hangs on one 27-line interface
(`meshcore-core/.../Transport.kt`):

```kotlin
interface Transport {
    val state: StateFlow<TransportState>
    val incoming: SharedFlow<ByteString>
    suspend fun connect()
    suspend fun send(frame: ByteString)
    suspend fun close()
}
```

BLE, TCP, and USB each implement this with completely different I/O
strategies (Kable characteristic subscriptions, Ktor stream sockets,
CDC-ACM serial ports), but `MeshCoreClient` never knows or cares.
Adding a new transport means writing one class and wiring it into the
scanner — zero changes to protocol code.

### Easy consumption pattern

The CLI and TUI show how thin a consumer can be:

```kotlin
val transport = TcpTransport(host, port)
val client = MeshCoreClient(transport, scope)
client.start()                      // handshake + event loop
client.getContacts()                // request/response
client.directMessages.collect { }   // reactive stream
```

Three lines to connect, one call per command, StateFlows for
everything reactive. If you can create a Transport and a
CoroutineScope you can build a MeshCore app.

**Where we've failed:** The library is easy to use but hard to use
*correctly*. There is no compile-time guarantee that `start()` is
called before `getContacts()`, or that `fetchAndPersist()` runs after
connection. A builder or state-machine API that enforces the lifecycle
would prevent a class of bugs.

---

## 2. User-focused mobile app

The Android app targets a specific user: someone who wants to **send
and receive messages** over a mesh radio. It is not a network admin
tool, a firmware flasher, or a full-featured dashboard. Features are
included only if they serve the messaging use case.

### What the app does

- Scan and connect to devices (BLE, USB)
- Show device status (battery, radio, contacts, channels)
- Send and receive direct messages and channel messages
- Persist message history offline (Room)
- Background refresh and home-screen widget (Glance)
- Detect nearby devices via BLE advertisements

### What the app deliberately omits

- Node configuration / firmware update
- Network topology visualization
- Admin operations (channel management, repeater config)
- Multi-device simultaneous connections

This is an intentional scope boundary. Admin features belong in a
separate tool (the CLI, or a future admin app) that can reuse the
same KMP library.

### Architecture pattern

The app follows **unidirectional data flow** with a stateful
controller and stateless Compose UI:

```
MeshcoreApp (singleton, owns graph)
  └─ AppConnectionController (owns ConnectionUiState)
       └─ MeshCoreManager → MeshCoreClient → Transport
```

UI screens observe `ConnectionUiState` (a sealed class:
`Idle | Connecting | Connected | Failed`) and render accordingly.
Every screen has a stateful entry point that wires callbacks and a
stateless body that takes plain data:

```kotlin
// Stateful — observes state, dispatches actions
@Composable fun DeviceScreen(onNavigate: (Route) -> Unit) { ... }

// Stateless — pure rendering, previewable
@Composable fun DeviceBody(state: DeviceUiState, ...) { ... }
```

This split is enforced by convention (see `docs/STYLEGUIDE.md` §
Stateless / Stateful split) and makes every screen previewable with
fake data.

**Where we've failed:**
- `DeviceScreen.kt` is 865 lines — too large for one file. The
  connected-device UI, status cards, failure cards, and 165 lines of
  preview functions should be extracted into separate files.
- `MeshcoreApp` is a god-object singleton that owns the database,
  repository, controller, theme preferences, widget bridge, and
  presence manager. It uses `GlobalScope.launch()` in `onCreate()`.
  This should be replaced with proper DI (Hilt or manual injection)
  so components are testable and lifecycles are explicit.
- Hidden dependencies are everywhere: `MeshcoreApp.get().connectionController`
  appears deep in UI code with no way to inject a mock.

---

## 3. Simplify the task for other apps

The library exists so that building a MeshCore app is a matter of
picking a transport and calling methods — not parsing binary frames or
managing BLE characteristics.

### What the library handles for you

| Concern | Where |
|---|---|
| Binary frame encoding/decoding | `meshcore-core` Frames.kt, Parsers.kt |
| Stream reassembly (TCP, USB) | `StreamFrameCodec` |
| Connection lifecycle + handshake | `MeshCoreManager`, `MeshCoreClient.start()` |
| Reactive state (contacts, messages) | `MeshCoreClient` StateFlows |
| Offline persistence + device merging | `meshcore-data` Repository |
| Reusable UI components | `meshcore-components` |

### What other apps still need to do

- Choose and instantiate a Transport
- Manage their own CoroutineScope lifecycle
- Handle platform permissions (BLE, USB)
- Decide on their own navigation and state management
- Implement their own error UX

### The Proto layer

`meshcore-devices-proto` defines Protocol Buffer messages for
persisting device state in DataStore. This is *not* a gRPC service
definition — the radio protocol is a custom binary format, not
protobuf-over-wire. The protos serve as a stable serialization
contract for cached device snapshots, with staleness timestamps
(`battery_at_ms`, `radio_at_ms`) so consumers know how fresh data is.

A gRPC service layer is planned, which will wrap the client API for
non-Kotlin consumers (Python scripts, web UIs, Go services). The CLI
already demonstrates the pattern — connect via TCP bridge — and gRPC
will formalize it into a stable service contract.

**Where we've failed:**
- The binary protocol has no formal specification. Frame types are
  scattered across `Codes.kt`, `CommandCode` enum, and inline comments
  in parsers. A `.proto` or `.fbs` definition of the wire format would
  make it possible to generate parsers for other languages.

---

## 4. Demonstrate modern design and Android architecture

The app serves as a reference for how to build with current Android
and KMP tooling. We pick technologies for what they teach, not just
what they do.

### Technologies we showcase

| Area | Choice | Why |
|---|---|---|
| Navigation | `androidx.navigation3` with `@Serializable` routes | Type-safe, survives process death |
| UI | Compose Material 3 with dynamic color | Current M3 spec, theme-driven |
| Persistence | Room (KMP) + DataStore (Proto) | Multiplatform DB, structured prefs |
| Background | WorkManager + Glance widgets | Jetpack-standard scheduling |
| Networking | Ktor (TCP), Kable (BLE) | KMP-native, coroutine-first |
| Build | Version catalogs, KSP, AGP 9 | Current Gradle best practices |
| CLI | Clikt + Mordant + GraalVM native-image | Modern JVM CLI tooling |
| Kotlin | 2.3.20, jvmToolchain(21), coroutines 1.10 | Latest stable |

### Patterns worth copying

1. **Sealed state classes.** `ConnectionUiState`, `TransportState`,
   `ManagerState` — exhaustive `when` ensures every state is handled.
2. **StateFlow everywhere.** The client exposes reactive streams that
   Compose collects directly. No LiveData, no RxJava, no callbacks.
3. **Stateless composables.** Every card and list item is a pure
   function of its parameters. State is lifted to screen level.
4. **Transport-agnostic protocol.** One client implementation works
   with any physical link. New transports are additive.
5. **Preview discipline.** Every data-driven composable has empty, few,
   many, loading, and failure previews in both light and dark themes.

### Where we've failed

**Testing:**
- The entire project has only two test files, both in
  `meshcore-core/commonTest/` (frame codec and parser tests).
- Zero tests for: transport implementations, client state machine,
  manager lifecycle, connection controller, repository logic, or any
  UI composable.
- A "reference" project should demonstrate testing patterns at every
  layer: unit tests for protocol parsing, integration tests for the
  client handshake (with a fake transport), and screenshot tests for
  key UI states.

**Error handling:**
- `AppConnectionController` wraps Room queries in `runCatching` and
  silently discards failures. A cache miss shouldn't crash, but it
  should log — or better, surface to the user that cached data may be
  stale.
- `MeshCoreManager` has no retry logic. A flaky BLE connection fails
  once and stays failed until the user manually reconnects. Exponential
  backoff with a retry limit would match user expectations for
  wireless links.

**Request/response correlation:**
- The protocol has no message IDs. Responses are matched to requests
  by type, not by correlation ID. If the device sends an unexpected
  frame, the wrong consumer may match it. This is a protocol-level
  limitation but the client could defend against it with sequence
  numbers or at least logging mismatches.

**Hardcoded values:**
- BLE connection timeout (20s), periodic refresh interval, and command
  timeouts (5s) are all literals in source. These should be
  configurable — or at minimum, named constants with documentation
  explaining why the value was chosen.

---

## Summary of improvement priorities

| Priority | Area | Action |
|---|---|---|
| High | Testing | Add unit tests for core client, transport fakes, UI screenshot tests |
| High | DI | Replace `MeshcoreApp` singleton with proper dependency injection |
| Medium | File size | Break `DeviceScreen.kt` into focused files |
| Medium | Error handling | Add logging to silent `runCatching` blocks; add BLE retry logic |
| Medium | Documentation | Write a wire protocol spec (even informal) for `meshcore-core` |
| Low | Configuration | Extract hardcoded timeouts into named, documented constants |
