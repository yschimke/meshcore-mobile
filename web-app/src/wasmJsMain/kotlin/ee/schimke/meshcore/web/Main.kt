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
import ee.schimke.meshcore.core.manager.ManagerState
import ee.schimke.meshcore.core.manager.MeshCoreManager
import ee.schimke.meshcore.transport.webserial.SerialPort
import ee.schimke.meshcore.transport.webserial.SerialPortRequest
import ee.schimke.meshcore.transport.webserial.WebSerial
import ee.schimke.meshcore.transport.webserial.WebSerialTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun main() {
  ComposeViewport(viewportContainerId = "app", configure = { enableBrowserWindowInsets = true }) {
    val manager = remember { MeshCoreManager() }
    // The port of the connection in progress, recorded with the radio once it connects.
    var port by remember { mutableStateOf<SerialPort?>(null) }
    LaunchedEffect(manager) {
      manager.state.collect { state ->
        val client = (state as? ManagerState.Connected)?.client ?: return@collect
        val self = client.selfInfo.filterNotNull().first()
        try {
          RadioStore.remember(
            SavedRadio(
              publicKey = self.publicKey.toHex(),
              name = self.name,
              usbVendorId = port?.usbVendorId,
              usbProductId = port?.usbProductId,
              lastConnectedMs = nowMs(),
            )
          )
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          // The list is a convenience; a failed save never blocks the connection.
        }
      }
    }
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
        SerialConnectPanel(manager, busy, onPort = { port = it })
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
private fun SerialConnectPanel(
  manager: MeshCoreManager,
  busy: Boolean,
  onPort: (SerialPort) -> Unit,
) {
  val scope = rememberCoroutineScope()
  var selecting by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var radios by remember { mutableStateOf<List<SavedRadio>>(emptyList()) }
  var granted by remember { mutableStateOf<List<SerialPort>>(emptyList()) }
  LaunchedEffect(Unit) {
    // Reloaded each time this panel shows, so radios added from another browser appear.
    radios = runCatching { RadioStore.load() }.getOrDefault(emptyList())
    granted = runCatching { WebSerial.grantedPorts() }.getOrDefault(emptyList())
  }
  val enabled = WebSerial.isSupported && !busy && !selecting
  fun connect(port: SerialPort?, request: () -> SerialPortRequest) {
    error = null
    selecting = true
    // Browser permission must be requested during this user gesture.
    val pending = if (port == null) request() else null
    scope.launch {
      try {
        val chosen = port ?: pending!!.awaitPort()
        onPort(chosen)
        manager.connect(WebSerialTransport(chosen.connection()))
      } catch (e: CancellationException) {
        withContext(NonCancellable) { manager.disconnect() }
        throw e
      } catch (e: Exception) {
        withContext(NonCancellable) { manager.disconnect() }
        error = "Could not connect. Choose a USB radio running MeshCore companion firmware."
      } finally {
        selecting = false
      }
    }
  }
  Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text("Plug in a MeshCore companion radio by USB, then choose its serial port.")
    if (!WebSerial.isSupported) {
      Text("USB connections require Chrome or Edge on desktop and an HTTPS page.")
    }
    Button(enabled = enabled, onClick = { connect(null) { WebSerial.requestPort() } }) {
      Text(if (busy || selecting) "Connecting…" else "Connect USB radio")
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (radios.isNotEmpty()) {
      Text("Your radios", style = MaterialTheme.typography.titleMedium)
      radios.forEach { radio ->
        SavedRadioRow(
          radio = radio,
          enabled = enabled,
          onConnect = {
            // A port this browser already granted with the same USB ids opens without a picker,
            // but only when no other saved radio shares those ids: otherwise the port could be
            // the other radio. Every other case uses the picker, narrowed to the ids. Port grants
            // never leave the browser.
            fun SerialPort.matches(r: SavedRadio) =
              r.usbVendorId != null &&
                usbVendorId == r.usbVendorId &&
                usbProductId == r.usbProductId
            val ambiguous = radios.any {
              it.publicKey != radio.publicKey &&
                it.usbVendorId == radio.usbVendorId &&
                it.usbProductId == radio.usbProductId
            }
            val match = if (ambiguous) null else granted.filter { it.matches(radio) }.singleOrNull()
            connect(match) { WebSerial.requestPort(radio.usbVendorId, radio.usbProductId) }
          },
          onForget = {
            scope.launch {
              radios = runCatching { RadioStore.forget(radio.publicKey) }.getOrDefault(radios)
            }
          },
        )
      }
    }
  }
}

@Composable
private fun SavedRadioRow(
  radio: SavedRadio,
  enabled: Boolean,
  onConnect: () -> Unit,
  onForget: () -> Unit,
) {
  OutlinedCard(Modifier.fillMaxWidth()) {
    Row(
      Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
      verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Column(Modifier.weight(1f)) {
        Text(radio.name.ifBlank { "Unnamed radio" }, style = MaterialTheme.typography.bodyLarge)
        Text(
          radio.publicKey.take(8),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      TextButton(onClick = onForget) { Text("Forget") }
      Button(enabled = enabled, onClick = onConnect) { Text("Connect") }
    }
  }
}
