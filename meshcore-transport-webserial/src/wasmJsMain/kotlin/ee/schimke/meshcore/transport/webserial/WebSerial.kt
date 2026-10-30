@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package ee.schimke.meshcore.transport.webserial

import kotlin.js.JsAny
import kotlin.js.Promise
import kotlinx.coroutines.await

object WebSerial {
  val isSupported: Boolean
    get() = supported()

  /**
   * Call synchronously from the click handler, before any coroutine suspension. A USB vendor (and
   * product) id narrows the browser's picker to matching ports.
   */
  fun requestPort(usbVendorId: Int? = null, usbProductId: Int? = null): SerialPortRequest =
    SerialPortRequest(request(usbVendorId ?: -1, usbProductId ?: -1))

  /** Ports this browser already granted to the page; they connect without a picker. */
  suspend fun grantedPorts(): List<SerialPort> {
    if (!isSupported) return emptyList()
    val ports = grantedPortsArray().await<JsAny>()
    return List(portCount(ports)) { SerialPort(portAt(ports, it)) }
  }
}

class SerialPortRequest internal constructor(private val promise: Promise<JsAny>) {
  suspend fun awaitPort(): SerialPort = SerialPort(promise.await())

  suspend fun awaitConnection(): SerialConnection = awaitPort().connection()
}

/** A browser serial port the page may open; its USB ids are absent for non-USB ports. */
class SerialPort internal constructor(private val port: JsAny) {
  val usbVendorId: Int?
    get() = usbVendorId(port).takeIf { it >= 0 }

  val usbProductId: Int?
    get() = usbProductId(port).takeIf { it >= 0 }

  fun connection(): SerialConnection = BrowserSerialConnection(session(port))
}

private class BrowserSerialConnection(private val handle: JsAny) : SerialConnection {
  override suspend fun open() {
    open(handle).await()
  }

  override suspend fun read(): ByteArray? {
    val chunk = read(handle).await() ?: return null
    return ByteArray(length(chunk)) { byteAt(chunk, it).toByte() }
  }

  override suspend fun write(bytes: ByteArray) {
    val chunk = array(bytes.size)
    bytes.forEachIndexed { index, byte -> setByte(chunk, index, byte.toInt() and 255) }
    write(handle, chunk).await()
  }

  override suspend fun close() {
    close(handle).await()
  }
}

private fun supported(): Boolean = js("globalThis.isSecureContext && 'serial' in navigator")

private fun request(vendorId: Int, productId: Int): Promise<JsAny> =
  js(
    """navigator.serial.requestPort(vendorId < 0 ? {} : {filters: [productId < 0
      ? {usbVendorId: vendorId} : {usbVendorId: vendorId, usbProductId: productId}]})"""
  )

private fun grantedPortsArray(): Promise<JsAny> =
  js("navigator.serial.getPorts ? navigator.serial.getPorts() : Promise.resolve([])")

private fun portCount(ports: JsAny): Int = js("ports.length")

private fun portAt(ports: JsAny, index: Int): JsAny = js("ports[index]")

private fun usbVendorId(port: JsAny): Int = js("port.getInfo?.().usbVendorId ?? -1")

private fun usbProductId(port: JsAny): Int = js("port.getInfo?.().usbProductId ?? -1")

private fun session(port: JsAny): JsAny =
  js("({port, reader: null, writer: null, opening: null, closing: null, closed: false})")

private fun open(handle: JsAny): Promise<JsAny?> =
  js(
    """(() => {
    handle.opening = handle.port.open({baudRate: 115200}).then(async () => {
      if (handle.closed) {
        await handle.port.close();
        throw new Error('Connection was closed');
      }
      if (!handle.port.readable || !handle.port.writable) throw new Error('Radio has no serial streams');
      handle.reader = handle.port.readable.getReader();
      handle.writer = handle.port.writable.getWriter();
      return null;
    });
    return handle.opening;
  })()"""
  )

private fun read(handle: JsAny): Promise<JsAny?> =
  js("handle.reader.read().then(result => result.done ? null : result.value)")

private fun write(handle: JsAny, bytes: JsAny): Promise<JsAny?> =
  js("handle.writer.write(bytes).then(() => null)")

private fun close(handle: JsAny): Promise<JsAny?> =
  js(
    """(() => {
    handle.closed = true;
    if (handle.closing) return handle.closing;
    handle.closing = (async () => {
      // The opening operation checks closed before acquiring stream locks.
      if (handle.opening) { try { await handle.opening; } catch (_) {} }
      if (handle.reader) {
        try { await handle.reader.cancel(); } catch (_) {}
        try { handle.reader.releaseLock(); } catch (_) {}
        handle.reader = null;
      }
      if (handle.writer) {
        try { await handle.writer.abort(); } catch (_) {}
        try { handle.writer.releaseLock(); } catch (_) {}
        handle.writer = null;
      }
      try { await handle.port.close(); } catch (_) {}
      return null;
    })();
    return handle.closing;
  })()"""
  )

private fun array(size: Int): JsAny = js("new Uint8Array(size)")

private fun length(bytes: JsAny): Int = js("bytes.length")

private fun byteAt(bytes: JsAny, index: Int): Int = js("bytes[index]")

private fun setByte(bytes: JsAny, index: Int, value: Int): Unit = js("{ bytes[index] = value; }")
