package ee.schimke.meshcore.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.mordant.rendering.TextColors.gray
import com.juul.kable.toIdentifier
import ee.schimke.meshcore.core.client.MeshCoreClient
import ee.schimke.meshcore.core.transport.Transport
import ee.schimke.meshcore.data.repository.SavedTransport
import ee.schimke.meshcore.transport.ble.BleScanner
import ee.schimke.meshcore.transport.ble.BleTransport
import ee.schimke.meshcore.transport.tcp.TcpTransport
import ee.schimke.meshcore.transport.usb.JvmSerialPort
import ee.schimke.meshcore.transport.usb.UsbSerialTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

abstract class SessionCommand(name: String, help: String) : CliktCommand(name = name) {

  private val helpText = help

  override fun help(context: com.github.ajalt.clikt.core.Context): String = helpText

  protected val host by option("--host", "-h").help("TCP host of the MeshCore companion")

  protected val port by option("--port", "-p").int().help("TCP port of the MeshCore companion")

  protected val ble by
    option("--ble").help("BLE device identifier (MAC address) or name prefix to scan for")

  protected val usb by
    option("--usb")
      .help("USB serial port name (e.g. /dev/ttyUSB0, COM3). Use --usb list to enumerate.")

  protected val warmupMs by
    option("--warmup")
      .int()
      .help("How long to wait after CMD_APP_START for device responses (ms). Default: 400")

  private val store = DeviceStore()

  private fun createTransport(): Transport =
    when {
      ble != null -> createBleTransport(ble!!)
      usb != null -> createUsbTransport(usb!!)
      host != null || port != null -> TcpTransport(host ?: "127.0.0.1", port ?: 5000)
      else -> {
        val saved = store.getFavorite()
        if (saved != null) {
          terminal.println(gray("Using saved device ${saved.first}:${saved.second}"))
          TcpTransport(saved.first, saved.second)
        } else {
          TcpTransport("127.0.0.1", 5000)
        }
      }
    }

  private fun createBleTransport(identifier: String): Transport {
    if (identifier.contains(":") || identifier.contains("-")) {
      terminal.println(gray("Connecting to BLE device $identifier..."))
      return BleTransport.fromIdentifier(identifier.toIdentifier())
    }
    terminal.println(gray("Scanning for BLE device matching '$identifier' (10s timeout)..."))
    return runBlocking {
      val scanner = BleScanner()
      val adv =
        withTimeoutOrNull(10_000) {
          scanner.advertisements.first { a ->
            a.name?.contains(identifier, ignoreCase = true) == true
          }
        } ?: error("No BLE device matching '$identifier' found within 10s.")
      terminal.println(gray("Found ${adv.name} (${adv.identifier})"))
      BleTransport(adv)
    }
  }

  private fun createUsbTransport(portName: String): Transport {
    if (portName == "list") {
      val ports = JvmSerialPort.listPorts()
      if (ports.isEmpty()) {
        terminal.println("No USB serial ports found.")
      } else {
        terminal.println("Available USB serial ports:")
        ports.forEach { p -> terminal.println("  ${p.portName} — ${p.descriptivePortName}") }
      }
      throw ProgramResult(0)
    }
    val ports = JvmSerialPort.listPorts()
    val match =
      ports.firstOrNull { it.portName == portName || it.portName.endsWith(portName) }
        ?: error("USB port '$portName' not found. Available: ${ports.map { it.portName }}")
    terminal.println(gray("Using USB port ${match.portName} (${match.descriptivePortName})"))
    return UsbSerialTransport(match)
  }

  protected fun <T> withClient(block: suspend (MeshCoreClient) -> T): T = runBlocking {
    val transport = createTransport()
    val warmup = warmupMs
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    transport.connect()
    val client = MeshCoreClient(transport, scope)
    try {
      client.start() // waits for SelfInfo response
      if (warmup != null && warmup > 0) delay(warmup.toLong())
      val result = block(client)
      // Persist device to Room on success (TCP only for now)
      if (transport is TcpTransport) {
        persistDevice(host ?: "127.0.0.1", port ?: 5000, client)
      }
      result
    } finally {
      client.stop()
      transport.close()
      scope.cancel()
    }
  }

  private suspend fun persistDevice(host: String, port: Int, client: MeshCoreClient) {
    runCatching {
      val repo = store.repository
      val self = client.selfInfo.value
      val label = self?.name ?: "tcp:$host:$port"
      val id = "tcp:$host:$port"
      repo.upsertDevice(id, label, SavedTransport.Tcp(host, port))
      // Persist state
      val now = System.currentTimeMillis()
      self?.let { repo.updateSelfInfo(id, it, now) }
      client.battery.value?.let { repo.updateBattery(id, it, now) }
      client.radio.value?.let { repo.updateRadio(id, it, now) }
      client.device.value?.let { repo.updateDeviceInfo(id, it, now) }
      if (client.contacts.value.isNotEmpty()) {
        repo.replaceContacts(id, client.contacts.value, now)
      }
      if (client.channels.value.isNotEmpty()) {
        repo.replaceChannels(id, client.channels.value, now)
      }
      // Set as favorite
      repo.toggleFavorite(id)
    }
  }
}
