package ee.schimke.meshcore.app.connection

import ee.schimke.meshcore.core.client.MeshCoreClient
import ee.schimke.meshcore.data.repository.SavedDevice
import ee.schimke.meshcore.data.repository.SavedTransport

/**
 * The OS-facing side effects of a connection lifecycle, kept behind an
 * interface so [AppConnectionController] stays pure connection-state +
 * delegation and the effects can be verified in isolation (a fake can
 * assert "on connected → start persister" with no real transport).
 *
 * Implementations own things with their own lifecycle: the foreground
 * service, the message persister, background refresh scheduling and
 * device-presence observation.
 */
interface ConnectionSideEffects {
  /**
   * Invoked once a device is connected and its canonical [deviceId] is
   * resolved. Should seed/persist device data and start any background
   * work (foreground service, message persister, data fetch).
   */
  suspend fun onConnected(
    deviceId: String,
    label: String,
    savedTransport: SavedTransport,
    client: MeshCoreClient,
  )

  /** Invoked when the connection drops, fails, or is cancelled. */
  fun onDisconnected()

  /**
   * Invoked when the favorite device changes, so presence observation
   * and periodic refresh can be (re)scheduled. [old] may equal [new].
   */
  suspend fun onFavoriteChanged(old: SavedDevice?, new: SavedDevice?)
}

/** No-op side effects, for tests and for constructing the controller without a Context. */
object NoopConnectionSideEffects : ConnectionSideEffects {
  override suspend fun onConnected(
    deviceId: String,
    label: String,
    savedTransport: SavedTransport,
    client: MeshCoreClient,
  ) {}

  override fun onDisconnected() {}

  override suspend fun onFavoriteChanged(old: SavedDevice?, new: SavedDevice?) {}
}
