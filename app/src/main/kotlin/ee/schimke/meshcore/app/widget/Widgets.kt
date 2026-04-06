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

// --- Quick send -------------------------------------------------------------

@RequiresApi(37)
class QuickSendWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, wm: AppWidgetManager, widgetIds: IntArray) {
        goAsync {
            coroutineScope {
                widgetIds.forEach { id ->
                    launch {
                        val bytes = withContext(Dispatchers.Main) {
                            captureSingleRemoteDocument(
                                context = context,
                                profile = RcPlatformProfiles.WIDGETS_V6,
                            ) {
                                QuickSendWidgetContent()
                            }
                        }
                        wm.updateAppWidget(id, RemoteViews(drawInstructions(bytes.bytes)))
                    }
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == QUICKSEND_ACTION) {
            QuickSendBroadcast.onTap(context)
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val QUICKSEND_ACTION = "ee.schimke.meshcore.app.QUICKSEND"
    }
}
