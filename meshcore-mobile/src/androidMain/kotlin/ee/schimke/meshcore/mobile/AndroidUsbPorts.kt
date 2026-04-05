package ee.schimke.meshcore.mobile

import android.content.Context
import dev.mcarr.usb.impl.UsfaPortList
import dev.mcarr.usb.interfaces.ISerialPortWrapper
import dev.zacsweers.metro.Inject

/**
 * Android USB port enumeration, wrapping `kotlin-usb-client-library`'s
 * [UsfaPortList]. The `Context` is injected via the Metro dependency
 * graph, so callers never have to pass it around – they get an instance
 * of this class from their graph (for example [MobileGraph]).
 */
@Inject
class AndroidUsbPortLister(
    private val context: Context,
) {
    /** Enumerate attached USB serial devices. */
    fun list(): List<ISerialPortWrapper> =
        UsfaPortList(context.applicationContext).get()
}
