@file:OptIn(ExperimentalMaterial3AdaptiveApi::class)

package ee.schimke.meshcore.components.ui

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import ee.schimke.meshcore.components.ui.theme.MeshcoreTheme
import ee.schimke.meshcore.core.model.BatteryInfo
import ee.schimke.meshcore.core.model.ChannelInfo
import ee.schimke.meshcore.core.model.ContactType
import ee.schimke.meshcore.core.model.RadioSettings
import kotlin.time.Instant
import kotlinx.io.bytestring.ByteString

// Wide-window (desktop / web / tablet) previews of the list-detail layout. The directive is pinned
// to two panes so the preview does not depend on the renderer's window size class.

private val general = ChatDestination.Channel(0, "General")

@Composable
private fun listDetailPreview(open: ChatDestination?) {
  MeshcoreTheme {
    val history = buildList {
      add(ThreePaneScaffoldDestinationItem<ChatDestination>(ListDetailPaneScaffoldRole.List))
      if (open != null)
        add(ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.Detail, open))
    }
    val navigator =
      rememberListDetailPaneScaffoldNavigator(
        scaffoldDirective =
          calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())
            .copy(maxHorizontalPartitions = 2),
        initialDestinationHistory = history,
      )
    MeshcoreListDetail(
      navigator = navigator,
      list = {
        DeviceBody(
          self = previewSelf(),
          battery = BatteryInfo(3980, 512, 4096),
          radio = RadioSettings(869_525_000, 125_000, 10, 5),
          contacts =
            listOf(
              previewContact("alice", -1, 0x11),
              previewContact("bob-repeater", 2, 0x22, ContactType.REPEATER),
              previewContact("common-room", 0, 0x33, ContactType.ROOM),
            ),
          channels =
            listOf(
              ChannelInfo(0, "General", ByteString()),
              ChannelInfo(1, "Emergency", ByteString()),
            ),
          onDisconnect = {},
          onSettingsClick = null,
        )
      },
      detail = { destination ->
        ChatBody(
          title = destination.name,
          subtitle = "Channel 0",
          messages =
            listOf(
              ChatMessage(
                id = "g1",
                senderName = "carol",
                text = "anyone near the north ridge?",
                timestamp = Instant.fromEpochSeconds(1_700_000_000),
                snr = 7,
                isMine = false,
              ),
              ChatMessage(
                id = "g2",
                senderName = null,
                text = "on my way up now",
                timestamp = Instant.fromEpochSeconds(1_700_000_060),
                isMine = true,
                status = MessageStatus.Confirmed,
              ),
            ),
          draft = "",
          onDraftChange = {},
          onSend = {},
          onBack = {},
        )
      },
    )
  }
}

/** Wide window with a channel open: the device lists beside the conversation. */
@Preview(showBackground = true, widthDp = 1200, heightDp = 800, name = "List-detail — open")
@Preview(
  showBackground = true,
  widthDp = 1200,
  heightDp = 800,
  uiMode = 0x20,
  name = "List-detail — open, dark",
)
@Composable
fun MeshcoreListDetailOpenPreview() = listDetailPreview(open = general)

/** Wide window before a conversation is picked: the detail pane shows its placeholder. */
@Preview(showBackground = true, widthDp = 1200, heightDp = 800, name = "List-detail — empty")
@Composable
fun MeshcoreListDetailEmptyPreview() = listDetailPreview(open = null)
