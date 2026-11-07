package ee.schimke.meshcore.desktop

import androidx.compose.runtime.*
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ee.schimke.meshcore.components.ui.MeshcoreClientApp
import ee.schimke.meshcore.components.ui.theme.MeshcoreTheme
import ee.schimke.meshcore.core.manager.MeshCoreManager
import ee.schimke.meshcore.transport.tcp.TcpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

fun main() = application {
  val manager = remember { MeshCoreManager() }
  val scope = rememberCoroutineScope()
  var closing by remember { mutableStateOf(false) }
  Window(
    title = "MeshCore",
    state = rememberWindowState(width = 1100.dp, height = 800.dp),
    onCloseRequest = {
      if (!closing) {
        closing = true
        scope.launch {
          try {
            withTimeoutOrNull(3_000) { withContext(Dispatchers.IO) { manager.disconnect() } }
          } finally {
            exitApplication()
          }
        }
      }
    },
  ) {
    MeshcoreTheme {
      MeshcoreClientApp(
        manager,
        connect = { host, port -> manager.connect(TcpTransport(host, port)) },
      )
    }
  }
}
