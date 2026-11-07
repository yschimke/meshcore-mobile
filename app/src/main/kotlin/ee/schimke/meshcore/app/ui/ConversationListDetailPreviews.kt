@file:OptIn(ExperimentalMaterial3AdaptiveApi::class)

package ee.schimke.meshcore.app.ui

import android.content.res.Configuration
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import ee.schimke.meshcore.app.ChannelRoute
import ee.schimke.meshcore.app.ConversationPaneMetadata
import ee.schimke.meshcore.app.DeviceListPaneMetadata
import ee.schimke.meshcore.app.DeviceRoute
import ee.schimke.meshcore.app.rememberConversationSceneStrategy
import ee.schimke.meshcore.app.ui.theme.MeshcoreTheme
import ee.schimke.meshcore.components.ui.ChatBody
import ee.schimke.meshcore.components.ui.ChatMessage
import ee.schimke.meshcore.components.ui.DeviceBody
import ee.schimke.meshcore.components.ui.MessageStatus
import ee.schimke.meshcore.core.model.BatteryInfo
import ee.schimke.meshcore.core.model.ChannelInfo
import ee.schimke.meshcore.core.model.ContactType
import ee.schimke.meshcore.core.model.RadioSettings
import kotlin.time.Instant
import kotlinx.io.bytestring.ByteString

// Large-screen previews of the phone app's list-detail layout: the app's NavDisplay scene strategy
// and pane metadata, with stateless content in place of the screens that need a connected device.
// The directive is pinned to two panes so the render does not depend on the renderer's window size
// class.

/** An unfolded foldable held landscape (Pixel Fold class inner display). */
private const val FOLDABLE_LANDSCAPE = "spec:width=841dp,height=673dp,dpi=420"

@Composable
private fun ConversationListDetailPreview(backStack: List<NavKey>) {
  val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
  val sceneStrategy = rememberConversationSceneStrategy(directive.copy(maxHorizontalPartitions = 2))
  MeshcoreTheme {
    NavDisplay(
      backStack = backStack,
      onBack = {},
      sceneStrategies = listOf(sceneStrategy),
      entryProvider =
        entryProvider {
          entry<DeviceRoute>(metadata = DeviceListPaneMetadata) {
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
            )
          }
          entry<ChannelRoute>(metadata = ConversationPaneMetadata) {
            ChatBody(
              title = "General",
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
          }
        },
    )
  }
}

/** A channel open: the Device lists beside the conversation. */
@Preview(showBackground = true, device = Devices.TABLET, name = "Tablet")
@Preview(
  showBackground = true,
  device = Devices.TABLET,
  name = "Tablet, dark",
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(showBackground = true, device = FOLDABLE_LANDSCAPE, name = "Foldable")
@Composable
fun ConversationListDetailOpenPreview() =
  ConversationListDetailPreview(backStack = listOf(DeviceRoute, ChannelRoute(0)))

/** Nothing picked yet: the detail pane shows the shared placeholder. */
@Preview(showBackground = true, device = Devices.TABLET, name = "Tablet")
@Preview(showBackground = true, device = FOLDABLE_LANDSCAPE, name = "Foldable")
@Composable
fun ConversationListDetailEmptyPreview() =
  ConversationListDetailPreview(backStack = listOf(DeviceRoute))
