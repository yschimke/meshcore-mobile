package ee.schimke.meshcore.mobile

import android.content.Context
import dev.mcarr.usb.impl.UsfaPortList
import dev.mcarr.usb.interfaces.ISerialPortWrapper
import ee.schimke.meshcore.components.ui.UsbPortDescriptor
import ee.schimke.meshcore.session.UsbPortResolver

/**
 * Android USB port enumeration, wrapping `kotlin-usb-client-library`'s
 * [UsfaPortList]. Exposes both transport-agnostic [descriptors] for the
 * UI and the concrete [ISerialPortWrapper]s used when opening a port.
 */
class AndroidUsbPortLister(private val context: Context) {
  /** Concrete attached USB serial ports (used when opening a connection). */
  fun list(): List<ISerialPortWrapper> = UsfaPortList(context.applicationContext).get()

  /** Transport-agnostic descriptors of the attached ports, for the UI. */
  fun descriptors(): List<UsbPortDescriptor> =
    list().map {
      UsbPortDescriptor(
        className = it::class.simpleName ?: "usb",
        vendorId = it.vendorId,
        productId = it.productId,
      )
    }
}

/**
 * [UsbPortResolver] backed by [AndroidUsbPortLister]: matches a saved
 * descriptor back to a live port by vendor/product id at connect time.
 */
class AndroidUsbPortResolver(private val lister: AndroidUsbPortLister) : UsbPortResolver {
  override fun resolve(className: String, vendorId: Int, productId: Int): ISerialPortWrapper? =
    lister.list().firstOrNull { it.vendorId == vendorId && it.productId == productId }
}
