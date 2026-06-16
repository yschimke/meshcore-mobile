package ee.schimke.meshcore.app.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import ee.schimke.meshcore.app.di.LocalAppGraph
import ee.schimke.meshcore.app.connection.ConnectionUiState
import ee.schimke.meshcore.components.ui.DeviceSettingsBody
import ee.schimke.meshcore.components.ui.DeviceSettingsUiState
import ee.schimke.meshcore.core.client.MeshCoreClient
import ee.schimke.meshcore.data.entity.MessageDirection
import ee.schimke.meshcore.data.entity.MessageStatus
import ee.schimke.meshcore.data.repository.MeshcoreRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock

private const val TAG = "DeviceSettings"

/** Internal discovery phase; mapped to [DeviceSettingsUiState] for the body. */
private sealed class SettingsPhase {
    data object Discovering : SettingsPhase()
    data class Ready(val hasBuzzer: Boolean) : SettingsPhase()
    data class Error(val message: String) : SettingsPhase()
}

/**
 * Sends [command] on the commands channel and waits for the first
 * RECEIVED response. Returns the response text, or null on timeout.
 */
private suspend fun sendCommandAndAwaitResponse(
    client: MeshCoreClient,
    repository: MeshcoreRepository,
    deviceId: String,
    channelIndex: Int,
    command: String,
    selfName: String?,
    timeoutMs: Long = 8_000,
): String? {
    val sentAt = Clock.System.now()
    val sentAtMs = sentAt.toEpochMilliseconds()

    // Insert + send
    val rowId = repository.insertSentChannelMessage(
        deviceId = deviceId,
        channelIndex = channelIndex,
        senderName = selfName,
        text = command,
        timestamp = sentAt,
        ackHash = null,
        status = MessageStatus.SENDING,
    )
    val result = runCatching {
        client.sendChannelText(channelIndex, command, sentAt)
    }
    if (result.isFailure) {
        Log.e(TAG, "Command '$command' send failed", result.exceptionOrNull())
        repository.updateMessageStatus(rowId, MessageStatus.FAILED)
        return null
    }
    val ack = result.getOrNull()
    repository.updateMessageStatusAndAck(rowId, MessageStatus.SENT, ack?.ackHash)

    // Wait for the first RECEIVED message that arrives after our send
    return withTimeoutOrNull(timeoutMs) {
        repository.observeChannelMessages(deviceId, channelIndex)
            .mapNotNull { messages ->
                messages.lastOrNull { msg ->
                    msg.direction == MessageDirection.RECEIVED &&
                        msg.timestampEpochMs >= sentAtMs - 2_000 // allow small clock skew
                }
            }
            .first()
            .text
    }
}

@Composable
fun DeviceSettingsScreen(
    channelIndex: Int,
    onBack: () -> Unit,
) {
    val app = LocalAppGraph.current
    val controller = app.connectionController
    val repository = app.repository
    val uiState by controller.state.collectAsState()
    val deviceId = controller.connectedDeviceId.collectAsState().value
    val client = (uiState as? ConnectionUiState.Connected)?.client
    val selfName = client?.selfInfo?.collectAsState()?.value?.name

    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf<SettingsPhase>(SettingsPhase.Discovering) }
    var buzzerMode by remember { mutableStateOf<String?>(null) }
    var buzzerLoading by remember { mutableStateOf(false) }

    // Discover capabilities on mount
    LaunchedEffect(client, deviceId) {
        val c = client ?: return@LaunchedEffect
        val did = deviceId ?: return@LaunchedEffect
        phase = SettingsPhase.Discovering
        Log.d(TAG, "Discovering device capabilities via /help")
        val helpResponse = sendCommandAndAwaitResponse(
            c, repository, did, channelIndex, "/help", selfName,
        )
        if (helpResponse == null) {
            phase = SettingsPhase.Error("No response from device")
            return@LaunchedEffect
        }
        Log.d(TAG, "Help response: $helpResponse")
        val hasBuzzer = helpResponse.contains("/buz", ignoreCase = true)
        phase = SettingsPhase.Ready(hasBuzzer = hasBuzzer)

        // Probe current buzzer state
        if (hasBuzzer) {
            buzzerLoading = true
            val buzResponse = sendCommandAndAwaitResponse(
                c, repository, did, channelIndex, "/buz", selfName,
            )
            buzzerMode = parseBuzzerMode(buzResponse)
            buzzerLoading = false
            Log.d(TAG, "Buzzer mode: $buzzerMode")
        }
    }

    val state: DeviceSettingsUiState = when (val p = phase) {
        SettingsPhase.Discovering -> DeviceSettingsUiState.Discovering
        is SettingsPhase.Error -> DeviceSettingsUiState.Error(p.message)
        is SettingsPhase.Ready ->
            DeviceSettingsUiState.Ready(
                hasBuzzer = p.hasBuzzer,
                buzzerMode = buzzerMode,
                buzzerLoading = buzzerLoading,
            )
    }

    DeviceSettingsBody(
        state = state,
        onToggleBuzzer = { wantOn ->
            val c = client
            val did = deviceId
            if (c != null && did != null) {
                scope.launch {
                    buzzerLoading = true
                    val cmd = if (wantOn) "/buz rtttl" else "/buz off"
                    val response = sendCommandAndAwaitResponse(
                        c, repository, did, channelIndex, cmd, selfName,
                    )
                    buzzerMode = parseBuzzerMode(response) ?: buzzerMode
                    buzzerLoading = false
                }
            }
        },
        onBack = onBack,
    )
}

/**
 * Parse buzzer mode from a device response like "Buzzer mode set to off".
 */
private fun parseBuzzerMode(response: String?): String? {
    if (response == null) return null
    val regex = Regex("""(?i)buzzer\s+mode\s+(?:set\s+to|:)\s+(\S+)""")
    return regex.find(response)?.groupValues?.get(1)?.lowercase()
}
