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
import kotlinx.coroutines.runBlocking

fun main() = application {
  val manager = remember { MeshCoreManager() }
  var settings by remember { mutableStateOf(ThemeSettings()) }
  var pickerVisible by remember { mutableStateOf(false) }
  Window(
    title = "MeshCore",
    state = rememberWindowState(width = 1100.dp, height = 800.dp),
    onCloseRequest = {
      runBlocking { manager.disconnect() }
      exitApplication()
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
