@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package ee.schimke.meshcore.desktop

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import ee.schimke.meshcore.components.ui.MeshcoreClientApp
import ee.schimke.meshcore.components.ui.theme.MeshcoreTheme
import ee.schimke.meshcore.core.manager.ManagerState
import ee.schimke.meshcore.core.manager.MeshCoreManager
import ee.schimke.meshcore.data.createMeshcoreDatabase
import ee.schimke.meshcore.data.repository.SavedTransport
import ee.schimke.meshcore.transport.tcp.TcpTransport
import java.awt.Desktop
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.desktop.AppReopenedListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The X11 WM_CLASS, matched by `StartupWMClass` in the Arch package's `meshcore-desktop.desktop`.
 * Not `meshcore`: the AUR's meshcore-bin (the reference companion app) already uses that name.
 */
private const val WM_CLASS = "meshcore-desktop"

fun main() {
  val isMac = System.getProperty("os.name").contains("Mac")
  if (isMac) {
    System.setProperty("apple.laf.useScreenMenuBar", "true")
    System.setProperty("apple.awt.application.name", "MeshCore")
  }
  nameX11WindowClass()
  val icon =
    BitmapPainter(
      checkNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream("meshcore.png"))
        .use(::loadImageBitmap),
    )
  application {
    val scope = rememberCoroutineScope()
    val manager = remember { MeshCoreManager(scope) }
    val connection by manager.state.collectAsState()
    val client = (connection as? ManagerState.Connected)?.client
    val database = remember { createMeshcoreDatabase() }
    DisposableEffect(database) { onDispose { database.close() } }
    val settings = remember {
      DesktopSettings(File(System.getProperty("user.home"), ".meshcore/desktop.properties"))
    }
    var preferences by remember { mutableStateOf(settings.load()) }
    val initialBounds = remember {
      restoreBounds(
        preferences.bounds,
        GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { device ->
          val configuration = device.defaultConfiguration
          val area = Rectangle(configuration.bounds)
          val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
          Rectangle(
            area.x + insets.left,
            area.y + insets.top,
            area.width - insets.left - insets.right,
            area.height - insets.top - insets.bottom,
          )
        },
      )
    }
    val windowState =
      rememberWindowState(
        width = initialBounds.width.dp,
        height = initialBounds.height.dp,
        position =
          if (preferences.positioned) WindowPosition(initialBounds.x.dp, initialBounds.y.dp)
          else WindowPosition.PlatformDefault,
        placement =
          if (preferences.maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
      )
    val history =
      remember(client) {
        client?.selfInfo?.value?.let { self ->
          DesktopChatHistory(database, self, SavedTransport.Tcp(preferences.host, preferences.port))
        }
      }
    var closing by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(true) }
    var showShortcuts by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    fun quit() {
      if (closing) return
      closing = true
      scope.launch {
        try {
          withContext(Dispatchers.IO) { runCatching { settings.save(preferences) } }
          withTimeoutOrNull(3_000) { withContext(Dispatchers.IO) { manager.disconnect() } }
        } finally {
          exitApplication()
        }
      }
    }
    suspend fun connect(host: String, port: Int) {
      manager.connect(TcpTransport(host, port))
      preferences = preferences.copy(host = host, port = port)
    }
    LaunchedEffect(settings) {
      snapshotFlow { preferences }
        .debounce(400)
        .collect { current ->
          withContext(Dispatchers.IO) {
            runCatching { settings.save(current) }
              .onFailure { System.err.println("Could not save desktop preferences: ${it.message}") }
          }
        }
    }
    // Closing a macOS window leaves the app available from the Dock. Quit still disconnects.
    DisposableEffect(Unit) {
      val desktop = if (isMac && Desktop.isDesktopSupported()) Desktop.getDesktop() else null
      val reopen = AppReopenedListener {
        visible = true
        windowState.isMinimized = false
      }
      desktop?.addAppEventListener(reopen)
      if (desktop?.isSupported(Desktop.Action.APP_QUIT_HANDLER) == true) {
        desktop.setQuitHandler { _, response ->
          response.cancelQuit()
          quit()
        }
      }
      onDispose {
        desktop?.removeAppEventListener(reopen)
        if (desktop?.isSupported(Desktop.Action.APP_QUIT_HANDLER) == true)
          desktop.setQuitHandler(null)
      }
    }
    Window(
      title = client?.selfInfo?.value?.name?.let { "$it — MeshCore" } ?: "MeshCore",
      icon = icon,
      state = windowState,
      visible = visible,
      onCloseRequest = { if (isMac) visible = false else quit() },
    ) {
      DisposableEffect(window) {
        window.minimumSize = Dimension(640, 480)
        val listener =
          object : ComponentAdapter() {
            fun saveBounds() {
              if (windowState.placement == WindowPlacement.Floating && !windowState.isMinimized) {
                preferences = preferences.copy(bounds = Rectangle(window.bounds), positioned = true)
              }
            }

            override fun componentMoved(event: ComponentEvent) = saveBounds()

            override fun componentResized(event: ComponentEvent) = saveBounds()
          }
        window.addComponentListener(listener)
        onDispose { window.removeComponentListener(listener) }
      }
      LaunchedEffect(windowState) {
        snapshotFlow { windowState.placement }
          .collect { placement ->
            if (placement != WindowPlacement.Fullscreen) {
              preferences = preferences.copy(maximized = placement == WindowPlacement.Maximized)
            }
          }
      }
      MenuBar {
        Menu("Connection", mnemonic = 'C') {
          Item(
            "Reconnect",
            enabled = !closing && connection !is ManagerState.Connecting && client == null,
            shortcut = KeyShortcut(Key.R, ctrl = !isMac, meta = isMac),
            onClick = {
              scope.launch {
                try {
                  connect(preferences.host, preferences.port)
                } catch (e: CancellationException) {
                  throw e
                } catch (e: Exception) {
                  connectionError = e.message ?: "Connection failed"
                }
              }
            },
          )
          Item(
            "Disconnect",
            enabled = !closing && connection !is ManagerState.Idle,
            shortcut = KeyShortcut(Key.D, ctrl = !isMac, meta = isMac, shift = true),
            onClick = manager::requestDisconnect,
          )
          if (!isMac) {
            Separator()
            Item("Quit", shortcut = KeyShortcut(Key.Q, ctrl = true), onClick = ::quit)
          }
        }
        Menu("Window", mnemonic = 'W') {
          CheckboxItem(
            "Full Screen",
            checked = windowState.placement == WindowPlacement.Fullscreen,
            shortcut =
              if (isMac) KeyShortcut(Key.F, ctrl = true, meta = true) else KeyShortcut(Key.F11),
            onCheckedChange = { fullscreen ->
              windowState.placement =
                if (fullscreen) WindowPlacement.Fullscreen
                else if (preferences.maximized) WindowPlacement.Maximized
                else WindowPlacement.Floating
            },
          )
          Item(
            "Minimize",
            shortcut = if (isMac) KeyShortcut(Key.M, meta = true) else null,
            onClick = { windowState.isMinimized = true },
          )
        }
        Menu("Help", mnemonic = 'H') {
          Item("Keyboard Shortcuts", onClick = { showShortcuts = true })
        }
      }
      MeshcoreTheme {
        MeshcoreClientApp(
          manager,
          connect = ::connect,
          history = history,
          defaultHost = preferences.host,
          defaultPort = preferences.port,
        )
        if (showShortcuts) {
          AlertDialog(
            onDismissRequest = { showShortcuts = false },
            title = { Text("Keyboard shortcuts") },
            text = {
              Text(
                "Enter: Send message\n" +
                  "${if (isMac) "⌘" else "Ctrl+"}R: Reconnect\n" +
                  "${if (isMac) "⇧⌘" else "Ctrl+Shift+"}D: Disconnect\n" +
                  "${if (isMac) "⌃⌘F" else "F11"}: Full screen\n" +
                  "Tab / Shift+Tab: Move keyboard focus\n" +
                  "Select message text to copy it.",
              )
            },
            confirmButton = { TextButton(onClick = { showShortcuts = false }) { Text("Close") } },
          )
        }
        connectionError?.let { message ->
          AlertDialog(
            onDismissRequest = { connectionError = null },
            title = { Text("Could not connect") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { connectionError = null }) { Text("Close") } },
          )
        }
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
