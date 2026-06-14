package ee.schimke.meshcore.session

import ee.schimke.meshcore.data.repository.SavedDevice
import ee.schimke.meshcore.data.repository.SavedTransport

/**
 * Transport-agnostic description of a device to connect to. Holds only neutral descriptors (a
 * transport kind plus the parameters needed to locate the device) and carries no references to
 * concrete `meshcore-transport-*` types. [TransportFactory] turns one of these into a live
 * [ee.schimke.meshcore.core.transport.Transport].
 *
 * This is the single connection-request model shared by the phone app and the CLI, so both stay in
 * sync when a transport option or connection parameter is added.
 */
sealed class ConnectionRequest {
  /** Human-readable label for the device, suitable for UI/log output. */
  abstract val label: String

  /**
   * A Bluetooth LE device addressed by its stable [identifier] (a MAC address on Android). [name]
   * is the advertised name, if known.
   */
  data class Ble(val identifier: String, val name: String?) : ConnectionRequest() {
    override val label: String
      get() = name ?: identifier
  }

  /** A companion radio reachable over TCP at [host]:[port]. */
  data class Tcp(val host: String, val port: Int) : ConnectionRequest() {
    override val label: String
      get() = "$host:$port"
  }

  /**
   * A USB serial device addressed by the wrapper [className] plus its [vendorId] / [productId]. The
   * concrete serial port is resolved at the [TransportFactory] layer via a [UsbPortResolver].
   */
  data class Usb(
    val className: String,
    val vendorId: Int,
    val productId: Int,
    override val label: String = "USB ${hex4(vendorId)}:${hex4(productId)}",
  ) : ConnectionRequest()
}

/** Lower-case-free uppercase 4-digit hex, e.g. `0x1A86` -> `1A86`. */
internal fun hex4(value: Int): String = value.toString(16).uppercase().padStart(4, '0')

/**
 * Map a persisted [SavedDevice] back to the neutral [ConnectionRequest] used to reconnect to it.
 */
fun SavedDevice.toConnectionRequest(): ConnectionRequest =
  when (val t = transport) {
    is SavedTransport.Ble -> ConnectionRequest.Ble(t.identifier, t.advertName ?: label)
    is SavedTransport.Tcp -> ConnectionRequest.Tcp(t.host, t.port)
    is SavedTransport.Usb -> ConnectionRequest.Usb(t.className, t.vendorId, t.productId, label)
  }
