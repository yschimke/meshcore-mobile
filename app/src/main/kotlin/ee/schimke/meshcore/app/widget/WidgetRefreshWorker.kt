package ee.schimke.meshcore.app.widget

import android.content.Context
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
import ee.schimke.meshcore.app.di.AssistedWorkerFactory
import ee.schimke.meshcore.app.di.WorkerKey
import ee.schimke.meshcore.data.repository.MeshcoreRepository
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * WorkManager worker that connects to the favorite device in the background, waits for a snapshot
 * update, then disconnects. Triggered by the Connection Status widget's "refresh" tap.
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

    // Wait for fresh data to arrive
    withTimeoutOrNull(15_000) {
      WidgetStateBridge.snapshot.filter { it.lastUpdatedMs != null }.first()
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
