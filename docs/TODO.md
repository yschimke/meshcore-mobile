# Meshcore — Project TODO

Tracked improvements and planned work. See `docs/PRINCIPLES.md` for
the reasoning behind many of these items.

## Architecture

- [ ] Replace `MeshcoreApp` singleton with dependency injection
  - Eliminate `MeshcoreApp.get()` calls scattered through UI code
  - Make controllers injectable for testing
  - Replace `GlobalScope.launch()` with scoped coroutines
- [x] Break `DeviceScreen.kt` (975 → 515 lines) into focused files
  - [x] Extract status views (`DeviceConnectStatus`, `DeviceStatusView`,
    `ConnectingCard`, `FailureCard`) to `DeviceStatusViews.kt` (231 lines)
  - [x] Move preview functions to `DeviceScreenPreviews.kt` (269 lines)
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

- [x] Core: unit tests for `MeshCoreClient` state machine (fake transport)
  - `FakeTransport`, `MeshCoreClientTest` (start, timeout, parse, seedFromCache)
- [ ] Core: integration test for client handshake + event parsing
- [ ] Core: test `MeshCoreManager` lifecycle (connect, disconnect, reconnect)
- [ ] Data: test `MeshcoreRepository` device merging and deduplication
- [x] Transport: test `StreamFrameCodec` edge cases (partial frames, junk recovery, oversized, reset)
- [ ] App: unit tests for `AppConnectionController`
- [ ] App: Compose screenshot tests for key UI states (empty, loaded, error)

## Error handling

- [x] Add logging to silent `runCatching` blocks in `AppConnectionController`
- [x] Surface stale-cache warnings to the user when Room queries fail
- [x] Add BLE reconnect with exponential backoff in `AppConnectionController`
  - Up to 5 retries, 2s–60s backoff + jitter, `Retrying` UI state
- [ ] Log or warn on protocol mismatches (unexpected frame types)

## Protocol

- [ ] Write a wire protocol spec for `meshcore-core`
  - Document frame format, command codes, response types
  - Currently scattered across `Codes.kt`, `CommandCode`, and parser comments
- [ ] Consider adding request/response correlation IDs
  - Currently matched by type only; misordering possible
- [x] Extract hardcoded timeouts into named constants
  - `PeriodicRefreshWorker`: `CONNECT_TIMEOUT_MS`, `DATA_WAIT_TIMEOUT_MS`
  - `AppConnectionController`: `connectTimeoutMs` already named; backoff constants added

## Mobile app — offline-first

- [x] Saved devices list loads from Room immediately on cold start
- [x] Device data seeded from Room cache on connect (`seedFromCache`)
- [x] Chat screens show persisted messages from Room, degrade to read-only offline
- [x] Widget shows cached data when offline, marked as disconnected
- [x] DeviceScreen shows cached contacts with subtle refresh bar while fetching live data
- [x] Enrich SavedDevicesPanel with cached device state (battery, contacts count)
- [x] Allow browsing cached contacts/channels for a saved device without connecting
  - `CachedDeviceScreen` reuses `DeviceBody` with Room data + "Cached data" banner
- [x] Add accessibility descriptions to icon-only UI elements
  (remaining `contentDescription = null` are all decorative/adjacent to text)
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
