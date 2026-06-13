package ee.schimke.meshcore.app.connection

import android.util.Log
import ee.schimke.meshcore.core.client.MeshCoreClient
import ee.schimke.meshcore.data.repository.MeshcoreRepository
import ee.schimke.meshcore.data.repository.SavedTransport

private const val TAG = "MeshConnect"

/**
 * Resolves the canonical device id for a freshly connected radio.
 *
 * Devices are keyed by transport (e.g. `ble:…` vs `usb:…`), but the same
 * physical radio may be reached over more than one transport. When the
 * connected device's public key matches a record saved under a different
 * id, the two are merged so contacts/messages/channels stay shared.
 *
 * Pure repository logic with no Android dependencies — unit-testable
 * against a fake [MeshcoreRepository].
 */
class DeviceIdentityResolver(private val repository: MeshcoreRepository) {

  /**
   * Returns the id under which the connected device should be tracked,
   * merging into a pre-existing record when the public key matches.
   *
   * @param attemptId the transport-derived id of the current attempt
   * @param savedTransport the transport that was used to connect
   * @param client the connected client (its `selfInfo` carries the public key)
   */
  suspend fun resolveCanonicalId(
    attemptId: String,
    savedTransport: SavedTransport,
    client: MeshCoreClient,
  ): String {
    val selfInfo = client.selfInfo.value ?: return attemptId
    val existingId =
      runCatching { repository.findDeviceIdByPublicKey(selfInfo.publicKey) }
        .onFailure { Log.w(TAG, "findDeviceIdByPublicKey failed", it) }
        .getOrNull()
    if (existingId != null && existingId != attemptId) {
      Log.d(TAG, "Merging $attemptId into existing $existingId")
      runCatching { repository.mergeDevice(attemptId, existingId, savedTransport) }
        .onFailure { Log.w(TAG, "mergeDevice failed for $attemptId -> $existingId", it) }
      return existingId
    }
    return attemptId
  }
}
