@file:SuppressLint("RestrictedApi")

package ee.schimke.meshcore.app.widget

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.profile.RcPlatformProfiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun drawInstructions(bytes: ByteArray): RemoteViews.DrawInstructions =
    RemoteViews.DrawInstructions.Builder(listOf(bytes)).build()

private const val STALE_THRESHOLD_MS = 60 * 60 * 1000L // 1 hour

private fun staleLabel(snap: WidgetSnapshot): String? {
    if (snap.connected) return null
    val updated = snap.lastUpdatedMs ?: return null
    val elapsed = System.currentTimeMillis() - updated
    if (elapsed < STALE_THRESHOLD_MS) return null
    val hours = elapsed / (60 * 60 * 1000)
    return if (hours < 24) "Updated ${hours}h ago" else "Updated ${hours / 24}d ago"
}

/**
 * Get the current widget snapshot, seeding from Room if the in-memory
 * bridge hasn't been populated yet.
 */
private suspend fun currentSnapshot(): WidgetSnapshot {
    val snap = WidgetStateBridge.snapshot.value
    if (snap.batteryMv != null || snap.deviceName != null) return snap

    val app = try { ee.schimke.meshcore.app.MeshcoreApp.get() } catch (_: Throwable) { return snap }
    val fav = app.repository.observeFavorite().first() ?: return snap
    val state = app.repository.getDeviceState(fav.id) ?: return snap

    return snap.copy(
        deviceName = state.selfName,
        pubkeyPrefix = state.selfPublicKey?.let { bytes ->
            bytes.joinToString("") { "%02x".format(it) }.take(16)
        },
        batteryMv = state.batteryMillivolts,
        batteryPercent = state.batteryMillivolts?.let {
            ee.schimke.meshcore.core.model.BatteryInfo(
                it, state.storageUsedKb ?: 0, state.storageTotalKb ?: 0,
            ).estimatePercent()
        },
        storageUsedKb = state.storageUsedKb,
        storageTotalKb = state.storageTotalKb,
        frequencyMhz = state.radioFrequencyHz?.let { it / 1_000_000.0 },
        bandwidthKhz = state.radioBandwidthHz?.let { it / 1000 },
        spreadingFactor = state.radioSpreadingFactor,
        codingRate = state.radioCodingRate,
        lastUpdatedMs = maxOf(
            state.selfInfoFetchedAtMs,
            state.batteryFetchedAtMs,
            state.radioFetchedAtMs,
        ).takeIf { it > 0 },
    )
}

// --- Device Info Widget -----------------------------------------------------

class DeviceInfoWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, wm: AppWidgetManager, widgetIds: IntArray) {
        goAsync {
            val snap = currentSnapshot()
            coroutineScope {
                widgetIds.forEach { id ->
                    launch {
                        val bytes = withContext(Dispatchers.Main) {
                            captureSingleRemoteDocument(
                                context = context,
                                profile = RcPlatformProfiles.WIDGETS_V6,
                            ) {
                                val pct = snap.batteryPercent
                                DeviceInfoWidgetContent(
                                    deviceName = snap.deviceName ?: "No device",
                                    pubkeyPrefix = snap.pubkeyPrefix,
                                    radioInfo = snap.radioLine,
                                    batteryLine = pct?.let {
                                        val mv = snap.batteryMv?.let { " · $it mV" } ?: ""
                                        "$it%$mv"
                                    },
                                    batteryProgress = pct?.let { it / 100f },
                                    batteryWarn = pct != null && pct < 30,
                                    storageLine = snap.storageLine,
                                    staleLabel = staleLabel(snap),
                                )
                            }
                        }
                        wm.updateAppWidget(id, RemoteViews(drawInstructions(bytes.bytes)))
                    }
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == REFRESH_ACTION) {
            WidgetRefreshWorker.enqueue(context)
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val REFRESH_ACTION = "ee.schimke.meshcore.app.WIDGET_REFRESH"
    }
}
