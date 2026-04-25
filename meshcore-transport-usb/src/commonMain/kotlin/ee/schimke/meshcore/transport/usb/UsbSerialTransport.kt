package ee.schimke.meshcore.transport.usb

import dev.mcarr.usb.interfaces.ISerialPortWrapper
import ee.schimke.meshcore.core.protocol.StreamFrameCodec
import ee.schimke.meshcore.core.transport.Transport
import ee.schimke.meshcore.core.transport.TransportState
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.yield
import kotlinx.io.bytestring.ByteString

/**
 * Serial transport built on [ISerialPortWrapper]. The port is opened in [connect] (with [baudRate]
 * applied), and frames are wrapped with the shared 0x3C/0x3E length-prefixed codec.
 *
 * On Android, construct with a `UsfaPortWrapper` from mcarr-usb-android. On JVM, construct with a
 * JvmSerialPort backed by jSerialComm.
 */
class UsbSerialTransport(
  private val port: ISerialPortWrapper,
  private val baudRate: Int = 115200,
  private val readChunkSize: Int = 256,
) : Transport {

  private val _state = MutableStateFlow<TransportState>(TransportState.Disconnected)
  override val state: StateFlow<TransportState> = _state.asStateFlow()

  private val _incoming = MutableSharedFlow<ByteString>(extraBufferCapacity = 64)
  override val incoming: SharedFlow<ByteString> = _incoming.asSharedFlow()

  private val decoder = StreamFrameCodec.Decoder()
  private val writeMutex = Mutex()
  private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var readerJob: Job? = null

  override suspend fun connect() {
    if (_state.value is TransportState.Connected) return
    _state.value = TransportState.Connecting
    try {
      port.open()
      port.setBaudRate(baudRate)
      decoder.reset()
      readerJob = ioScope.launch {
        try {
          while (isActive && port.isOpen()) {
            val chunk =
              try {
                port.read(readChunkSize, 250L)
              } catch (_: CancellationException) {
                yield()
                continue
              }
            if (chunk.isEmpty()) {
              yield()
              continue
            }
            for (pkt in decoder.ingest(chunk)) {
              if (pkt.isRx) _incoming.emit(pkt.payload)
            }
          }
        } catch (t: Throwable) {
          _state.value = TransportState.Error(t)
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
    if (!port.isOpen()) error("USB transport not connected")
    val packet = StreamFrameCodec.encodeTx(frame)
    writeMutex.withLock { port.write(packet.toByteArray(), 1000L) }
  }

  override suspend fun close() {
    closeQuietly()
    _state.value = TransportState.Disconnected
  }

  private fun closeQuietly() {
    readerJob?.cancel()
    readerJob = null
    try {
      if (port.isOpen()) port.close()
    } catch (_: Throwable) {}
  }
}
