package ee.schimke.meshcore.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import ee.schimke.meshcore.core.model.Contact
import ee.schimke.meshcore.core.model.ContactType
import ee.schimke.meshcore.core.model.PublicKey
import ee.schimke.meshcore.core.model.RadioSettings
import ee.schimke.meshcore.core.model.ReceivedDirectMessage
import ee.schimke.meshcore.core.model.SelfInfo
import ee.schimke.meshcore.core.protocol.TextType
import ee.schimke.meshcore.data.MeshcoreDatabase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString

/**
 * Repository-level tests against an in-memory Room database (JVM, no Android). These document the
 * device-merge / public-key deduplication flow that [MeshcoreRepository] performs when the same
 * physical node is reached over two different transports (e.g. BLE then USB).
 */
class MeshcoreRepositoryTest {

  private lateinit var db: MeshcoreDatabase
  private lateinit var repo: MeshcoreRepository

  @BeforeTest
  fun setUp() {
    db =
      Room.inMemoryDatabaseBuilder<MeshcoreDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
    repo = MeshcoreRepository(db)
  }

  @AfterTest
  fun tearDown() {
    db.close()
  }

  private fun pubKey(fill: Int): PublicKey =
    PublicKey.fromBytes(ByteString(*ByteArray(32) { fill.toByte() }))

  private fun selfInfo(name: String, key: PublicKey): SelfInfo =
    SelfInfo(
      advertType = 1,
      txPowerDbm = 14,
      maxPowerDbm = 22,
      publicKey = key,
      latitude = 53.0,
      longitude = -1.5,
      multiAcks = 0,
      advertLocationPolicy = 0,
      telemetryFlags = 0,
      manualAddContacts = 0,
      radio = RadioSettings(869_525_000, 125_000, 10, 5),
      name = name,
    )

  private fun contact(key: PublicKey, name: String): Contact =
    Contact(
      publicKey = key,
      type = ContactType.fromRaw(1),
      flags = 0,
      pathLength = 0,
      path = ByteString(),
      name = name,
      advertTimestamp = Instant.fromEpochSeconds(0),
      latitude = 0.0,
      longitude = 0.0,
      lastModified = Instant.fromEpochSeconds(0),
    )

  @Test
  fun upsertDevice_isObservable() = runTest {
    repo.upsertDevice("ble:aa", "Node A", SavedTransport.Ble("aa", "adv-a"))

    val devices = repo.observeDevices().first()
    assertEquals(1, devices.size)
    assertEquals("ble:aa", devices[0].id)
    assertTrue(devices[0].transport is SavedTransport.Ble)
  }

  @Test
  fun updateSelfInfo_enablesPublicKeyLookupAndUpdatesLabel() = runTest {
    val key = pubKey(0x11)
    repo.upsertDevice("ble:aa", "placeholder", SavedTransport.Ble("aa", null))

    repo.updateSelfInfo("ble:aa", selfInfo("Real Name", key), fetchedAtMs = 1_000)

    assertEquals("ble:aa", repo.findDeviceIdByPublicKey(key))
    // updateSelfInfo also rewrites the device label with the node's real name.
    assertEquals("Real Name", repo.getDevice("ble:aa")?.label)
  }

  @Test
  fun findDeviceIdByPublicKey_returnsNullForUnknownKey() = runTest {
    repo.upsertDevice("ble:aa", "Node A", SavedTransport.Ble("aa", null))
    repo.updateSelfInfo("ble:aa", selfInfo("A", pubKey(0x11)), fetchedAtMs = 0)

    assertNull(repo.findDeviceIdByPublicKey(pubKey(0x22)))
  }

  @Test
  fun mergeDevice_dedupesByPublicKeyMovingTransportAndPreservingCanonicalData() = runTest {
    val key = pubKey(0x33)

    // Canonical entry first seen over BLE, with cached state (public key) and a contact.
    repo.upsertDevice("ble:aa", "Node", SavedTransport.Ble("aa", "adv"))
    repo.updateSelfInfo("ble:aa", selfInfo("Node", key), fetchedAtMs = 0)
    repo.replaceContacts("ble:aa", listOf(contact(pubKey(0x99), "friend")), fetchedAtMs = 0)

    // Same physical node reached over USB lands as a separate entry...
    repo.upsertDevice("usb:bb", "Node", SavedTransport.Usb("Cls", 1, 2))
    // ...but the public-key lookup finds the existing canonical entry.
    val canonical = repo.findDeviceIdByPublicKey(key)
    assertEquals("ble:aa", canonical)

    repo.mergeDevice(
      fromId = "usb:bb",
      intoId = "ble:aa",
      transport = SavedTransport.Usb("Cls", 1, 2),
    )

    // Source entry is gone, canonical survives and now carries the USB transport.
    assertNull(repo.getDevice("usb:bb"))
    val merged = repo.getDevice("ble:aa")
    assertTrue(merged?.transport is SavedTransport.Usb, "transport should be moved to USB")
    // Canonical contacts (and the public-key state) are preserved across the merge.
    assertEquals(1, repo.getContacts("ble:aa").size)
    assertEquals("ble:aa", repo.findDeviceIdByPublicKey(key))
  }

  @Test
  fun mergeDevice_intoSameIdIsANoOpDelete() = runTest {
    repo.upsertDevice("ble:aa", "Node", SavedTransport.Ble("aa", "adv"))

    // Merging an entry into itself must not delete it.
    repo.mergeDevice(
      fromId = "ble:aa",
      intoId = "ble:aa",
      transport = SavedTransport.Ble("aa", "adv"),
    )

    assertEquals(1, repo.observeDevices().first().size)
  }

  @Test
  fun forgetDevice_cascadesToContacts() = runTest {
    repo.upsertDevice("ble:aa", "Node", SavedTransport.Ble("aa", null))
    repo.replaceContacts("ble:aa", listOf(contact(pubKey(0x99), "friend")), fetchedAtMs = 0)
    assertEquals(1, repo.getContacts("ble:aa").size)

    repo.forgetDevice("ble:aa")

    assertNull(repo.getDevice("ble:aa"))
    assertTrue(
      repo.getContacts("ble:aa").isEmpty(),
      "contacts should cascade-delete with the device",
    )
  }

  @Test
  fun insertReceivedDm_deduplicatesByTimestampAndText() = runTest {
    repo.upsertDevice("ble:aa", "Node", SavedTransport.Ble("aa", null))
    val contactHex = pubKey(0x99).toHex()
    val msg =
      ReceivedDirectMessage(
        snr = 10,
        senderPrefix = pubKey(0x99),
        pathLength = 0,
        timestamp = Instant.fromEpochSeconds(1_000),
        textType = TextType.Plain,
        text = "hello",
      )

    repo.insertReceivedDm("ble:aa", msg, contactHex)
    repo.insertReceivedDm("ble:aa", msg, contactHex) // duplicate

    assertEquals(1, repo.getRecentMessages("ble:aa").size, "duplicate DM should be ignored")
  }
}
