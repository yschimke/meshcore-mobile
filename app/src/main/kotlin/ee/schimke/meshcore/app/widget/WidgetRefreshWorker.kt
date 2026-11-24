package ee.schimke.meshcore.app.widget

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "MeshWidgetRefresh"

/**
 * WorkManager worker that polls fresh battery/storage state from the connected favorite device and
 * persists it so widgets refresh. Triggered by the Connection Status widget's "refresh" tap.
 */
@AssistedInject
class WidgetRefreshWorker(
  @Assisted context: Context,
  @Assisted params: WorkerParameters,
  private val repository: MeshcoreRepository,
  private val connectionController: AppConnectionController,
) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    val favorite = repository.observeFavorite().first() ?: return Result.failure()

    // Only refresh if already connected — never initiate BLE connections
    // from background work (it triggers pairing prompts).
    if (connectionController.connectedDeviceId.value != favorite.id) {
      return Result.success()
    }

    val client =
      (connectionController.state.value as? ConnectionUiState.Connected)?.client
        ?: return Result.success()
    withTimeoutOrNull(15_000) {
      try {
        val bat = client.getBatteryAndStorage()
        repository.updateBattery(favorite.id, bat, System.currentTimeMillis())
      } catch (t: Throwable) {
        currentCoroutineContext().ensureActive()
        Log.w(TAG, "Widget refresh: battery fetch failed", t)
      }
    }

    return Result.success()
  }

  @AssistedFactory
  @WorkerKey(WidgetRefreshWorker::class)
  @ContributesIntoMap(AppScope::class)
  fun interface Factory : AssistedWorkerFactory {
    override fun create(context: Context, params: WorkerParameters): WidgetRefreshWorker
  }

  companion object {
    private const val WORK_NAME = "widget_refresh"

    fun enqueue(context: Context) {
      val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
      WorkManager.getInstance(context)
        .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
  }
}
