package ee.schimke.meshcore.core.test

import ee.schimke.meshcore.core.transport.Transport
import ee.schimke.meshcore.core.transport.TransportState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.io.bytestring.ByteString

/**
 * In-memory [Transport] for tests. Records outbound frames in [sentFrames] and lets the test inject
 * inbound frames via [receive].
 */
class FakeTransport : Transport {
  private val _state = MutableStateFlow<TransportState>(TransportState.Disconnected)
  override val state: StateFlow<TransportState> = _state.asStateFlow()

  private val _incoming = MutableSharedFlow<ByteString>(extraBufferCapacity = 64)
  override val incoming: SharedFlow<ByteString> = _incoming.asSharedFlow()

  val sentFrames = mutableListOf<ByteString>()

  /**
   * When non-null, [connect] throws this instead of transitioning to [TransportState.Connected].
   */
  var connectError: Throwable? = null

  /** Set true once [close] has been invoked, so tests can assert teardown happened. */
  var closed: Boolean = false
    private set

  /**
   * Optional hook invoked for every frame passed to [send] (after it is recorded in [sentFrames]).
   * Lets a test auto-respond to a request frame, e.g. replying to `AppStart` with a `SelfInfo`
   * frame so [ee.schimke.meshcore.core.manager.MeshCoreManager.connect] can reach `Connected`.
   */
  var onSend: (suspend (ByteString) -> Unit)? = null

  override suspend fun connect() {
    connectError?.let { throw it }
    _state.value = TransportState.Connected
  }

  override suspend fun send(frame: ByteString) {
    sentFrames += frame
    onSend?.invoke(frame)
  }

  override suspend fun close() {
    closed = true
    _state.value = TransportState.Disconnected
  }

  /** Simulate a frame arriving from the device side. */
  suspend fun receive(frame: ByteString) {
    _incoming.emit(frame)
  }

  /** Push an arbitrary transport state (e.g. [TransportState.Error]) for state-machine tests. */
  fun emitState(state: TransportState) {
    _state.value = state
  }
}
