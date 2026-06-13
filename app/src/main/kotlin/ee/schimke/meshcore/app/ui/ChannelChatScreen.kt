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
import ee.schimke.meshcore.app.di.LocalAppGraph
import ee.schimke.meshcore.app.connection.ConnectionUiState
import ee.schimke.meshcore.components.ui.ChatInput
import ee.schimke.meshcore.components.ui.ChatMessage
import ee.schimke.meshcore.components.ui.ChatMessageList
import ee.schimke.meshcore.components.ui.MessageStatus
import ee.schimke.meshcore.data.entity.MessageDirection
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
    val app = LocalAppGraph.current
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
                val isMine = entity.direction == MessageDirection.SENT ||
                    (selfName != null && entity.senderName == selfName)
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

    /** Send (or re-send) a channel message. Returns the DB row ID. */
    fun doSend(text: String, timestamp: Instant, existingRowId: Long? = null) {
        val c = client ?: return
        val did = deviceId ?: return
        scope.launch {
            // Insert placeholder immediately (or mark existing row as sending)
            val rowId = if (existingRowId != null) {
                repository.updateMessageStatus(existingRowId, DbMessageStatus.SENDING)
                existingRowId
            } else {
                repository.insertSentChannelMessage(
                    deviceId = did,
                    channelIndex = channelIndex,
                    senderName = selfName,
                    text = text,
                    timestamp = timestamp,
                    ackHash = null,
                    status = DbMessageStatus.SENDING,
                )
            }

            val result = runCatching {
                c.sendChannelText(
                    channelIdx = channelIndex,
                    text = text,
                    timestamp = timestamp,
                )
            }
            if (result.isFailure) {
                Log.e(TAG, "Channel send failed: ${result.exceptionOrNull()?.message}", result.exceptionOrNull())
                repository.updateMessageStatus(rowId, DbMessageStatus.FAILED)
            } else {
                val ack = result.getOrNull()
                Log.d(TAG, "Channel send ok: ackHash=${ack?.ackHash} flood=${ack?.isFlood}")
                repository.updateMessageStatusAndAck(rowId, DbMessageStatus.SENT, ack?.ackHash)
            }
        }
    }

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
                onRetry = { msg ->
                    // Extract the DB row ID from the ChatMessage id ("msg-123" → 123)
                    val rowId = msg.id.removePrefix("msg-").toLongOrNull() ?: return@ChatMessageList
                    doSend(msg.text, msg.timestamp, existingRowId = rowId)
                },
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
                    doSend(text, now)
                },
            )
        }
    }
}
