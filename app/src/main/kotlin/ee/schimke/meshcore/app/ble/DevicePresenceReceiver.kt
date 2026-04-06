package ee.schimke.meshcore.app.ble

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import ee.schimke.meshcore.app.MeshcoreApp
import ee.schimke.meshcore.data.repository.SavedTransport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private const val TAG = "MeshPresence"
private const val MIN_SYNC_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes
private const val ACTION_DEVICE_APPEARING =
    "android.companion.CompanionDeviceManager.COMPANION_DEVICE_APPEARING"

/**
 * Receives [CompanionDeviceManager.ACTION_COMPANION_DEVICE_APPEARING] when
 * the favorite BLE device comes into range. If it's been more than 15
 * minutes since the last sync, triggers a background connect + data refresh.
 *
 * Registered in the manifest so it works even when the app is killed.
 * Requires [CompanionDeviceManager.startObservingDevicePresence] to be
 * called for the favorite device's MAC address.
 */
class DevicePresenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DEVICE_APPEARING) return

        val app = try { MeshcoreApp.get() } catch (_: Throwable) { return }
        val controller = app.connectionController

        // Already connected — nothing to do
        if (controller.connectedDeviceId.value != null) return

        val favorite = runBlocking {
            app.repository.observeFavorite().first()
        } ?: return

        // Only for BLE favorites
        if (favorite.transport !is SavedTransport.Ble) return

        // Check cooldown — don't reconnect if we synced recently
        val lastSync = runBlocking {
            app.repository.getDeviceState(favorite.id)?.selfInfoFetchedAtMs ?: 0L
        }
        val elapsed = System.currentTimeMillis() - lastSync
        if (elapsed < MIN_SYNC_INTERVAL_MS) {
            Log.d(TAG, "Device appeared but last sync was ${elapsed / 1000}s ago, skipping")
            return
        }

        Log.d(TAG, "Favorite device appeared after ${elapsed / 1000}s — triggering sync")
        controller.requestReconnect(favorite)
    }
}
