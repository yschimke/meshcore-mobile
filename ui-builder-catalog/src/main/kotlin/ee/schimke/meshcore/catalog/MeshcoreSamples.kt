package ee.schimke.meshcore.catalog

import ee.schimke.meshcore.components.ui.COMMANDS_CHANNEL_NAME
import ee.schimke.meshcore.components.ui.ChatMessage
import ee.schimke.meshcore.components.ui.MessageStatus
import ee.schimke.meshcore.core.model.BatteryInfo
import ee.schimke.meshcore.core.model.ChannelInfo
import ee.schimke.meshcore.core.model.Contact
import ee.schimke.meshcore.core.model.ContactType
import ee.schimke.meshcore.core.model.PublicKey
import ee.schimke.meshcore.core.model.RadioSettings
import ee.schimke.meshcore.core.model.SelfInfo
import kotlin.time.Instant
import kotlinx.io.bytestring.ByteString

/**
 * Insertion defaults: the same sample device, contacts, channels and messages the component
 * library's own previews draw (`DeviceBodyPreviews`, `ChatBodyPreviews`; those fixtures are private
 * to their files), so a freshly placed component looks like the app.
 */
object MeshcoreSamples {
  fun key(fill: Int): PublicKey = PublicKey.fromBytes(ByteString(*ByteArray(32) { fill.toByte() }))

  private val epoch = Instant.fromEpochSeconds(1_700_000_000)

  private fun ago(seconds: Long): Instant = Instant.fromEpochSeconds(1_700_000_000 - seconds)

  val radio = RadioSettings(869_525_000, 125_000, 10, 5)

  val battery = BatteryInfo(millivolts = 3980, storageUsedKb = 512, storageTotalKb = 4096)

  val self =
    SelfInfo(
      advertType = 1,
      txPowerDbm = 14,
      maxPowerDbm = 22,
      publicKey = key(0xAB),
      latitude = 53.0,
      longitude = -1.5,
      multiAcks = 0,
      advertLocationPolicy = 0,
      telemetryFlags = 0,
      manualAddContacts = 0,
      radio = radio,
      name = "node-peak",
    )

  fun contact(name: String, pathLength: Int, fill: Int, type: ContactType = ContactType.CHAT) =
    Contact(
      publicKey = key(fill),
      type = type,
      flags = 0,
      pathLength = pathLength,
      path = ByteString(),
      name = name,
      advertTimestamp = epoch,
      latitude = 0.0,
      longitude = 0.0,
      lastModified = epoch,
    )

  val contacts =
    listOf(
      contact("alice", -1, 0x11),
      contact("bob-repeater", 2, 0x22, ContactType.REPEATER),
      contact("common-room", 0, 0x33, ContactType.ROOM),
      contact("soil-sensor-1", 3, 0x44, ContactType.SENSOR),
    )

  val channels =
    listOf(
      ChannelInfo(index = 0, name = "General"),
      ChannelInfo(index = 1, name = "Emergency"),
      ChannelInfo(index = 2, name = COMMANDS_CHANNEL_NAME),
    )

  /** The direct chat with alice from `ContactChatPreview`. */
  val contactMessages =
    listOf(
      ChatMessage("c1", null, "hey — are you on tonight?", ago(900), snr = 6, isMine = false),
      ChatMessage(
        "c2",
        null,
        "yeah, firing up the node now",
        ago(840),
        isMine = true,
        status = MessageStatus.Confirmed,
      ),
      ChatMessage(
        "c3",
        null,
        "nice — I'll relay through bob-repeater",
        ago(120),
        snr = 4,
        isMine = false,
      ),
      ChatMessage(
        "c4",
        null,
        "sounds good, sending a test ping",
        ago(30),
        isMine = true,
        status = MessageStatus.Sent,
      ),
    )

  /** A received channel post, with its sender and signal, from `ChannelChatPreview`. */
  val channelMessage =
    ChatMessage("g1", "alice", "anyone near the north ridge?", ago(1200), snr = 7, isMine = false)
}
