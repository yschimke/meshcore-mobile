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
import ee.schimke.meshcore.app.MeshcoreApp
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

private const val TAG = "MeshRefresh"
private const val DATA_WAIT_TIMEOUT_MS = 15_000L

/**
 * Periodic background worker that connects to the favorite device every
 * 15 minutes, fetches fresh device info + contacts + channels, persists
 * to Room, updates widgets, and disconnects.
 *
 * Scheduled via [scheduleIfFavoriteExists] when a favorite device is set.
 * Cancelled via [cancel] when the favorite is cleared.
 */
class PeriodicRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = MeshcoreApp.get()
        val favorite = app.repository.observeFavorite().first()
        if (favorite == null) {
            Log.d(TAG, "No favorite device, skipping refresh")
            return Result.success()
        }

        // Only refresh if already connected — never initiate BLE connections
        // from background work (it triggers pairing prompts).
        if (app.connectionController.connectedDeviceId.value != favorite.id) {
            Log.d(TAG, "Periodic refresh: not connected to ${favorite.label}, skipping")
            return Result.success()
        }

        // Wait for fresh data to arrive
        withTimeoutOrNull(DATA_WAIT_TIMEOUT_MS) {
            WidgetStateBridge.snapshot.filter { it.lastUpdatedMs != null }.first()
        }

        Log.d(TAG, "Periodic refresh: done")
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "meshcore_periodic_refresh"

        fun scheduleIfFavoriteExists(context: Context) {
            val request = PeriodicWorkRequestBuilder<PeriodicRefreshWorker>(
                15, TimeUnit.MINUTES,
            ).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build(),
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
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
