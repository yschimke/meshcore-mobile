package ee.schimke.meshcore.desktop

import androidx.compose.runtime.*
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ee.schimke.meshcore.components.ui.MeshcoreClientApp
import ee.schimke.meshcore.components.ui.theme.MeshcoreTheme
import ee.schimke.meshcore.core.manager.MeshCoreManager
import ee.schimke.meshcore.transport.tcp.TcpTransport
import java.awt.Toolkit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The X11 WM_CLASS, matched by `StartupWMClass` in the Arch package's `meshcore-desktop.desktop`.
 * Not `meshcore`: the AUR's meshcore-bin (the reference companion app) already uses that name.
 */
private const val WM_CLASS = "meshcore-desktop"

fun main() {
  nameX11WindowClass()
  val icon =
    BitmapPainter(
      checkNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream("meshcore.png"))
        .use(::loadImageBitmap),
    )
  application {
    val manager = remember { MeshCoreManager() }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    Window(
      title = "MeshCore",
      icon = icon,
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
}

/**
 * AWT names every window's class after the main class (`ee-schimke-meshcore-desktop-MainKt`), so
 * docks and taskbars can't match the window to its launcher. Must run before the first window.
 */
private fun nameX11WindowClass() {
  val toolkit = Toolkit.getDefaultToolkit()
  if (toolkit.javaClass.name != "sun.awt.X11.XToolkit") return
  toolkit.javaClass.getDeclaredField("awtAppClassName").apply {
    isAccessible = true
    set(toolkit, WM_CLASS)
  }
}
