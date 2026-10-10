@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package ee.schimke.meshcore.transport.webserial

import kotlin.js.JsAny
import kotlin.js.Promise
import kotlinx.coroutines.await

object WebSerial {
  val isSupported: Boolean
    get() = supported()

  // Call synchronously from the click handler, before any coroutine suspension.
  fun requestPort(): SerialPortRequest = SerialPortRequest(request())
}

class SerialPortRequest internal constructor(private val promise: Promise<JsAny>) {
  suspend fun awaitConnection(): SerialConnection =
    BrowserSerialConnection(session(promise.await()))
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

private fun request(): Promise<JsAny> = js("navigator.serial.requestPort()")

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
