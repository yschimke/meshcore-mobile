@file:SuppressLint("RestrictedApi")

package ee.schimke.meshcore.app.widget

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.profile.RcPlatformProfiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// --- Shared helpers ---------------------------------------------------------

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

// --- Battery + SNR ----------------------------------------------------------

@RequiresApi(37)
class BatteryWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, wm: AppWidgetManager, widgetIds: IntArray) {
        goAsync {
            val snap = WidgetStateBridge.snapshot.first()
            coroutineScope {
                widgetIds.forEach { id ->
                    launch {
                        val bytes = withContext(Dispatchers.Main) {
                            captureSingleRemoteDocument(
                                context = context,
                                profile = RcPlatformProfiles.WIDGETS_V6,
                            ) {
                                BatteryWidgetContent(
                                    batteryPercent = snap.batteryPercent?.let { "$it%" } ?: "—",
                                    batteryMv = snap.batteryMv?.let { "$it mV" },
                                    snr = snap.lastSnr?.let { "SNR $it" },
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
}

// --- Mesh status (name + contact count + freq) ------------------------------

@RequiresApi(37)
class MeshStatusWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, wm: AppWidgetManager, widgetIds: IntArray) {
        goAsync {
            val snap = WidgetStateBridge.snapshot.first()
            coroutineScope {
                widgetIds.forEach { id ->
                    launch {
                        val bytes = withContext(Dispatchers.Main) {
                            captureSingleRemoteDocument(
                                context = context,
                                profile = RcPlatformProfiles.WIDGETS_V6,
                            ) {
                                MeshStatusWidgetContent(
                                    deviceName = snap.deviceName ?: "Not connected",
                                    contactCount = "${snap.contactCount} contacts",
                                    frequencyMhz = snap.frequencyMhz?.let { "%.3f MHz".format(it) },
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
}

// --- Last received message --------------------------------------------------

@RequiresApi(37)
class LastMessageWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, wm: AppWidgetManager, widgetIds: IntArray) {
        goAsync {
            val snap = WidgetStateBridge.snapshot.first()
            coroutineScope {
                widgetIds.forEach { id ->
                    launch {
                        val bytes = withContext(Dispatchers.Main) {
                            captureSingleRemoteDocument(
                                context = context,
                                profile = RcPlatformProfiles.WIDGETS_V6,
                            ) {
                                LastMessageWidgetContent(
                                    message = snap.lastMessage ?: "(none yet)",
                                )
                            }
                        }
                        wm.updateAppWidget(id, RemoteViews(drawInstructions(bytes.bytes)))
                    }
                }
            }
        }
    }
}

// --- Connection status ------------------------------------------------------

@RequiresApi(37)
class ConnectionStatusWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, wm: AppWidgetManager, widgetIds: IntArray) {
        goAsync {
            val snap = WidgetStateBridge.snapshot.first()
            coroutineScope {
                widgetIds.forEach { id ->
                    launch {
                        val bytes = withContext(Dispatchers.Main) {
                            captureSingleRemoteDocument(
                                context = context,
                                profile = RcPlatformProfiles.WIDGETS_V6,
                            ) {
                                ConnectionStatusWidgetContent(
                                    status = if (snap.connected) "Connected" else "Disconnected",
                                    deviceName = snap.deviceName,
                                    lastSeen = if (!snap.connected) {
                                        snap.lastConnectedMs?.let { formatElapsed(it) }
                                    } else null,
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

private fun formatElapsed(timestampMs: Long): String {
    val elapsed = System.currentTimeMillis() - timestampMs
    val minutes = elapsed / 60_000
    return when {
        minutes < 1 -> "Last seen just now"
        minutes < 60 -> "Last seen ${minutes}m ago"
        minutes < 1440 -> "Last seen ${minutes / 60}h ago"
        else -> "Last seen ${minutes / 1440}d ago"
    }
}
