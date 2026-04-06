package ee.schimke.meshcore.app.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ee.schimke.meshcore.app.MeshcoreApp
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * WorkManager worker that connects to the favorite device in the background,
 * waits for a snapshot update, then disconnects. Triggered by the Connection
 * Status widget's "refresh" tap.
 */
class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = MeshcoreApp.get()
        val favorite = app.repository.observeFavorite().first()
            ?: return Result.failure()

        // Only refresh if already connected — never initiate BLE connections
        // from background work (it triggers pairing prompts).
        if (app.connectionController.connectedDeviceId.value != favorite.id) {
            return Result.success()
        }

        // Wait for fresh data to arrive
        withTimeoutOrNull(15_000) {
            WidgetStateBridge.snapshot.filter { it.lastUpdatedMs != null }.first()
        }

        return Result.success()
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
