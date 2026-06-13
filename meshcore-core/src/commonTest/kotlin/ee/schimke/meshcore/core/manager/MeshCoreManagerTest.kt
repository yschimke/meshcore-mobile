package ee.schimke.meshcore.core.manager

import ee.schimke.meshcore.core.protocol.CommandCode
import ee.schimke.meshcore.core.test.FakeTransport
import ee.schimke.meshcore.core.test.TestFrames
import ee.schimke.meshcore.core.transport.TransportState
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * Lifecycle tests for [MeshCoreManager] using the in-memory [FakeTransport] — no BLE/USB hardware.
 * Documents the intended pattern for third-party consumers: drive the manager with any [Transport]
 * implementation and assert on its [MeshCoreManager.state] flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MeshCoreManagerTest {

  /**
   * A transport that completes the handshake by replying to the `AppStart` frame with a `SelfInfo`
   * response, the way a real device does. Without this reply [MeshCoreManager.connect] would block
   * until the client's handshake timeout.
   */
  private fun handshakingTransport(name: String = "node"): FakeTransport {
    val transport = FakeTransport()
    transport.onSend = { frame ->
      if (frame[0] == CommandCode.AppStart.raw) {
        transport.receive(TestFrames.selfInfoFrame(name))
      }
    }
    return transport
  }

  @Test
  fun connect_transitionsToConnectedAndSendsAppStart() =
    runTest(UnconfinedTestDispatcher()) {
      val manager = MeshCoreManager(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
      val transport = handshakingTransport()

      manager.connect(transport)

      val state = manager.state.value
      assertTrue(state is ManagerState.Connected, "expected Connected, got $state")
      assertNotNull(manager.client, "client should be available after connect")
      assertTrue(transport.sentFrames.isNotEmpty(), "expected handshake frames to be sent")
      assertTrue(
        transport.sentFrames[0][0] == CommandCode.AppStart.raw,
        "first sent frame should be AppStart",
      )
    }

  @Test
  fun disconnect_transitionsToIdleAndClosesTransport() =
    runTest(UnconfinedTestDispatcher()) {
      val manager = MeshCoreManager(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
      val transport = handshakingTransport()

      manager.connect(transport)
      manager.disconnect()

      assertTrue(manager.state.value is ManagerState.Idle, "expected Idle after disconnect")
      assertNull(manager.client, "client should be cleared after disconnect")
      assertTrue(transport.closed, "transport should be closed on disconnect")
    }

  @Test
  fun connect_supersedesPreviousConnection() =
    runTest(UnconfinedTestDispatcher()) {
      val manager = MeshCoreManager(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
      val first = handshakingTransport("first")
      val second = handshakingTransport("second")

      manager.connect(first)
      val firstClient = manager.client
      manager.connect(second)

      val state = manager.state.value
      assertTrue(state is ManagerState.Connected, "expected Connected after second connect")
      assertTrue(first.closed, "the superseded transport should be closed")
      assertTrue(manager.client !== firstClient, "a new client should back the second connection")
      assertSame(manager.client, state.client, "state should expose the live client")
    }

  @Test
  fun reconnect_afterDisconnectSucceeds() =
    runTest(UnconfinedTestDispatcher()) {
      val manager = MeshCoreManager(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

      manager.connect(handshakingTransport())
      manager.disconnect()
      assertTrue(manager.state.value is ManagerState.Idle)

      manager.connect(handshakingTransport())
      assertTrue(manager.state.value is ManagerState.Connected, "should reconnect cleanly")
    }

  @Test
  fun connect_failsWhenTransportConnectThrows() =
    runTest(UnconfinedTestDispatcher()) {
      val manager = MeshCoreManager(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
      val boom = IllegalStateException("GATT failed")
      val transport = FakeTransport().apply { connectError = boom }

      val thrown = assertFailsWith<IllegalStateException> { manager.connect(transport) }
      assertSame(boom, thrown, "connect should rethrow the transport failure")

      val state = manager.state.value
      assertTrue(state is ManagerState.Failed, "expected Failed, got $state")
      assertSame(boom, state.cause, "failure should carry the transport cause")
    }

  @Test
  fun transportError_whileConnected_transitionsToFailed() =
    runTest(UnconfinedTestDispatcher()) {
      val manager = MeshCoreManager(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
      val transport = handshakingTransport()

      manager.connect(transport)
      assertTrue(manager.state.value is ManagerState.Connected)

      val cause = RuntimeException("link lost")
      transport.emitState(TransportState.Error(cause))

      val state = manager.state.value
      assertTrue(state is ManagerState.Failed, "expected Failed after transport error, got $state")
      assertSame(cause, state.cause)
    }

  @Test
  fun transportDisconnect_whileConnected_transitionsToIdle() =
    runTest(UnconfinedTestDispatcher()) {
      val manager = MeshCoreManager(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
      val transport = handshakingTransport()

      manager.connect(transport)
      assertTrue(manager.state.value is ManagerState.Connected)

      transport.emitState(TransportState.Disconnected)

      assertTrue(
        manager.state.value is ManagerState.Idle,
        "a dropped transport should return the manager to Idle",
      )
    }
}
