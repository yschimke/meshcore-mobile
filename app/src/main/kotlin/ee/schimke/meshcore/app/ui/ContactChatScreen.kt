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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ee.schimke.meshcore.app.MeshcoreApp
import ee.schimke.meshcore.app.connection.ConnectionUiState
import ee.schimke.meshcore.core.model.Contact
import ee.schimke.meshcore.core.model.MeshEvent
import ee.schimke.meshcore.components.ui.ChatInput
import ee.schimke.meshcore.components.ui.ChatMessage
import ee.schimke.meshcore.components.ui.ChatMessageList
import ee.schimke.meshcore.components.ui.MessageStatus
import kotlinx.coroutines.launch
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactChatScreen(
    publicKeyHex: String,
    onBack: () -> Unit,
) {
    val controller = MeshcoreApp.get().connectionController
    val uiState by controller.state.collectAsState()
    val client = (uiState as? ConnectionUiState.Connected)?.client

    // Resolve the contact from the client's contact list
    val contacts by client?.contacts?.collectAsState() ?: return
    val contact = contacts.firstOrNull { it.publicKey.toHex() == publicKeyHex }
    val contactName = contact?.name ?: publicKeyHex.take(12)

    val messages = remember { mutableStateListOf<ChatMessage>() }
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }

    // Listen for incoming DMs from this contact
    LaunchedEffect(client) {
        client?.events?.collect { ev ->
            when (ev) {
                is MeshEvent.DirectMessage -> {
                    val prefix = ev.message.senderPrefix.toHex()
                    if (publicKeyHex.startsWith(prefix)) {
                        messages.add(
                            ChatMessage(
                                id = "rx-${messages.size}-${ev.message.timestamp.epochSeconds}",
                                senderName = contactName,
                                text = ev.message.text,
                                timestamp = ev.message.timestamp,
                                snr = ev.message.snr,
                                isMine = false,
                            ),
                        )
                    }
                }
                is MeshEvent.SendConfirmedEvent -> {
                    val hash = ev.confirmed.ackHash
                    val idx = messages.indexOfFirst {
                        it.id.startsWith("tx-$hash-") && it.status == MessageStatus.Sent
                    }
                    if (idx >= 0) {
                        messages[idx] = messages[idx].copy(status = MessageStatus.Confirmed)
                    }
                }
                else -> Unit
            }
        }
    }

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
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
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
                    if (text.isBlank() || client == null || contact == null) return@ChatInput
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
                        messages.add(
                            ChatMessage(
                                id = "tx-${ack?.ackHash ?: messages.size}-${now.epochSeconds}",
                                senderName = null,
                                text = text,
                                timestamp = now,
                                isMine = true,
                                status = if (result.isSuccess) MessageStatus.Sent else MessageStatus.Failed,
                            ),
                        )
                    }
                },
            )
        }
    }
}
