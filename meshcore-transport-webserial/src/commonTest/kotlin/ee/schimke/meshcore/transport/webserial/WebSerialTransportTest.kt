package ee.schimke.meshcore.transport.webserial

import ee.schimke.meshcore.core.transport.TransportState
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import kotlinx.io.bytestring.ByteString

@OptIn(ExperimentalCoroutinesApi::class)
class WebSerialTransportTest {
  private class Port : SerialConnection {
    val chunks = Channel<ByteArray>(Channel.UNLIMITED)
    val writes = mutableListOf<ByteArray>()
    var opened = false
    var closed = false
    var failWrite = false

    override suspend fun open() {
      opened = true
    }

    override suspend fun read(): ByteArray? = chunks.receiveCatching().getOrNull()

    override suspend fun write(bytes: ByteArray) {
      check(!failWrite) { "Serial write failed" }
      writes += bytes
    }

    override suspend fun close() {
      closed = true
      chunks.close()
    }
  }

  @Test
  fun fragmentedFramesAndTxEchoAreDecodedLikeNativeUsb() = runTest {
    val port = Port()
    val transport = WebSerialTransport(port, backgroundScope)
    val received = mutableListOf<ByteString>()
    val collector =
      backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
        transport.incoming.take(2).toList(received)
      }
    transport.connect()
    // Stray bytes, a TX echo, a fragmented RX header/body and a second RX frame.
    port.chunks.send(byteArrayOf(0, 0x3c, 1, 0, 99, 0x3e, 2))
    port.chunks.send(byteArrayOf(0, 10))
    port.chunks.send(byteArrayOf(11, 0x3e, 1, 0, 12))
    collector.join()
    assertEquals(listOf(ByteString(10, 11), ByteString(12)), received)
    transport.close()
    assertTrue(port.closed)
  }

  @Test
  fun writesHaveUsbHeaderAndRejectSendsAfterClosing() = runTest {
    val port = Port()
    val transport = WebSerialTransport(port, backgroundScope)
    transport.connect()
    transport.send(ByteString(1, 2, 3))
    assertContentEquals(byteArrayOf(0x3c, 3, 0, 1, 2, 3), port.writes.single())
    transport.close()
    assertFailsWith<IllegalStateException> { transport.send(ByteString(4)) }
    assertEquals(TransportState.Disconnected, transport.state.value)
  }

  @Test
  fun closingUnblocksThePendingReadAndIsIdempotent() = runTest {
    val port = Port()
    val transport = WebSerialTransport(port, backgroundScope)
    transport.connect()
    runCurrent()
    withTimeout(100) { transport.close() }
    transport.close()
    assertTrue(port.closed)
    assertEquals(TransportState.Disconnected, transport.state.value)
  }

  @Test
  fun unplugReportsAnErrorAndClosesThePort() = runTest {
    val port = Port()
    val transport = WebSerialTransport(port, backgroundScope)
    transport.connect()
    port.chunks.close()
    runCurrent()
    assertIs<TransportState.Error>(transport.state.value)
    assertTrue(port.closed)
    transport.close()
  }

  @Test
  fun writeFailureClosesThePortAndReportsAnError() = runTest {
    val port = Port()
    val transport = WebSerialTransport(port, backgroundScope)
    transport.connect()
    port.failWrite = true
    assertFailsWith<IllegalStateException> { transport.send(ByteString(1)) }
    assertIs<TransportState.Error>(transport.state.value)
    assertTrue(port.closed)
    transport.close()
  }

  @Test
  fun cancellingOpenClosesThePort() = runTest {
    var closed = false
    val port =
      object : SerialConnection {
        override suspend fun open() {
          awaitCancellation()
        }

        override suspend fun read(): ByteArray? = null

        override suspend fun write(bytes: ByteArray) {}

        override suspend fun close() {
          closed = true
        }
      }
    val transport = WebSerialTransport(port, backgroundScope)
    val connect = launch { transport.connect() }
    runCurrent()
    connect.cancelAndJoin()
    assertTrue(closed)
    assertEquals(TransportState.Disconnected, transport.state.value)
  }
}
