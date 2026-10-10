@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package ee.schimke.meshcore.web

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import ee.schimke.meshcore.app.ui.theme.*
import ee.schimke.meshcore.components.ui.MeshcoreClientApp
import ee.schimke.meshcore.components.ui.theme.MeshcoreTheme
import ee.schimke.meshcore.core.manager.MeshCoreManager
import ee.schimke.meshcore.transport.webserial.WebSerial
import ee.schimke.meshcore.transport.webserial.WebSerialTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

fun main() {
  ComposeViewport(viewportContainerId = "app", configure = { enableBrowserWindowInsets = true }) {
    val manager = remember { MeshCoreManager() }
    var settings by remember { mutableStateOf(ThemeSettings()) }
    var picker by remember { mutableStateOf(false) }
    val dark =
      when (settings.mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
      }
    MeshcoreTheme(darkTheme = dark) {
      MeshcoreClientApp(manager, onOpenThemePicker = { picker = true }) { busy ->
        SerialConnectPanel(manager, busy)
      }
      if (picker)
        ThemePickerDialog(
          settings,
          onModeSelect = { settings = settings.copy(mode = it) },
          onPaletteSelect = { settings = settings.copy(palette = it) },
          onDismiss = { picker = false },
          dynamicColorAvailable = false,
        )
    }
  }
}

@Composable
private fun SerialConnectPanel(manager: MeshCoreManager, busy: Boolean) {
  val scope = rememberCoroutineScope()
  var selecting by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text("Plug in a MeshCore companion radio by USB, then choose its serial port.")
    if (!WebSerial.isSupported) {
      Text("USB connections require Chrome or Edge on desktop and an HTTPS page.")
    }
    Button(
      enabled = WebSerial.isSupported && !busy && !selecting,
      onClick = {
        error = null
        selecting = true
        // Browser permission must be requested during this user gesture.
        val request = WebSerial.requestPort()
        scope.launch {
          try {
            manager.connect(WebSerialTransport(request.awaitConnection()))
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            error = "Could not connect. Choose a USB radio running MeshCore companion firmware."
          } finally {
            selecting = false
          }
        }
      },
    ) {
      Text(if (busy || selecting) "Connecting…" else "Connect USB radio")
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
  }
}
