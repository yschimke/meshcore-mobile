@file:SuppressLint("RestrictedApi")

package ee.schimke.meshcore.app.widget

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import ee.schimke.meshcore.core.client.MeshCoreClient
import ee.schimke.meshcore.core.manager.ManagerState
import ee.schimke.meshcore.core.manager.MeshCoreManager
import ee.schimke.meshcore.core.model.MeshEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Snapshot of everything the sample widgets want to render.
 */
data class WidgetSnapshot(
    val deviceName: String? = null,
    val batteryMv: Int? = null,
    val batteryPercent: Int? = null,
    val lastSnr: Int? = null,
    val frequencyMhz: Double? = null,
    val contactCount: Int = 0,
    val lastMessage: String? = null,
)

object WidgetStateBridge {
    private val _snapshot = MutableStateFlow(WidgetSnapshot())
    val snapshot: StateFlow<WidgetSnapshot> = _snapshot.asStateFlow()

    private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var clientJob: Job? = null

    /** Hook up to a live [MeshCoreManager]; emits snapshots for widgets. */
    fun start(context: Context, manager: MeshCoreManager) {
        val appContext = context.applicationContext
        bridgeScope.launch {
            manager.state.collect { state ->
                clientJob?.cancel()
                clientJob = null
                if (state is ManagerState.Connected) {
                    clientJob = bridgeScope.launch { observeClient(appContext, state.client) }
                } else if (state is ManagerState.Idle) {
                    _snapshot.value = WidgetSnapshot()
                    notifyWidgets(appContext)
                }
            }
        }
    }

    private suspend fun observeClient(context: Context, client: MeshCoreClient) {
        val updater = bridgeScope.launch {
            client.events.collect { ev ->
                val next = when (ev) {
                    is MeshEvent.DirectMessage -> _snapshot.value.copy(
                        lastMessage = "${ev.message.text}",
                        lastSnr = ev.message.snr,
                    )
                    is MeshEvent.ChannelMessage -> _snapshot.value.copy(
                        lastMessage = "#${ev.message.channelIndex} ${ev.message.body}",
                        lastSnr = ev.message.snr,
                    )
                    is MeshEvent.Battery -> _snapshot.value.copy(
                        batteryMv = ev.info.millivolts,
                        batteryPercent = ev.info.estimatePercent(),
                    )
                    is MeshEvent.SelfInfoEvent -> _snapshot.value.copy(
                        deviceName = ev.info.name,
                        frequencyMhz = ev.info.radio.frequencyHz / 1_000_000.0,
                    )
                    is MeshEvent.Radio -> _snapshot.value.copy(
                        frequencyMhz = ev.settings.frequencyHz / 1_000_000.0,
                    )
                    MeshEvent.EndOfContacts -> _snapshot.value.copy(
                        contactCount = client.contacts.value.size,
                    )
                    else -> _snapshot.value
                }
                if (next != _snapshot.value) {
                    _snapshot.value = next
                    notifyWidgets(context)
                }
            }
        }
        updater.join()
    }

    /**
     * Trigger an APPWIDGET_UPDATE broadcast for all four widget receivers
     * so they re-capture their Remote Compose documents.
     */
    private fun notifyWidgets(context: Context) {
        val wm = AppWidgetManager.getInstance(context)
        val receivers = listOf(
            BatteryWidgetReceiver::class.java,
            MeshStatusWidgetReceiver::class.java,
            LastMessageWidgetReceiver::class.java,
            QuickSendWidgetReceiver::class.java,
        )
        for (cls in receivers) {
            val ids = wm.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isNotEmpty()) {
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                    component = ComponentName(context, cls)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
