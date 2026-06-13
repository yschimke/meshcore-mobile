package ee.schimke.meshcore.app.connection

import android.content.Context
import android.util.Log
import ee.schimke.meshcore.app.ble.DevicePresenceManager
import ee.schimke.meshcore.app.service.MeshcoreConnectionService
import ee.schimke.meshcore.app.widget.PeriodicRefreshWorker
import ee.schimke.meshcore.core.client.MeshCoreClient
import ee.schimke.meshcore.data.repository.MeshcoreRepository
import ee.schimke.meshcore.data.repository.SavedDevice
import ee.schimke.meshcore.data.repository.SavedTransport
import ee.schimke.meshcore.data.repository.toBattery
import ee.schimke.meshcore.data.repository.toDeviceInfo
import ee.schimke.meshcore.data.repository.toRadio
import ee.schimke.meshcore.data.repository.toSelfInfo
import ee.schimke.meshcore.data.sync.MessagePersister
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val TAG = "MeshConnect"

/**
 * Android implementation of [ConnectionSideEffects]: seeds/persists Room
 * data on connect, runs the foreground service, message persister and
 * data refresh, and wires background refresh + presence observation when
 * the favorite changes.
 */
class AndroidConnectionSideEffects(
  private val appContext: Context,
  private val repository: MeshcoreRepository,
  private val scope: CoroutineScope,
  private val onWarning: (String) -> Unit = {},
) : ConnectionSideEffects {

  @Volatile private var persisterJob: Job? = null

  override suspend fun onConnected(
    deviceId: String,
    label: String,
    savedTransport: SavedTransport,
    client: MeshCoreClient,
  ) {
    // Seed the live client from Room so the UI shows cached data immediately.
    runCatching {
        val state = repository.getDeviceState(deviceId)
        val contacts = repository.getContacts(deviceId)
        val channels = repository.getChannels(deviceId)
        client.seedFromCache(
          selfInfo = state?.toSelfInfo(),
          contacts = contacts,
          battery = state?.toBattery(),
          radio = state?.toRadio(),
          deviceInfo = state?.toDeviceInfo(),
          channels = channels,
        )
      }
      .onFailure {
        Log.w(TAG, "seedFromCache failed for $deviceId", it)
        onWarning("Cached data may be stale")
      }
    runCatching {
        repository.upsertDevice(
          id = deviceId,
          label = client.selfInfo.value?.name ?: label,
          transport = savedTransport,
        )
      }
      .onFailure { Log.w(TAG, "upsertDevice failed for $deviceId", it) }

    MeshcoreConnectionService.start(appContext, label)

    // Persist incoming messages in the background.
    persisterJob?.cancel()
    persisterJob =
      scope.launch {
        val persister =
          MessagePersister(
            repository = repository,
            deviceId = deviceId,
            contactResolver = { prefix ->
              val hex = prefix.toHex()
              client.contacts.value
                .firstOrNull { it.publicKey.toHex().startsWith(hex) }
                ?.publicKey
                ?.toHex()
            },
          )
        persister.collect(client.events)
      }
    // Fetch fresh data and persist to Room.
    scope.launch { fetchAndPersist(deviceId, client) }
  }

  override fun onDisconnected() {
    persisterJob?.cancel()
    MeshcoreConnectionService.stop(appContext)
  }

  override suspend fun onFavoriteChanged(old: SavedDevice?, new: SavedDevice?) {
    if (old != null) DevicePresenceManager.stopObserving(appContext, old)
    if (new != null) {
      PeriodicRefreshWorker.scheduleIfFavoriteExists(appContext)
      DevicePresenceManager.startObserving(appContext, new)
    } else {
      PeriodicRefreshWorker.cancel(appContext)
    }
  }

  private suspend fun fetchAndPersist(deviceId: String, client: MeshCoreClient) {
    val now = System.currentTimeMillis()
    Log.d(TAG, "fetchAndPersist: starting for $deviceId")

    client.selfInfo.value?.let {
      Log.d(TAG, "fetchAndPersist: persisting selfInfo name='${it.name}'")
      repository.updateSelfInfo(deviceId, it, now)
    }
    runCatching {
        val bat = client.getBatteryAndStorage()
        Log.d(TAG, "fetchAndPersist: battery ${bat.millivolts}mV")
        repository.updateBattery(deviceId, bat, now)
      }
      .onFailure { Log.w(TAG, "fetchAndPersist: battery fetch failed", it) }
    runCatching {
        val radio = client.getRadioSettings()
        Log.d(TAG, "fetchAndPersist: radio ${radio.frequencyHz}Hz")
        repository.updateRadio(deviceId, radio, now)
      }
      .onFailure { Log.w(TAG, "fetchAndPersist: radio fetch failed", it) }
    client.device.value?.let { repository.updateDeviceInfo(deviceId, it, now) }

    Log.d(TAG, "fetchAndPersist: fetching contacts")
    runCatching { client.getContacts() }
      .onFailure { Log.w(TAG, "fetchAndPersist: contacts fetch failed", it) }
    repository.replaceContacts(deviceId, client.contacts.value, now)

    Log.d(TAG, "fetchAndPersist: fetching channels")
    runCatching { client.getChannels() }
      .onFailure { Log.w(TAG, "fetchAndPersist: channels fetch failed", it) }
    repository.replaceChannels(deviceId, client.channels.value, now)
    Log.d(TAG, "fetchAndPersist: done")
  }
}
