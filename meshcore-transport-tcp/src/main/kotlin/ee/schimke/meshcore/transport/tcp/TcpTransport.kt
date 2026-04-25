package ee.schimke.meshcore.transport.tcp

import ee.schimke.meshcore.core.protocol.StreamFrameCodec
import ee.schimke.meshcore.core.transport.Transport
import ee.schimke.meshcore.core.transport.TransportState
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.bytestring.ByteString

/**
 * TCP transport built on ktor-network. Frames are wrapped with the 0x3C/0x3E length-prefixed codec
 * shared with the USB serial transport.
 */
class TcpTransport(private val host: String, private val port: Int) : Transport {

  private val _state = MutableStateFlow<TransportState>(TransportState.Disconnected)
  override val state: StateFlow<TransportState> = _state.asStateFlow()

  private val _incoming = MutableSharedFlow<ByteString>(extraBufferCapacity = 64)
  override val incoming: SharedFlow<ByteString> = _incoming.asSharedFlow()

  private val decoder = StreamFrameCodec.Decoder()
  private val writeMutex = Mutex()
  private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private var selector: SelectorManager? = null
  private var socket: Socket? = null
  private var readChannel: ByteReadChannel? = null
  private var writeChannel: ByteWriteChannel? = null
  private var readerJob: Job? = null

  override suspend fun connect() {
    if (_state.value is TransportState.Connected) return
    _state.value = TransportState.Connecting
    try {
      val sel = SelectorManager(Dispatchers.Default)
      val s = aSocket(sel).tcp().connect(host, port) { noDelay = true }
      selector = sel
      socket = s
      readChannel = s.openReadChannel()
      writeChannel = s.openWriteChannel(autoFlush = true)
      decoder.reset()
      readerJob = ioScope.launch {
        val rc = readChannel ?: return@launch
        val buf = ByteArray(1024)
        try {
          while (isActive && !rc.isClosedForRead) {
            val n = rc.readAvailable(buf, 0, buf.size)
            if (n <= 0) break
            val chunk = buf.copyOf(n)
            for (pkt in decoder.ingest(chunk)) {
              if (pkt.isRx) _incoming.emit(pkt.payload)
            }
          }
        } catch (t: Throwable) {
          _state.value = TransportState.Error(t)
        } finally {
          if (_state.value !is TransportState.Error) {
            _state.value = TransportState.Disconnected
          }
        }
      }
      _state.value = TransportState.Connected
    } catch (t: Throwable) {
      closeQuietly()
      _state.value = TransportState.Error(t)
      throw t
    }
  }

  override suspend fun send(frame: ByteString) {
    val wc = writeChannel ?: error("TCP transport not connected")
    val packet = StreamFrameCodec.encodeTx(frame).toByteArray()
    writeMutex.withLock { wc.writeFully(packet) }
  }

  override suspend fun close() {
    closeQuietly()
    _state.value = TransportState.Disconnected
  }

  private fun closeQuietly() {
    try {
      socket?.close()
    } catch (_: Throwable) {}
    try {
      selector?.close()
    } catch (_: Throwable) {}
    socket = null
    selector = null
    readChannel = null
    writeChannel = null
    readerJob?.cancel()
    readerJob = null
  }
}
