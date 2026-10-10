package ee.schimke.meshcore.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ee.schimke.meshcore.app.ui.theme.ThemeMode
import ee.schimke.meshcore.app.ui.theme.ThemePickerDialog
import ee.schimke.meshcore.app.ui.theme.ThemeSettings
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
  var settings by remember { mutableStateOf(ThemeSettings()) }
  var pickerVisible by remember { mutableStateOf(false) }
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
    val dark =
      when (settings.mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
      }
    MeshcoreTheme(darkTheme = dark) {
      MeshcoreClientApp(
        manager,
        connect = { host, port -> manager.connect(TcpTransport(host, port)) },
        onOpenThemePicker = { pickerVisible = true },
      )
      if (pickerVisible)
        ThemePickerDialog(
          settings,
          onModeSelect = { settings = settings.copy(mode = it) },
          onPaletteSelect = { settings = settings.copy(palette = it) },
          onDismiss = { pickerVisible = false },
          dynamicColorAvailable = false,
        )
    }
  }
}
