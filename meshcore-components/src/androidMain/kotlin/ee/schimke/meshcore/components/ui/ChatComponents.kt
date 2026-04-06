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
import androidx.compose.ui.text.font.FontWeight
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
 */
@Composable
fun ChatMessageList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
) {
    // reverseLayout anchors the list to the bottom so the keyboard
    // pushes the latest messages up instead of scrolling them off-screen.
    val reversed = messages.asReversed()
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
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            state = state,
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        ) {
            items(reversed, key = { it.id }) { msg ->
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
    val subtleColor = textColor.copy(alpha = 0.55f)
    val shape = RoundedCornerShape(
        topStart = 12.dp, topEnd = 12.dp,
        bottomStart = if (message.isMine) 12.dp else 4.dp,
        bottomEnd = if (message.isMine) 4.dp else 12.dp,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (message.isMine) 48.dp else 0.dp,
                end = if (message.isMine) 0.dp else 48.dp,
            ),
        contentAlignment = alignment,
    ) {
        Surface(
            shape = shape,
            color = bubbleColor,
            modifier = Modifier,
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                // Sender header: name + SNR on same row
                if (!message.isMine && message.senderName != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = message.senderName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        message.snr?.let { snr ->
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = "SNR $snr",
                                style = MaterialTheme.typography.labelSmall,
                                color = subtleColor,
                            )
                        }
                    }
                }

                // Message text
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    modifier = Modifier.padding(top = if (!message.isMine && message.senderName != null) 2.dp else 0.dp),
                )

                // Footer: timestamp + status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 3.dp),
                ) {
                    Text(
                        text = formatTimestamp(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = subtleColor,
                    )
                    if (message.isMine) {
                        Text(
                            text = "\u00B7",
                            style = MaterialTheme.typography.labelSmall,
                            color = subtleColor,
                        )
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
                            else subtleColor,
                        )
                    }
                    // SNR for own messages (received messages show it in header)
                    if (message.isMine && message.snr != null) {
                        Text(
                            text = "\u00B7 SNR ${message.snr}",
                            style = MaterialTheme.typography.labelSmall,
                            color = subtleColor,
                        )
                    }
                }
            }
        }
    }
}

private val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
private val dateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm")

private fun formatTimestamp(instant: Instant): String {
    val epochMs = instant.toEpochMilliseconds()
    if (epochMs == 0L) return ""
    val zdt = java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault())
    val now = java.time.LocalDate.now()
    return if (zdt.toLocalDate() == now) {
        zdt.format(timeFormatter)
    } else {
        zdt.format(dateTimeFormatter)
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
            enabled = enabled,
            placeholder = {
                Text(if (enabled) placeholder else "Not connected")
            },
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
