package ee.schimke.meshcore.session

import dev.mcarr.usb.interfaces.ISerialPortWrapper
import ee.schimke.meshcore.transport.usb.JvmSerialPort

/**
 * [UsbPortResolver] for JVM hosts (the CLI/TUI), backed by jSerialComm. Matches an enumerated port
 * by vendor/product id.
 */
class JvmUsbPortResolver : UsbPortResolver {
  override fun resolve(className: String, vendorId: Int, productId: Int): ISerialPortWrapper? =
    JvmSerialPort.listPorts().firstOrNull { it.vendorId == vendorId && it.productId == productId }
}
