package ee.schimke.meshcore.core.client

import ee.schimke.meshcore.core.model.BatteryInfo
import ee.schimke.meshcore.core.model.RadioSettings
import ee.schimke.meshcore.core.model.SelfInfo
import ee.schimke.meshcore.core.protocol.CommandCode
import ee.schimke.meshcore.core.protocol.ResponseCode
import ee.schimke.meshcore.core.test.FakeTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.bytestring.ByteString
import kotlinx.io.readByteString
import kotlinx.io.writeIntLe

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MeshCoreClientTest {

  /**
   * Build a minimal SelfInfo response frame:
   * [ResponseCode.SelfInfo][advType:u8][txPwr:i8][maxPwr:i8][pubkey:32][lat:i32le][lon:i32le]
   * [multiAcks:u8][advPolicy:u8][telemetry:u8][manualAdd:u8][freq:i32le][bw:i32le][sf:u8][cr:u8][name\0]
   */
  private fun selfInfoFrame(name: String = "test-node"): ByteString {
    val buf = Buffer()
    buf.writeByte(ResponseCode.SelfInfo.raw) // code
    buf.writeByte(1) // advType
    buf.writeByte(14) // txPower
    buf.writeByte(22) // maxPower
    buf.write(ByteArray(32) { 0xAB.toByte() }) // public key
    buf.writeIntLe(53_000_000) // lat * 1_000_000
    buf.writeIntLe(-1_500_000) // lon * 1_000_000
    buf.writeByte(0) // multiAcks
    buf.writeByte(0) // advertLocationPolicy
    buf.writeByte(0) // telemetryFlags
    buf.writeByte(0) // manualAddContacts
    buf.writeIntLe(869_525_000) // freq Hz
    buf.writeIntLe(125_000) // bandwidth Hz
    buf.writeByte(10) // spreading factor
    buf.writeByte(5) // coding rate
    buf.write(name.encodeToByteArray())
    buf.writeByte(0) // null terminator
    return buf.readByteString()
  }

  @Test
  fun start_sendsAppStartFrame() =
    runTest(UnconfinedTestDispatcher()) {
      val transport = FakeTransport()
      val client =
        MeshCoreClient(transport, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

      // start() sends AppStart then waits for SelfInfo.
      // With UnconfinedTestDispatcher the pump runs eagerly,
      // so injecting a frame immediately satisfies the wait.
      val job = launch { client.start(timeoutMs = 2_000) }
      transport.receive(selfInfoFrame())
      job.join()

      // First sent frame should start with CommandCode.AppStart
      assertTrue(transport.sentFrames.isNotEmpty(), "expected at least one sent frame")
      assertEquals(CommandCode.AppStart.raw, transport.sentFrames[0][0])

      client.stop()
    }

  @Test
  fun start_timesOutWithoutSelfInfo() = runTest {
    val transport = FakeTransport()
    val client = MeshCoreClient(transport, backgroundScope)

    // Don't inject any response — start() should throw
    val ex = assertFailsWith<IllegalStateException> { client.start(timeoutMs = 500) }
    assertTrue(ex.message!!.contains("No response"), "expected timeout message, got: ${ex.message}")

    client.stop()
  }

  @Test
  fun start_parsesSelfInfoResponse() =
    runTest(UnconfinedTestDispatcher()) {
      val transport = FakeTransport()
      val client =
        MeshCoreClient(transport, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

      assertNull(client.selfInfo.value, "selfInfo should be null before start")

      val job = launch { client.start(timeoutMs = 2_000) }
      transport.receive(selfInfoFrame("my-radio"))
      job.join()

      val info = client.selfInfo.value
      assertNotNull(info, "selfInfo should be populated after start")
      assertEquals("my-radio", info.name)
      assertEquals(14, info.txPowerDbm)
      assertEquals(869_525_000, info.radio.frequencyHz)

      client.stop()
    }

  @Test
  fun seedFromCache_populatesEmptyStateFlows() = runTest {
    val transport = FakeTransport()
    val client = MeshCoreClient(transport, backgroundScope)

    val cachedSelf =
      SelfInfo(
        advertType = 1,
        txPowerDbm = 10,
        maxPowerDbm = 20,
        publicKey =
          ee.schimke.meshcore.core.model.PublicKey.fromBytes(ByteString(*ByteArray(32) { 0x01 })),
        latitude = 51.0,
        longitude = -0.1,
        multiAcks = 0,
        advertLocationPolicy = 0,
        telemetryFlags = 0,
        manualAddContacts = 0,
        radio = RadioSettings(868_000_000, 125_000, 12, 5),
        name = "cached-node",
      )
    val cachedBattery = BatteryInfo(3800, 100, 4096)

    client.seedFromCache(selfInfo = cachedSelf, battery = cachedBattery)

    assertEquals("cached-node", client.selfInfo.value?.name)
    assertEquals(3800, client.battery.value?.millivolts)
    assertTrue(client.contacts.value.isEmpty(), "contacts should remain empty")
  }

  @Test
  fun seedFromCache_doesNotOverrideLiveData() =
    runTest(UnconfinedTestDispatcher()) {
      val transport = FakeTransport()
      val client =
        MeshCoreClient(transport, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

      // Start and receive live SelfInfo
      val job = launch { client.start(timeoutMs = 2_000) }
      transport.receive(selfInfoFrame("live-node"))
      job.join()

      // Now seed with different cached data — should NOT override
      val cached =
        SelfInfo(
          advertType = 1,
          txPowerDbm = 0,
          maxPowerDbm = 0,
          publicKey =
            ee.schimke.meshcore.core.model.PublicKey.fromBytes(ByteString(*ByteArray(32) { 0x00 })),
          latitude = 0.0,
          longitude = 0.0,
          multiAcks = 0,
          advertLocationPolicy = 0,
          telemetryFlags = 0,
          manualAddContacts = 0,
          radio = RadioSettings(0, 0, 0, 0),
          name = "stale-cached",
        )
      client.seedFromCache(selfInfo = cached)

      assertEquals("live-node", client.selfInfo.value?.name, "live data should not be overridden")

      client.stop()
    }
}
