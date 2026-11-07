@file:OptIn(ExperimentalMaterial3AdaptiveApi::class)

package ee.schimke.meshcore.components.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import ee.schimke.meshcore.components.generated.resources.Res
import ee.schimke.meshcore.components.generated.resources.list_detail_placeholder
import ee.schimke.meshcore.components.ui.theme.Dimens
import org.jetbrains.compose.resources.stringResource

/**
 * A conversation opened from the device lists: what the detail pane of [MeshcoreListDetail] shows.
 */
sealed interface ChatDestination {
  val name: String

  data class Contact(val key: String, override val name: String) : ChatDestination

  data class Channel(val index: Int, override val name: String) : ChatDestination
}

/**
 * Adaptive list-detail layout: the device body (Channels / Contacts / Rooms / Repeaters) as the
 * list pane and the open conversation as the detail pane. In an expanded-width window both panes
 * show side by side, with a placeholder until a conversation is picked; in compact and medium
 * windows only one pane shows at a time, as single-pane navigation.
 *
 * Compose Multiplatform's adaptive-navigation has no `NavigableListDetailPaneScaffold` for desktop
 * or web, so this drives `ListDetailPaneScaffold` from the [navigator]'s directive and state; the
 * conversations' own back buttons call [closeChat].
 */
@Composable
fun MeshcoreListDetail(
  navigator: ThreePaneScaffoldNavigator<ChatDestination>,
  list: @Composable () -> Unit,
  detail: @Composable (ChatDestination) -> Unit,
  modifier: Modifier = Modifier,
) {
  ListDetailPaneScaffold(
    directive = navigator.scaffoldDirective,
    scaffoldState = navigator.scaffoldState,
    modifier = modifier,
    listPane = { AnimatedPane { list() } },
    detailPane = {
      AnimatedPane {
        val destination = navigator.currentDestination?.contentKey
        if (destination != null) key(destination) { detail(destination) }
        else ListDetailPlaceholder()
      }
    },
  )
}

/** Open [destination] in the detail pane, replacing (not stacking on) any open conversation. */
suspend fun ThreePaneScaffoldNavigator<ChatDestination>.openChat(destination: ChatDestination) {
  if (currentDestination?.pane == ListDetailPaneScaffoldRole.Detail) {
    navigateBack(BackNavigationBehavior.PopLatest)
  }
  navigateTo(ListDetailPaneScaffoldRole.Detail, destination)
}

/** Close the open conversation, back to the list. */
suspend fun ThreePaneScaffoldNavigator<ChatDestination>.closeChat() {
  navigateBack(BackNavigationBehavior.PopLatest)
}

@Composable
private fun ListDetailPlaceholder() {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    modifier = Modifier.fillMaxSize(),
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(Dimens.ScreenPadding)) {
      Text(
        text = stringResource(Res.string.list_detail_placeholder),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
  }
}
