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

  override suspend fun connect() {
    _state.value = TransportState.Connected
  }

  override suspend fun send(frame: ByteString) {
    sentFrames += frame
  }

  override suspend fun close() {
    _state.value = TransportState.Disconnected
  }

  /** Simulate a frame arriving from the device side. */
  suspend fun receive(frame: ByteString) {
    _incoming.emit(frame)
  }
}
