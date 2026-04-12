package ee.schimke.meshcore.app.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ee.schimke.meshcore.app.MeshcoreApp
import ee.schimke.meshcore.app.connection.ConnectionUiState
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

/** Discovery state for the device settings screen. */
private sealed class SettingsState {
    data object Discovering : SettingsState()
    data class Ready(val hasBuzzer: Boolean) : SettingsState()
    data class Error(val message: String) : SettingsState()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSettingsScreen(
    channelIndex: Int,
    onBack: () -> Unit,
) {
    val app = MeshcoreApp.get()
    val controller = app.connectionController
    val repository = app.repository
    val uiState by controller.state.collectAsState()
    val deviceId = controller.connectedDeviceId.collectAsState().value
    val client = (uiState as? ConnectionUiState.Connected)?.client
    val selfName = client?.selfInfo?.collectAsState()?.value?.name

    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<SettingsState>(SettingsState.Discovering) }
    var buzzerMode by remember { mutableStateOf<String?>(null) }
    var buzzerLoading by remember { mutableStateOf(false) }

    // Discover capabilities on mount
    LaunchedEffect(client, deviceId) {
        val c = client ?: return@LaunchedEffect
        val did = deviceId ?: return@LaunchedEffect
        state = SettingsState.Discovering
        Log.d(TAG, "Discovering device capabilities via /help")
        val helpResponse = sendCommandAndAwaitResponse(
            c, repository, did, channelIndex, "/help", selfName,
        )
        if (helpResponse == null) {
            state = SettingsState.Error("No response from device")
            return@LaunchedEffect
        }
        Log.d(TAG, "Help response: $helpResponse")
        val hasBuzzer = helpResponse.contains("/buz", ignoreCase = true)
        state = SettingsState.Ready(hasBuzzer = hasBuzzer)

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Settings", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                actions = {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            when (val s = state) {
                is SettingsState.Discovering -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(12.dp))
                        Text(
                            "Discovering device capabilities\u2026",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is SettingsState.Error -> {
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
                is SettingsState.Ready -> {
                    if (!s.hasBuzzer) {
                        Text(
                            text = "No configurable settings detected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    } else {
                        Spacer(Modifier.size(8.dp))
                        BuzzerRow(
                            mode = buzzerMode,
                            loading = buzzerLoading,
                            onToggle = { wantOn ->
                                val c = client ?: return@BuzzerRow
                                val did = deviceId ?: return@BuzzerRow
                                scope.launch {
                                    buzzerLoading = true
                                    val cmd = if (wantOn) "/buz rtttl" else "/buz off"
                                    val response = sendCommandAndAwaitResponse(
                                        c, repository, did, channelIndex, cmd, selfName,
                                    )
                                    buzzerMode = parseBuzzerMode(response) ?: buzzerMode
                                    buzzerLoading = false
                                }
                            },
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BuzzerRow(
    mode: String?,
    loading: Boolean,
    onToggle: (wantOn: Boolean) -> Unit,
) {
    val isOn = mode != null && mode != "off"

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Buzzer",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = when {
                    loading -> "Updating\u2026"
                    mode == null -> "Unknown"
                    mode == "off" -> "Off"
                    else -> "On ($mode)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(12.dp))
        }
        Switch(
            checked = isOn,
            onCheckedChange = { onToggle(it) },
            enabled = !loading && mode != null,
        )
    }
}

/**
 * Parse buzzer mode from a device response like "Buzzer mode set to off".
 */
private fun parseBuzzerMode(response: String?): String? {
    if (response == null) return null
    val regex = Regex("""(?i)buzzer\s+mode\s+(?:set\s+to|:)\s+(\S+)""")
    return regex.find(response)?.groupValues?.get(1)?.lowercase()
}
