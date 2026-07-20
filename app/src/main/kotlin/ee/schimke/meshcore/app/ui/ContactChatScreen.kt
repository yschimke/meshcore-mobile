package ee.schimke.meshcore.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import ee.schimke.meshcore.app.di.LocalAppGraph
import ee.schimke.meshcore.app.connection.ConnectionUiState
import ee.schimke.meshcore.components.ui.ChatBody
import ee.schimke.meshcore.components.ui.ChatMessage
import ee.schimke.meshcore.components.ui.MessageStatus
import ee.schimke.meshcore.components.generated.resources.Res
import ee.schimke.meshcore.components.generated.resources.contact_type_room
import ee.schimke.meshcore.components.generated.resources.contact_type_repeater
import ee.schimke.meshcore.core.model.ContactType
import org.jetbrains.compose.resources.stringResource
import ee.schimke.meshcore.data.entity.MessageDirection
import ee.schimke.meshcore.data.entity.MessageStatus as DbMessageStatus
import android.util.Log
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

private const val TAG = "MeshSend"

@Composable
fun ContactChatScreen(
    publicKeyHex: String,
    onBack: () -> Unit,
) {
    val app = LocalAppGraph.current
    val controller = app.connectionController
    val repository = app.repository
    val uiState by controller.state.collectAsState()
    val deviceId = controller.connectedDeviceId.collectAsState().value
    val client = (uiState as? ConnectionUiState.Connected)?.client

    val contacts by client?.contacts?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val contact = contacts.firstOrNull { it.publicKey.toHex() == publicKeyHex }
    val contactName = contact?.name ?: publicKeyHex.take(12)
    val requiresLogin = contact?.type == ContactType.ROOM || contact?.type == ContactType.REPEATER

    // Login state for rooms/repeaters
    var loggedIn by remember { mutableStateOf(false) }
    var loginDialogState by remember { mutableStateOf<LoginDialogState?>(null) }
    val scope = rememberCoroutineScope()

    // Show login dialog on first visit for rooms/repeaters
    LaunchedEffect(requiresLogin) {
        if (requiresLogin && !loggedIn && loginDialogState == null) {
            loginDialogState = LoginDialogState.Prompting()
        }
    }

    // Sync messages after successful login
    LaunchedEffect(loggedIn) {
        if (loggedIn && client != null) {
            runCatching { client.syncMessages() }
        }
    }

    // Login dialog
    if (loginDialogState != null && contact != null) {
        LoginDialog(
            contactName = contactName,
            contactType = if (contact.type == ContactType.ROOM) stringResource(Res.string.contact_type_room)
                else stringResource(Res.string.contact_type_repeater),
            state = loginDialogState!!,
            onLogin = { password ->
                loginDialogState = LoginDialogState.Authenticating
                scope.launch {
                    val result = runCatching {
                        client?.login(contact.publicKey, password)
                            ?: error("Not connected")
                    }
                    if (result.isSuccess) {
                        loggedIn = true
                        loginDialogState = null
                        Log.d(TAG, "Login success for ${publicKeyHex.take(12)}")
                    } else {
                        Log.w(TAG, "Login failed for ${publicKeyHex.take(12)}: ${result.exceptionOrNull()?.message}")
                        loginDialogState = LoginDialogState.Prompting(
                            errorMessage = "Login failed. Check the password and try again.",
                        )
                    }
                }
            },
            onDismiss = {
                loginDialogState = null
                onBack()
            },
        )
    }

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

    var draft by remember { mutableStateOf("") }
    val chatEnabled = client != null && contact != null && (!requiresLogin || loggedIn)

    val subtitle = contact?.let {
        buildString {
            append(it.type.name)
            append(" · ")
            append(if (it.isFlood) "flood" else "${it.pathLength} hops")
            if (requiresLogin) {
                append(" · ")
                append(if (loggedIn) "joined" else "not joined")
            }
        }
    }

    ChatBody(
        title = contactName,
        subtitle = subtitle,
        messages = messages,
        draft = draft,
        onDraftChange = { draft = it },
        onSend = {
            val text = draft.trim()
            if (text.isNotBlank() && client != null && contact != null && deviceId != null) {
                draft = ""
                val now = Clock.System.now()
                Log.d(TAG, "DM sending to ${publicKeyHex.take(12)}: '$text'")
                scope.launch {
                    val result = runCatching {
                        client.sendText(
                            recipient = contact.publicKey,
                            text = text,
                            timestamp = now,
                        )
                    }
                    val ack = result.getOrNull()
                    if (result.isFailure) {
                        Log.e(TAG, "DM send failed: ${result.exceptionOrNull()?.message}", result.exceptionOrNull())
                    } else {
                        Log.d(TAG, "DM send ok: ackHash=${ack?.ackHash} flood=${ack?.isFlood}")
                    }
                    repository.insertSentDm(
                        deviceId = deviceId,
                        contactKeyHex = publicKeyHex,
                        text = text,
                        timestamp = now,
                        ackHash = ack?.ackHash,
                        status = if (result.isSuccess) DbMessageStatus.SENT else DbMessageStatus.FAILED,
                    )
                    Log.d(TAG, "DM persisted to Room")
                }
            } else {
                Log.w(TAG, "DM send guard: blank=${text.isBlank()} client=${client != null} contact=${contact != null} deviceId=$deviceId")
            }
        },
        onBack = onBack,
        inputEnabled = chatEnabled,
    )
}
