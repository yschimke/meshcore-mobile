package ee.schimke.meshcore.transport.webserial

import ee.schimke.meshcore.core.protocol.StreamFrameCodec
import ee.schimke.meshcore.core.transport.Transport
import ee.schimke.meshcore.core.transport.TransportState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.bytestring.ByteString

/** Browser stream operations. Close must be idempotent and unblock pending reads/writes. */
interface SerialConnection {
  suspend fun open()

  suspend fun read(): ByteArray?

  suspend fun write(bytes: ByteArray)

  suspend fun close()
}

/** The same USB CDC framing as the native companion transport. */
class WebSerialTransport(
  private val port: SerialConnection,
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : Transport {
  private val mutableState = MutableStateFlow<TransportState>(TransportState.Disconnected)
  override val state = mutableState.asStateFlow()
  private val frames = MutableSharedFlow<ByteString>(extraBufferCapacity = 64)
  override val incoming = frames.asSharedFlow()
  private val decoder = StreamFrameCodec.Decoder()
  private val writes = Mutex()
  private var reader: Job? = null
  private var closed = false

  override suspend fun connect() {
    check(!closed) { "Choose the radio again to reconnect" }
    if (state.value == TransportState.Connected) return
    mutableState.value = TransportState.Connecting
    try {
      port.open()
      check(!closed) { "Connection was closed" }
      decoder.reset()
      mutableState.value = TransportState.Connected
      reader = scope.launch {
        try {
          while (isActive) {
            val bytes = port.read() ?: error("The USB radio was disconnected")
            if (bytes.isEmpty()) {
              yield()
              continue
            }
            for (packet in decoder.ingest(bytes)) {
              if (packet.isRx) frames.emit(packet.payload)
            }
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          mutableState.value = TransportState.Error(e)
        } finally {
          withContext(NonCancellable) { port.close() }
        }
      }
    } catch (e: CancellationException) {
      withContext(NonCancellable) { close() }
      throw e
    } catch (e: Exception) {
      withContext(NonCancellable) { port.close() }
      mutableState.value = TransportState.Error(e)
      throw e
    }
  }

  override suspend fun send(frame: ByteString) {
    writes.withLock {
      check(!closed && state.value == TransportState.Connected) { "Radio is not connected" }
      try {
        port.write(StreamFrameCodec.encodeTx(frame).toByteArray())
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        reader?.cancel()
        withContext(NonCancellable) { port.close() }
        mutableState.value = TransportState.Error(e)
        throw e
      }
    }
  }

  override suspend fun close() {
    closed = true
    reader?.cancel()
    withContext(NonCancellable) {
      port.close()
      reader?.join()
    }
    reader = null
    scope.cancel()
    mutableState.value = TransportState.Disconnected
  }
}
