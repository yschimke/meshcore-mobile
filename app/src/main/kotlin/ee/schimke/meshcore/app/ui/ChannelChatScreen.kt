package ee.schimke.meshcore.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ee.schimke.meshcore.app.MeshcoreApp
import ee.schimke.meshcore.app.connection.ConnectionUiState
import ee.schimke.meshcore.components.ui.ChatInput
import ee.schimke.meshcore.components.ui.ChatMessage
import ee.schimke.meshcore.components.ui.ChatMessageList
import ee.schimke.meshcore.components.ui.MessageStatus
import ee.schimke.meshcore.data.entity.MessageStatus as DbMessageStatus
import android.util.Log
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

private const val TAG = "MeshSend"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelChatScreen(
    channelIndex: Int,
    onBack: () -> Unit,
) {
    val app = MeshcoreApp.get()
    val controller = app.connectionController
    val repository = app.repository
    val uiState by controller.state.collectAsState()
    val deviceId = controller.connectedDeviceId.collectAsState().value
    val client = (uiState as? ConnectionUiState.Connected)?.client

    val channels by client?.channels?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val channel = channels.firstOrNull { it.index == channelIndex }
    val channelName = channel?.name?.ifBlank { null } ?: "Channel $channelIndex"
    val selfName = client?.selfInfo?.collectAsState()?.value?.name

    // Read messages from Room
    val dbMessages by (deviceId?.let { repository.observeChannelMessages(it, channelIndex) }
        ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList())

    val messages by remember(dbMessages, selfName) {
        derivedStateOf {
            dbMessages.map { entity ->
                val isMine = selfName != null && entity.senderName == selfName
                ChatMessage(
                    id = "msg-${entity.rowId}",
                    senderName = if (!isMine) entity.senderName else null,
                    text = entity.text,
                    timestamp = Instant.fromEpochMilliseconds(entity.timestampEpochMs),
                    snr = entity.snr,
                    isMine = isMine,
                    status = when (entity.status) {
                        DbMessageStatus.SENDING -> MessageStatus.Sending
                        DbMessageStatus.SENT -> MessageStatus.Sent
                        DbMessageStatus.CONFIRMED -> MessageStatus.Confirmed
                        DbMessageStatus.FAILED -> MessageStatus.Failed
                    },
                )
            }
        }
    }

    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(channelName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Channel $channelIndex",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            ChatMessageList(
                messages = messages,
                modifier = Modifier.weight(1f),
            )
            ChatInput(
                value = draft,
                onValueChange = { draft = it },
                enabled = client != null,
                onSend = {
                    val text = draft.trim()
                    if (text.isBlank() || client == null || deviceId == null) {
                        Log.w(TAG, "Channel send guard: blank=${text.isBlank()} client=${client != null} deviceId=$deviceId")
                        return@ChatInput
                    }
                    draft = ""
                    val now = Clock.System.now()
                    Log.d(TAG, "Channel[$channelIndex] sending: '$text'")
                    scope.launch {
                        val result = runCatching {
                            client.sendChannelText(
                                channelIdx = channelIndex,
                                text = text,
                                timestamp = now,
                            )
                        }
                        if (result.isFailure) {
                            Log.e(TAG, "Channel send failed: ${result.exceptionOrNull()?.message}", result.exceptionOrNull())
                        } else {
                            val ack = result.getOrNull()
                            Log.d(TAG, "Channel send ok: ackHash=${ack?.ackHash} flood=${ack?.isFlood}")
                        }
                        // Message appears in history when the device echoes it back
                        // as a ChannelMessage event, picked up by MessagePersister.
                    }
                },
            )
        }
    }
}
