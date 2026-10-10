package ee.schimke.meshcore.components.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ee.schimke.composeai.rcplayer.compose.RcComposePlayer
import ee.schimke.composeai.rcplayer.runtime.RcPlayerEvent

/** Play wire documents on Android or desktop without Android View interop. */
@Composable
fun RemoteComposeCard(
  bytes: ByteArray,
  modifier: Modifier = Modifier,
  onEvent: (RcPlayerEvent) -> Unit = {},
) {
  RcComposePlayer(bytes = bytes, modifier = modifier, onEvent = onEvent)
}
