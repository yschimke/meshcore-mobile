package ee.schimke.meshcore.session

import dev.mcarr.usb.interfaces.ISerialPortWrapper
import ee.schimke.meshcore.core.transport.Transport
import ee.schimke.meshcore.data.repository.SavedTransport
import ee.schimke.meshcore.data.repository.bleDeviceId
import ee.schimke.meshcore.data.repository.tcpDeviceId
import ee.schimke.meshcore.data.repository.usbDeviceId
import ee.schimke.meshcore.transport.usb.UsbSerialTransport

/**
 * A resolved connection: the live [transport] plus the canonical [deviceId] and [savedTransport]
 * descriptor used to persist it.
 */
data class ResolvedConnection(
  val transport: Transport,
  val deviceId: String,
  val savedTransport: SavedTransport,
)

/**
 * Single mapping of "neutral [ConnectionRequest] -> live [Transport]" shared by the phone app and
 * CLI. Implementations build the concrete transport for their platform; the wiring of
 * platform-specific bits (TCP/BLE construction, USB port resolution) is hidden behind
 * [createTcpTransport], [createBleTransport] and [UsbPortResolver].
 */
interface TransportFactory {
  fun resolve(request: ConnectionRequest): ResolvedConnection
}

/**
 * Resolves a USB descriptor (className + vendor/product id) to a concrete [ISerialPortWrapper].
 * Platform-specific: Android enumerates via `AndroidUsbPortLister`, the JVM via jSerialComm.
 */
fun interface UsbPortResolver {
  fun resolve(className: String, vendorId: Int, productId: Int): ISerialPortWrapper?
}

/** A [UsbPortResolver] that never finds a port; for BLE/TCP-only hosts. */
val NoUsbPortResolver: UsbPortResolver = UsbPortResolver { _, _, _ -> null }

/** Build a TCP transport. JVM-only `meshcore-transport-tcp` is wired per platform. */
internal expect fun createTcpTransport(host: String, port: Int): Transport

/** Build a BLE transport addressed by its stable [identifier]. */
internal expect fun createBleTransport(identifier: String): Transport

/**
 * Default [TransportFactory]. BLE and TCP construction are delegated to platform helpers; USB ports
 * are resolved through [usbPorts].
 */
class DefaultTransportFactory(private val usbPorts: UsbPortResolver = NoUsbPortResolver) :
  TransportFactory {
  override fun resolve(request: ConnectionRequest): ResolvedConnection =
    when (request) {
      is ConnectionRequest.Ble ->
        ResolvedConnection(
          transport = createBleTransport(request.identifier),
          deviceId = bleDeviceId(request.identifier),
          savedTransport = SavedTransport.Ble(request.identifier, request.name),
        )
      is ConnectionRequest.Tcp ->
        ResolvedConnection(
          transport = createTcpTransport(request.host, request.port),
          deviceId = tcpDeviceId(request.host, request.port),
          savedTransport = SavedTransport.Tcp(request.host, request.port),
        )
      is ConnectionRequest.Usb -> {
        val port =
          usbPorts.resolve(request.className, request.vendorId, request.productId)
            ?: error("USB port ${request.label} is no longer attached.")
        ResolvedConnection(
          transport = UsbSerialTransport(port),
          deviceId = usbDeviceId(request.className, request.vendorId, request.productId),
          savedTransport =
            SavedTransport.Usb(request.className, request.vendorId, request.productId),
        )
      }
    }
}
