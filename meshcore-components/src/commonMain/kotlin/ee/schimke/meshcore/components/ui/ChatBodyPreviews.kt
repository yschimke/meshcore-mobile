package ee.schimke.meshcore.components.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ee.schimke.meshcore.components.ui.icons.MeshIcons
import ee.schimke.meshcore.components.ui.theme.MeshcoreTheme
import kotlin.time.Instant

// Design-parity preview subjects for the chat screens (contact 1:1, channel
// group, and device commands), on the CMP desktop render path. All three share
// the stateless ChatBody; the previews differ only in title/subtitle and the
// sample message list. See docs/design-parity.md.

private fun ts(secondsAgo: Long): Instant = Instant.fromEpochSeconds(1_700_000_000 - secondsAgo)

private val contactMessages =
  listOf(
    ChatMessage(
      id = "c1",
      senderName = null,
      text = "hey — are you on tonight?",
      timestamp = ts(900),
      snr = 6,
      isMine = false,
    ),
    ChatMessage(
      id = "c2",
      senderName = null,
      text = "yeah, firing up the node now",
      timestamp = ts(840),
      isMine = true,
      status = MessageStatus.Confirmed,
    ),
    ChatMessage(
      id = "c3",
      senderName = null,
      text = "nice — I'll relay through bob-repeater",
      timestamp = ts(120),
      snr = 4,
      isMine = false,
    ),
    ChatMessage(
      id = "c4",
      senderName = null,
      text = "sounds good, sending a test ping",
      timestamp = ts(30),
      isMine = true,
      status = MessageStatus.Sent,
    ),
  )

private val channelMessages =
  listOf(
    ChatMessage(
      id = "g1",
      senderName = "alice",
      text = "anyone near the north ridge?",
      timestamp = ts(1200),
      snr = 7,
      isMine = false,
    ),
    ChatMessage(
      id = "g2",
      senderName = "bob-repeater",
      text = "I've got line of sight, relaying",
      timestamp = ts(1080),
      snr = 3,
      isMine = false,
    ),
    ChatMessage(
      id = "g3",
      senderName = null,
      text = "copy — net looks healthy",
      timestamp = ts(300),
      isMine = true,
      status = MessageStatus.Confirmed,
    ),
    ChatMessage(
      id = "g4",
      senderName = null,
      text = "broadcasting weather update",
      timestamp = ts(20),
      isMine = true,
      status = MessageStatus.Sending,
    ),
  )

private val commandMessages =
  listOf(
    ChatMessage(
      id = "x1",
      senderName = null,
      text = "get bat",
      timestamp = ts(180),
      isMine = true,
      status = MessageStatus.Sent,
    ),
    ChatMessage(
      id = "x2",
      senderName = "device",
      text = "battery: 3980 mV (97%)",
      timestamp = ts(176),
      snr = 9,
      isMine = false,
    ),
    ChatMessage(
      id = "x3",
      senderName = null,
      text = "get freq",
      timestamp = ts(40),
      isMine = true,
      status = MessageStatus.Confirmed,
    ),
    ChatMessage(
      id = "x4",
      senderName = "device",
      text = "freq: 869.525 MHz · BW 125 kHz · SF 10 · CR 5",
      timestamp = ts(36),
      snr = 9,
      isMine = false,
    ),
  )

@Composable
private fun chatPreview(
  title: String,
  subtitle: String?,
  messages: List<ChatMessage>,
  dark: Boolean,
  inputPlaceholder: String = "Message",
  actions: @Composable RowScope.() -> Unit = {},
) {
  MeshcoreTheme(darkTheme = dark) {
    ChatBody(
      title = title,
      subtitle = subtitle,
      messages = messages,
      draft = "",
      onDraftChange = {},
      onSend = {},
      onBack = {},
      inputPlaceholder = inputPlaceholder,
      actions = actions,
    )
  }
}

@Composable
private fun RowScope.terminalAction() {
  Icon(
    MeshIcons.Terminal,
    contentDescription = null,
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(end = 12.dp),
  )
}

/**
 * Design-parity subject: the 1:1 contact chat (light). Reference `design/ContactChat.light.html`.
 */
@Preview(
  showBackground = true,
  showSystemUi = true,
  device = Devices.PIXEL_7,
  name = "Contact chat",
)
@Composable
fun ContactChatPreview() = chatPreview("alice", "Direct message", contactMessages, dark = false)

/** Design-parity subject: the 1:1 contact chat (dark). Reference `design/ContactChat.dark.html`. */
@Preview(
  showBackground = true,
  showSystemUi = true,
  device = Devices.PIXEL_7,
  uiMode = 0x20,
  name = "Contact chat — dark",
)
@Composable
fun ContactChatDarkPreview() = chatPreview("alice", "Direct message", contactMessages, dark = true)

/**
 * Arabic (`ar`) locale variant of [ContactChatPreview] — the `catalog.spec.json` `Chat/Contact`
 * `props:{locale:"ar"}` variant.
 *
 * `ChatBody` draws only three `stringResource(...)` strings (the input placeholder and the send /
 * back content descriptions), so a `de` or `ja` variant would render all but indistinguishable from
 * English and earn its place in the catalog on nothing. **RTL is different**: it mirrors the entire
 * layout regardless of how much localized copy a screen carries — message bubbles swap sides, the
 * back arrow flips, and the input row's leading/trailing affordances exchange ends. That is real
 * coverage, and it's why `ar` is the one locale worth carrying on every screen.
 *
 * Sender names and message bodies stay literal, so any mirroring visible here is layout, not copy.
 */
@Preview(
  showBackground = true,
  showSystemUi = true,
  device = Devices.PIXEL_7,
  locale = "ar",
  name = "Contact chat — Arabic",
)
@Composable
fun ContactChatArabicPreview() =
  chatPreview("alice", "Direct message", contactMessages, dark = false)

/**
 * Design-parity subject: the group channel chat (light). Reference `design/ChannelChat.light.html`.
 */
@Preview(
  showBackground = true,
  showSystemUi = true,
  device = Devices.PIXEL_7,
  name = "Channel chat",
)
@Composable
fun ChannelChatPreview() = chatPreview("General", "Channel 0", channelMessages, dark = false)

/**
 * Design-parity subject: the group channel chat (dark). Reference `design/ChannelChat.dark.html`.
 */
@Preview(
  showBackground = true,
  showSystemUi = true,
  device = Devices.PIXEL_7,
  uiMode = 0x20,
  name = "Channel chat — dark",
)
@Composable
fun ChannelChatDarkPreview() = chatPreview("General", "Channel 0", channelMessages, dark = true)

/**
 * Arabic (`ar`) locale variant of [ChannelChatPreview] — see [ContactChatArabicPreview] for why RTL
 * is the locale worth carrying here. The channel variant additionally mirrors the subtitle row
 * ("Channel 0") under the title.
 */
@Preview(
  showBackground = true,
  showSystemUi = true,
  device = Devices.PIXEL_7,
  locale = "ar",
  name = "Channel chat — Arabic",
)
@Composable
fun ChannelChatArabicPreview() = chatPreview("General", "Channel 0", channelMessages, dark = false)

/**
 * Design-parity subject: the device commands console (light). Reference
 * `design/Commands.light.html`.
 */
@Preview(showBackground = true, showSystemUi = true, device = Devices.PIXEL_7, name = "Commands")
@Composable
fun CommandsPreview() =
  chatPreview(
    title = "Commands",
    subtitle = null,
    messages = commandMessages,
    dark = false,
    inputPlaceholder = "Enter command…",
    actions = { terminalAction() },
  )

/**
 * Arabic (`ar`) locale variant of [CommandsPreview] — see [ContactChatArabicPreview] for why RTL is
 * the locale worth carrying here. The commands console is the most interesting of the three under
 * mirroring: its monospace command/response log and terminal action keep Latin content inside an
 * otherwise mirrored frame, which is exactly where bidirectional layout tends to go wrong.
 */
@Preview(
  showBackground = true,
  showSystemUi = true,
  device = Devices.PIXEL_7,
  locale = "ar",
  name = "Commands — Arabic",
)
@Composable
fun CommandsArabicPreview() =
  chatPreview(
    title = "Commands",
    subtitle = null,
    messages = commandMessages,
    dark = false,
    inputPlaceholder = "Enter command…",
    actions = { terminalAction() },
  )

/**
 * Design-parity subject: the device commands console (dark). Reference `design/Commands.dark.html`.
 */
@Preview(
  showBackground = true,
  showSystemUi = true,
  device = Devices.PIXEL_7,
  uiMode = 0x20,
  name = "Commands — dark",
)
@Composable
fun CommandsDarkPreview() =
  chatPreview(
    title = "Commands",
    subtitle = null,
    messages = commandMessages,
    dark = true,
    inputPlaceholder = "Enter command…",
    actions = { terminalAction() },
  )
