package ee.schimke.meshcore.app.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ee.schimke.meshcore.app.MeshcoreApp
import ee.schimke.meshcore.app.ble.DeviceProximityCheck
import ee.schimke.meshcore.core.manager.ManagerState
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

        // Check if device is nearby before connecting
        if (!DeviceProximityCheck.isNearby(applicationContext, favorite)) {
            return Result.success() // Not nearby, skip silently
        }

        app.connectionController.requestReconnect(favorite)

        // Wait up to 30s for a connected state + first data
        withTimeoutOrNull(30_000) {
            app.manager.state.filter { it is ManagerState.Connected }.first()
            // Give widgets time to receive at least one event
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
