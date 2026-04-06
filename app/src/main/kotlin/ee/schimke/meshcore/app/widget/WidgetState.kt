@file:SuppressLint("RestrictedApi")

package ee.schimke.meshcore.app.widget

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.profile.RcPlatformProfiles
import androidx.compose.runtime.Composable
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
    val connected: Boolean = false,
    val deviceName: String? = null,
    val pubkeyPrefix: String? = null,
    val batteryMv: Int? = null,
    val batteryPercent: Int? = null,
    val storageUsedKb: Long? = null,
    val storageTotalKb: Long? = null,
    val lastSnr: Int? = null,
    val frequencyMhz: Double? = null,
    val bandwidthKhz: Int? = null,
    val spreadingFactor: Int? = null,
    val codingRate: Int? = null,
    val contactCount: Int = 0,
    val lastMessage: String? = null,
    val lastConnectedMs: Long? = null,
    val lastUpdatedMs: Long? = null,
) {
    val storageLine: String?
        get() = if (storageUsedKb != null && storageTotalKb != null)
            "$storageUsedKb / $storageTotalKb kB" else null

    val radioLine: String?
        get() {
            val freq = frequencyMhz ?: return null
            return buildString {
                append("%.3f MHz".format(freq))
                if (bandwidthKhz != null) append(" · BW $bandwidthKhz kHz")
                if (spreadingFactor != null) append(" · SF $spreadingFactor")
                if (codingRate != null) append(" · CR $codingRate")
            }
        }
}

object WidgetStateBridge {
    private val _snapshot = MutableStateFlow(WidgetSnapshot())
    val snapshot: StateFlow<WidgetSnapshot> = _snapshot.asStateFlow()

    private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var clientJob: Job? = null

    /** Hook up to a live [MeshCoreManager]; emits snapshots for widgets. */
    fun start(context: Context, manager: MeshCoreManager) {
        val appContext = context.applicationContext

        // Set initial widget picker previews
        bridgeScope.launch {
            runCatching { updateWidgetPreviews(appContext) }
        }

        bridgeScope.launch {
            manager.state.collect { state ->
                clientJob?.cancel()
                clientJob = null
                if (state is ManagerState.Connected) {
                    _snapshot.value = _snapshot.value.copy(connected = true)
                    notifyWidgets(appContext)
                    clientJob = bridgeScope.launch { observeClient(appContext, state.client) }
                } else if (state is ManagerState.Idle) {
                    // Preserve historical data — only mark disconnected
                    _snapshot.value = _snapshot.value.copy(connected = false)
                    notifyWidgets(appContext)
                }
                // Update widget picker previews with latest data
                runCatching { updateWidgetPreviews(appContext) }
            }
        }
    }

    private suspend fun observeClient(context: Context, client: MeshCoreClient) {
        // Seed snapshot from current client state (data may have arrived
        // before the bridge started observing events).
        val self = client.selfInfo.value
        val bat = client.battery.value
        val radio = client.radio.value
        val contacts = client.contacts.value
        var seeded = _snapshot.value.copy(connected = true)
        if (self != null) seeded = seeded.copy(
            deviceName = self.name,
            pubkeyPrefix = self.publicKey.toHex().take(16),
            frequencyMhz = self.radio.frequencyHz / 1_000_000.0,
            bandwidthKhz = self.radio.bandwidthHz / 1000,
            spreadingFactor = self.radio.spreadingFactor,
            codingRate = self.radio.codingRate,
            lastConnectedMs = System.currentTimeMillis(),
        )
        if (bat != null) seeded = seeded.copy(
            batteryMv = bat.millivolts,
            batteryPercent = bat.estimatePercent(),
            storageUsedKb = bat.storageUsedKb,
            storageTotalKb = bat.storageTotalKb,
        )
        if (radio != null) seeded = seeded.copy(
            frequencyMhz = radio.frequencyHz / 1_000_000.0,
        )
        if (contacts.isNotEmpty()) seeded = seeded.copy(
            contactCount = contacts.size,
        )
        if (seeded != _snapshot.value) {
            _snapshot.value = seeded.copy(lastUpdatedMs = System.currentTimeMillis())
            notifyWidgets(context)
        }

        // Observe events for messages and live pushes
        val eventsJob = bridgeScope.launch {
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
                    else -> _snapshot.value
                }
                if (next != _snapshot.value) {
                    _snapshot.value = next.copy(lastUpdatedMs = System.currentTimeMillis())
                    notifyWidgets(context)
                }
            }
        }
        // Observe StateFlows for data that arrives via fetchAndPersist
        val stateJob = bridgeScope.launch {
            kotlinx.coroutines.flow.combine(
                client.selfInfo,
                client.battery,
                client.radio,
                client.contacts,
            ) { self, bat, radio, contacts ->
                var s = _snapshot.value.copy(connected = true)
                if (self != null) s = s.copy(
                    deviceName = self.name,
                    pubkeyPrefix = self.publicKey.toHex().take(16),
                    frequencyMhz = self.radio.frequencyHz / 1_000_000.0,
                    lastConnectedMs = System.currentTimeMillis(),
                )
                if (bat != null) s = s.copy(
                    batteryMv = bat.millivolts,
                    batteryPercent = bat.estimatePercent(),
                    storageUsedKb = bat.storageUsedKb,
                    storageTotalKb = bat.storageTotalKb,
                )
                if (radio != null) s = s.copy(
                    frequencyMhz = radio.frequencyHz / 1_000_000.0,
                    bandwidthKhz = radio.bandwidthHz / 1000,
                    spreadingFactor = radio.spreadingFactor,
                    codingRate = radio.codingRate,
                )
                s = s.copy(contactCount = contacts.size)
                s
            }.collect { next ->
                if (next != _snapshot.value) {
                    _snapshot.value = next.copy(lastUpdatedMs = System.currentTimeMillis())
                    notifyWidgets(context)
                }
            }
        }
        eventsJob.join()
        stateJob.cancel()
    }

    /**
     * Capture live previews for the widget picker via [AppWidgetManager.setWidgetPreview].
     */
    @RequiresApi(37)
    suspend fun updateWidgetPreviews(context: Context) {
        val snap = _snapshot.value
        val wm = AppWidgetManager.getInstance(context)

        suspend fun setPreview(cls: Class<*>, content: @Composable () -> Unit) {
            val bytes = withContext(Dispatchers.Main) {
                captureSingleRemoteDocument(
                    context = context,
                    profile = RcPlatformProfiles.WIDGETS_V6,
                    content = content,
                )
            }
            val di = RemoteViews.DrawInstructions.Builder(listOf(bytes.bytes)).build()
            wm.setWidgetPreview(
                ComponentName(context, cls),
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                RemoteViews(di),
            )
        }

        val pct = snap.batteryPercent ?: 94
        setPreview(DeviceInfoWidgetReceiver::class.java) {
            DeviceInfoWidgetContent(
                deviceName = snap.deviceName ?: "node-peak",
                pubkeyPrefix = snap.pubkeyPrefix ?: "ab1234567890cdef",
                radioInfo = snap.radioLine ?: "869.525 MHz · BW 125 kHz · SF 10 · CR 5",
                batteryLine = snap.batteryPercent?.let { p ->
                    val mv = snap.batteryMv?.let { " · $it mV" } ?: ""
                    "$p%$mv"
                } ?: "94% · 4135 mV",
                batteryProgress = pct / 100f,
                batteryWarn = pct < 30,
                storageLine = snap.storageLine ?: "512 / 4096 kB",
            )
        }
    }

    /**
     * Trigger an APPWIDGET_UPDATE broadcast for all widget receivers
     * so they re-capture their Remote Compose documents.
     */
    private fun notifyWidgets(context: Context) {
        val wm = AppWidgetManager.getInstance(context)
        val receivers = listOf(
            DeviceInfoWidgetReceiver::class.java,
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
