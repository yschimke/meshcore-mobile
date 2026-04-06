package ee.schimke.meshcore.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import ee.schimke.meshcore.data.entity.MessageDirection
import ee.schimke.meshcore.data.entity.MessageStatus as DbMessageStatus
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactChatScreen(
    publicKeyHex: String,
    onBack: () -> Unit,
) {
    val app = MeshcoreApp.get()
    val controller = app.connectionController
    val repository = app.repository
    val uiState by controller.state.collectAsState()
    val deviceId = controller.connectedDeviceId.collectAsState().value
    val client = (uiState as? ConnectionUiState.Connected)?.client

    val contacts by client?.contacts?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val contact = contacts.firstOrNull { it.publicKey.toHex() == publicKeyHex }
    val contactName = contact?.name ?: publicKeyHex.take(12)

    // Read messages from Room (includes both sent and received, persisted across restarts)
    val dbMessages by (deviceId?.let { repository.observeDms(it, publicKeyHex) }
        ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList())

    val messages by remember(dbMessages) {
        derivedStateOf {
            dbMessages.map { entity ->
                ChatMessage(
                    id = "msg-${entity.rowId}",
                    senderName = if (entity.direction == MessageDirection.RECEIVED) contactName else null,
                    text = entity.text,
                    timestamp = Instant.fromEpochMilliseconds(entity.timestampEpochMs),
                    snr = entity.snr,
                    isMine = entity.direction == MessageDirection.SENT,
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
                        Text(contactName, style = MaterialTheme.typography.titleMedium)
                        contact?.let {
                            Text(
                                text = "${it.type.name} · ${if (it.isFlood) "flood" else "${it.pathLength} hops"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ChatMessageList(
                messages = messages,
                modifier = Modifier.weight(1f),
            )
            ChatInput(
                value = draft,
                onValueChange = { draft = it },
                enabled = client != null && contact != null,
                onSend = {
                    val text = draft.trim()
                    if (text.isBlank() || client == null || contact == null || deviceId == null) return@ChatInput
                    draft = ""
                    val now = Clock.System.now()
                    scope.launch {
                        val result = runCatching {
                            client.sendText(
                                recipient = contact.publicKey,
                                text = text,
                                timestamp = now,
                            )
                        }
                        val ack = result.getOrNull()
                        repository.insertSentDm(
                            deviceId = deviceId,
                            contactKeyHex = publicKeyHex,
                            text = text,
                            timestamp = now,
                            ackHash = ack?.ackHash,
                            status = if (result.isSuccess) DbMessageStatus.SENT else DbMessageStatus.FAILED,
                        )
                    }
                },
            )
        }
    }
}
