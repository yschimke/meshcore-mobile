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

- [x] Define gRPC binder service wrapping `MeshCoreClient` API
  - `meshcore-grpc-service` JVM module with proto + gRPC-Kotlin stubs
  - `MeshcoreBinderService` in app module, signature-permission protected
  - 9 RPCs: connection status, self info, contacts, channels, battery,
    send DM, send channel msg, subscribe connection status, subscribe messages
- [ ] Build companion daemon that bridges serial/BLE to gRPC
- [ ] Enable non-Kotlin consumers (Python, Go, web) via generated stubs
- [ ] Add client SDK module with `BinderChannelBuilder` helper for third-party apps
- [ ] Promote `MeshcoreBinderService` to foreground service for persistent background access
- [ ] Migrate to protobuf-gradle-plugin when AGP 9 support lands (google/protobuf-gradle-plugin#787)

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

## Wear OS companion

- [x] Add Horologist (data layer only) + Wear Compose M3 + wear-compose-remote dependencies
- [x] Create `wear` module skeleton with manifest and build config
- [x] Implement `MeshcoreWearDataService` (phone-side gRPC over Data Layer)
- [x] Implement `MeshcoreWearClient` (watch-side gRPC consumer via Horologist channel)
- [x] Build `MeshcoreWearTheme` (dark-only, teal, Wear Compose M3 — no Horologist Compose)
- [x] Build `StatusScreen` with connection, battery, radio info (`TransformingLazyColumn`)
- [x] Build `ContactsScreen` and `QuickReplyScreen` for messaging
- [x] Build Wear widget with `wear-compose-remote` (Remote Material 3 components)
- [ ] Add `MeshMessageNotifier` for MessagingStyle notifications with inline reply
- [x] Add Wear OS section to STYLEGUIDE.md
- [ ] Test on Wear OS emulator and physical device

## Widgets and background

- [ ] Make periodic refresh interval configurable
- [ ] Add error indicator to widget when device is unreachable
- [ ] Test widget rendering across different sizes and themes
- [ ] Consider `wear-compose-remote` for watch widget (matches phone's Remote Compose pattern)
