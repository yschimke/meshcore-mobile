package ee.schimke.meshcore.app.widget

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import ee.schimke.meshcore.app.connection.AppConnectionController
import ee.schimke.meshcore.app.connection.ConnectionUiState
import ee.schimke.meshcore.app.di.AssistedWorkerFactory
import ee.schimke.meshcore.app.di.WorkerKey
import ee.schimke.meshcore.data.repository.MeshcoreRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "MeshRefresh"
private const val DATA_WAIT_TIMEOUT_MS = 15_000L

/**
 * Periodic background worker that polls fresh battery and storage state from the favorite device
 * every 15 minutes when already connected, persists to Room, and lets [WidgetStateBridge] update
 * widgets.
 *
 * Scheduled via [scheduleIfFavoriteExists] when a favorite device is set. Cancelled via [cancel]
 * when the favorite is cleared.
 */
@AssistedInject
class PeriodicRefreshWorker(
  @Assisted context: Context,
  @Assisted params: WorkerParameters,
  private val repository: MeshcoreRepository,
  private val connectionController: AppConnectionController,
) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    val favorite = repository.observeFavorite().first()
    if (favorite == null) {
      Log.d(TAG, "No favorite device, skipping refresh")
      return Result.success()
    }

    // Only refresh if already connected — never initiate BLE connections
    // from background work (it triggers pairing prompts).
    if (connectionController.connectedDeviceId.value != favorite.id) {
      Log.d(TAG, "Periodic refresh: not connected to ${favorite.label}, skipping")
      return Result.success()
    }

    val client =
      (connectionController.state.value as? ConnectionUiState.Connected)?.client
        ?: return Result.success()
    withTimeoutOrNull(DATA_WAIT_TIMEOUT_MS) {
      try {
        val bat = client.getBatteryAndStorage()
        repository.updateBattery(favorite.id, bat, System.currentTimeMillis())
      } catch (t: Throwable) {
        currentCoroutineContext().ensureActive()
        Log.w(TAG, "Periodic refresh: battery fetch failed", t)
      }
    }

    Log.d(TAG, "Periodic refresh: done")
    return Result.success()
  }

  @AssistedFactory
  @WorkerKey(PeriodicRefreshWorker::class)
  @ContributesIntoMap(AppScope::class)
  fun interface Factory : AssistedWorkerFactory {
    override fun create(context: Context, params: WorkerParameters): PeriodicRefreshWorker
  }

  companion object {
    private const val WORK_NAME = "meshcore_periodic_refresh"

    fun scheduleIfFavoriteExists(context: Context) {
      val request =
        PeriodicWorkRequestBuilder<PeriodicRefreshWorker>(
            15,
            TimeUnit.MINUTES,
          )
          .setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build(),
          )
          .build()

      WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(
          WORK_NAME,
          ExistingPeriodicWorkPolicy.KEEP,
          request,
        )
      Log.d(TAG, "Periodic refresh scheduled (every 15min)")
    }

    fun cancel(context: Context) {
      WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
      Log.d(TAG, "Periodic refresh cancelled")
    }
  }
}
