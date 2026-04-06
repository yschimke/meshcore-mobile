# Meshcore — Project TODO

Tracked improvements and planned work. See `docs/PRINCIPLES.md` for
the reasoning behind many of these items.

## Architecture

- [ ] Replace `MeshcoreApp` singleton with dependency injection
  - Eliminate `MeshcoreApp.get()` calls scattered through UI code
  - Make controllers injectable for testing
  - Replace `GlobalScope.launch()` with scoped coroutines
- [ ] Break `DeviceScreen.kt` (865 lines) into focused files
  - Extract `ConnectedDevice` section to its own file
  - Extract `FailureCard`, `ConnectingCard` to shared components
  - Move preview functions to a separate preview file
- [ ] Enforce client lifecycle at compile time
  - Builder or state-machine API so `start()` must precede commands
  - Prevent calling `getContacts()` on an unstarted client

## gRPC service layer

- [ ] Define gRPC service wrapping `MeshCoreClient` API
- [ ] Build companion daemon that bridges serial/BLE to gRPC
- [ ] Enable non-Kotlin consumers (Python, Go, web) via generated stubs

## Testing

- [ ] Core: unit tests for `MeshCoreClient` state machine (fake transport)
- [ ] Core: integration test for client handshake + event parsing
- [ ] Core: test `MeshCoreManager` lifecycle (connect, disconnect, reconnect)
- [ ] Data: test `MeshcoreRepository` device merging and deduplication
- [ ] Transport: test `StreamFrameCodec` edge cases (partial frames, junk recovery)
- [ ] App: unit tests for `AppConnectionController`
- [ ] App: Compose screenshot tests for key UI states (empty, loaded, error)

## Error handling

- [ ] Add logging to silent `runCatching` blocks in `AppConnectionController`
- [ ] Surface stale-cache warnings to the user when Room queries fail
- [ ] Add BLE reconnect with exponential backoff in `MeshCoreManager`
- [ ] Log or warn on protocol mismatches (unexpected frame types)

## Protocol

- [ ] Write a wire protocol spec for `meshcore-core`
  - Document frame format, command codes, response types
  - Currently scattered across `Codes.kt`, `CommandCode`, and parser comments
- [ ] Consider adding request/response correlation IDs
  - Currently matched by type only; misordering possible
- [ ] Extract hardcoded timeouts (5s command, 20s BLE) into named constants

## Mobile app

- [ ] Offline-first: show cached data immediately, refresh in background
- [ ] Improve empty/loading/failure states for all list screens
- [ ] Add accessibility descriptions to icon-only UI elements
- [ ] Investigate message delivery confirmation UX

## Widgets and background

- [ ] Make periodic refresh interval configurable
- [ ] Add error indicator to widget when device is unreachable
- [ ] Test widget rendering across different sizes and themes
