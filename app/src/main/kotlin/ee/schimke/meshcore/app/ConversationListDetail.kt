@file:OptIn(ExperimentalMaterial3AdaptiveApi::class)

package ee.schimke.meshcore.app

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.SceneStrategy
import ee.schimke.meshcore.components.ui.ListDetailPlaceholder

// Adaptive list-detail layout for large screens and foldables. The Device screen (Channels /
// Contacts / Rooms / Repeaters) is the list pane and the open conversation (ContactRoute, which
// rooms and repeaters open too, or ChannelRoute) is the detail pane. When the window is wide
// enough for two panes, NavDisplay shows them side by side, with a placeholder until a
// conversation is picked; otherwise the strategy declines and NavDisplay falls back to today's
// single-pane navigation. Every other route stays full screen.

/**
 * The scene strategy for the app's NavDisplay. [directive] decides how many panes fit; it defaults
 * to the current window's, so two panes show from expanded width (840dp) up.
 *
 * Back pops one entry at a time ([BackNavigationBehavior.PopLatest]). The default,
 * `PopUntilScaffoldValueChange`, finds no back target with a conversation open in two panes:
 * closing it shows the placeholder in the same two-pane layout, so the scaffold value does not
 * change and back would leave the app instead of closing the conversation.
 */
@Composable
internal fun rememberConversationSceneStrategy(
  directive: PaneScaffoldDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2()),
): SceneStrategy<NavKey> =
  rememberListDetailSceneStrategy(
    backNavigationBehavior = BackNavigationBehavior.PopLatest,
    directive = directive,
  )

/** NavEntry metadata for [DeviceRoute]: the list pane, with the shared placeholder as detail. */
internal val DeviceListPaneMetadata: Map<String, Any> =
  ListDetailSceneStrategy.listPane(detailPlaceholder = { ListDetailPlaceholder() })

/** NavEntry metadata for [ContactRoute] and [ChannelRoute]: the detail pane. */
internal val ConversationPaneMetadata: Map<String, Any> = ListDetailSceneStrategy.detailPane()

/**
 * Opens the conversation [route] from the Device lists.
 *
 * In two panes the list stays visible while a conversation is open, so picking another one swaps
 * the detail instead of stacking a conversation per tap: back then closes the conversation rather
 * than walking through every chat opened. Picking the open one again does nothing. In one pane the
 * Device screen is on top whenever its lists can be tapped, so this pushes the conversation as
 * before.
 */
internal fun MutableList<NavKey>.openConversation(route: NavKey) {
  val top = lastOrNull()
  when {
    top == route -> Unit
    top is ContactRoute || top is ChannelRoute -> set(lastIndex, route)
    else -> add(route)
  }
}
