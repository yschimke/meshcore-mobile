package ee.schimke.meshcore.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.mordant.rendering.TextColors.gray
import ee.schimke.meshcore.app.data.proto.BatteryInfoPb
import ee.schimke.meshcore.app.data.proto.ContactPb
import ee.schimke.meshcore.app.data.proto.DeviceInfoPb
import ee.schimke.meshcore.app.data.proto.DeviceSnapshotPb
import ee.schimke.meshcore.app.data.proto.RadioSettingsPb
import ee.schimke.meshcore.app.data.proto.SelfInfoPb
import ee.schimke.meshcore.core.client.MeshCoreClient
import ee.schimke.meshcore.core.transport.Transport
import com.juul.kable.toIdentifier
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
import okio.ByteString.Companion.toByteString

/**
 * Mixin that every CLI subcommand inherits from to get shared
 * transport options plus a [withClient] helper that opens a transport,
 * runs the block, and tears everything down.
 *
 * ## Transport selection
 *
 * - `--host`/`--port` → TCP (default, most common for CLI)
 * - `--ble <mac-or-name>` → Bluetooth LE via Kable
 * - `--usb <port-name>` → USB serial via jSerialComm (e.g. `/dev/ttyUSB0`)
 *
 * The device is "sticky": on first use you specify the transport,
 * and on subsequent runs the last-used device is remembered in
 * `~/.meshcore/devices.pb`. Override anytime with explicit flags.
 */
abstract class SessionCommand(
    name: String,
    help: String,
) : CliktCommand(name = name) {

    private val helpText = help
    override fun help(context: com.github.ajalt.clikt.core.Context): String = helpText

    protected val host by option("--host", "-h")
        .help("TCP host of the MeshCore companion")

    protected val port by option("--port", "-p").int()
        .help("TCP port of the MeshCore companion")

    protected val ble by option("--ble")
        .help("BLE device identifier (MAC address) or name prefix to scan for")

    protected val usb by option("--usb")
        .help("USB serial port name (e.g. /dev/ttyUSB0, COM3). Use --usb list to enumerate.")

    protected val warmupMs by option("--warmup")
        .int()
        .help("How long to wait after CMD_APP_START for device responses (ms). Default: 400")

    private val store = DeviceStore()

    private fun createTransport(): Transport = when {
        ble != null -> createBleTransport(ble!!)
        usb != null -> createUsbTransport(usb!!)
        host != null || port != null -> TcpTransport(host ?: "127.0.0.1", port ?: 5000)
        else -> {
            // Try saved favorite
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
        // If it looks like a MAC address, connect directly
        if (identifier.contains(":") || identifier.contains("-")) {
            terminal.println(gray("Connecting to BLE device $identifier..."))
            return BleTransport.fromIdentifier(identifier.toIdentifier())
        }
        // Otherwise, scan and find by name prefix
        terminal.println(gray("Scanning for BLE device matching '$identifier' (10s timeout)..."))
        return runBlocking {
            val scanner = BleScanner()
            val adv = withTimeoutOrNull(10_000) {
                scanner.advertisements.first { a ->
                    a.name?.contains(identifier, ignoreCase = true) == true
                }
            } ?: error(
                "No BLE device matching '$identifier' found within 10s. " +
                    "Ensure Bluetooth is enabled and the device is advertising.",
            )
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
                ports.forEach { p ->
                    terminal.println("  ${p.portName} — ${p.descriptivePortName}")
                }
            }
            throw com.github.ajalt.clikt.core.ProgramResult(0)
        }
        val ports = JvmSerialPort.listPorts()
        val match = ports.firstOrNull {
            it.portName == portName || it.portName.endsWith(portName)
        } ?: error("USB port '$portName' not found. Available: ${ports.map { it.portName }}")
        terminal.println(gray("Using USB port ${match.portName} (${match.descriptivePortName})"))
        return UsbSerialTransport(match)
    }

    protected fun <T> withClient(block: suspend (MeshCoreClient) -> T): T = runBlocking {
        val transport = createTransport()
        val warmup = warmupMs ?: 400
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        transport.connect()
        val client = MeshCoreClient(transport, scope)
        try {
            client.start()
            if (warmup > 0) delay(warmup.toLong())
            val result = block(client)
            // Persist device + snapshot on success (TCP only for now)
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

    private fun persistDevice(host: String, port: Int, client: MeshCoreClient) {
        runCatching {
            val self = client.selfInfo.value
            val label = self?.name ?: "tcp:$host:$port"
            val now = System.currentTimeMillis()
            val snapshot = DeviceSnapshotPb(
                self_info = self?.let {
                    SelfInfoPb(
                        advert_type = it.advertType,
                        tx_power_dbm = it.txPowerDbm,
                        max_power_dbm = it.maxPowerDbm,
                        public_key = it.publicKey.bytes.toByteArray().toByteString(),
                        latitude = it.latitude,
                        longitude = it.longitude,
                        name = it.name,
                    )
                },
                self_info_at_ms = if (self != null) now else 0L,
                contacts = client.contacts.value.map { c ->
                    ContactPb(
                        public_key = c.publicKey.bytes.toByteArray().toByteString(),
                        type = c.type.raw,
                        flags = c.flags,
                        path_length = c.pathLength,
                        name = c.name,
                        advert_timestamp_epoch_s = c.advertTimestamp.epochSeconds,
                        latitude = c.latitude,
                        longitude = c.longitude,
                        last_modified_epoch_s = c.lastModified.epochSeconds,
                    )
                },
                contacts_at_ms = if (client.contacts.value.isNotEmpty()) now else 0L,
                battery = client.battery.value?.let {
                    BatteryInfoPb(
                        millivolts = it.millivolts,
                        storage_used_kb = it.storageUsedKb,
                        storage_total_kb = it.storageTotalKb,
                    )
                },
                battery_at_ms = if (client.battery.value != null) now else 0L,
                radio = client.radio.value?.let {
                    RadioSettingsPb(
                        frequency_hz = it.frequencyHz,
                        bandwidth_hz = it.bandwidthHz,
                        spreading_factor = it.spreadingFactor,
                        coding_rate = it.codingRate,
                    )
                },
                radio_at_ms = if (client.radio.value != null) now else 0L,
                device_info = client.device.value?.let {
                    DeviceInfoPb(
                        protocol_version = it.protocolVersion,
                        max_contacts = it.maxContacts,
                        max_channels = it.maxChannels,
                    )
                },
                device_info_at_ms = if (client.device.value != null) now else 0L,
            )
            store.recordConnect(host, port, label, snapshot)
        }
    }
}

// Keep the old name as a typealias for backward compatibility in existing commands
@Deprecated("Use SessionCommand", ReplaceWith("SessionCommand"))
typealias TcpSessionCommand = SessionCommand
