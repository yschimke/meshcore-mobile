package ee.schimke.meshcore.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.time.Instant

/** Presentation model for a single chat message. */
data class ChatMessage(
    val id: String,
    val senderName: String?,
    val text: String,
    val timestamp: Instant,
    val snr: Int? = null,
    val isMine: Boolean,
    val status: MessageStatus = MessageStatus.Sent,
)

enum class MessageStatus { Sending, Sent, Confirmed, Failed }

/**
 * Scrollable message list with auto-scroll to newest message.
 * Messages are displayed oldest-first (index 0 = oldest).
 */
@Composable
fun ChatMessageList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
) {
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            state.animateScrollToItem(messages.lastIndex)
        }
    }
    if (messages.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No messages yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            state = state,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            reverseLayout = false,
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatBubble(msg)
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage, modifier: Modifier = Modifier) {
    val alignment = if (message.isMine) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (message.isMine)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = if (message.isMine)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(
        topStart = 12.dp, topEnd = 12.dp,
        bottomStart = if (message.isMine) 12.dp else 4.dp,
        bottomEnd = if (message.isMine) 4.dp else 12.dp,
    )

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = alignment) {
        Surface(
            shape = shape,
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!message.isMine && message.senderName != null) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    message.snr?.let { snr ->
                        Text(
                            text = "SNR $snr",
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.6f),
                        )
                    }
                    if (message.isMine) {
                        val statusText = when (message.status) {
                            MessageStatus.Sending -> "Sending\u2026"
                            MessageStatus.Sent -> "Sent"
                            MessageStatus.Confirmed -> "Delivered"
                            MessageStatus.Failed -> "Failed"
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (message.status == MessageStatus.Failed)
                                MaterialTheme.colorScheme.error
                            else textColor.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Chat input bar with text field and send button.
 */
@Composable
fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean = true,
    placeholder: String = "Message",
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
        )
        Spacer(Modifier.size(8.dp))
        IconButton(
            onClick = onSend,
            enabled = enabled && value.isNotBlank(),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Send,
                contentDescription = "Send",
                tint = if (enabled && value.isNotBlank())
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
