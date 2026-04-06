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
import ee.schimke.meshcore.app.ble.DeviceProximityCheck
import ee.schimke.meshcore.core.manager.ManagerState
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

private const val TAG = "MeshRefresh"
private const val CONNECT_TIMEOUT_MS = 30_000L
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

        // Don't reconnect if already connected to this device
        val alreadyConnected = app.connectionController.connectedDeviceId.value == favorite.id

        if (!alreadyConnected) {
            // Check if device is nearby before attempting connection
            if (!DeviceProximityCheck.isNearby(applicationContext, favorite)) {
                Log.d(TAG, "Periodic refresh: device not nearby, skipping")
                return Result.success()
            }
            Log.d(TAG, "Periodic refresh: connecting to ${favorite.label}")
            app.connectionController.requestReconnect(favorite)
        }

        // Wait for connection + data
        val connected = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            app.manager.state.filter { it is ManagerState.Connected }.first()
        }

        if (connected == null) {
            Log.d(TAG, "Periodic refresh: connection timeout")
            return Result.retry()
        }

        // Give time for fetchAndPersist to complete (it runs on the controller's scope)
        withTimeoutOrNull(DATA_WAIT_TIMEOUT_MS) {
            WidgetStateBridge.snapshot.filter { it.lastUpdatedMs != null }.first()
        }

        // Disconnect only if WE initiated the connection (not if user was already connected)
        if (!alreadyConnected) {
            Log.d(TAG, "Periodic refresh: disconnecting")
            app.connectionController.cancel()
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
