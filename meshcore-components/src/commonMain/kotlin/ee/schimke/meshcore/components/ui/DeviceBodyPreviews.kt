package ee.schimke.meshcore.components.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import ee.schimke.meshcore.components.ui.theme.MeshcoreTheme
import ee.schimke.meshcore.core.model.BatteryInfo
import ee.schimke.meshcore.core.model.ChannelInfo
import ee.schimke.meshcore.core.model.Contact
import ee.schimke.meshcore.core.model.ContactType
import ee.schimke.meshcore.core.model.PublicKey
import ee.schimke.meshcore.core.model.RadioSettings
import ee.schimke.meshcore.core.model.SelfInfo
import kotlin.time.Instant
import kotlinx.io.bytestring.ByteString

// Design-parity preview subjects for the Device screen, on the CMP desktop
// render path. The other Device previews (status views, extra DeviceBody states)
// stay in :app for the Android preview baseline.

private fun previewPubKey(fill: Byte): PublicKey =
  PublicKey.fromBytes(ByteString(*ByteArray(32) { fill }))

private fun previewContact(
  name: String,
  pathLen: Int,
  fill: Byte,
  type: ContactType = ContactType.CHAT,
): Contact =
  Contact(
    publicKey = previewPubKey(fill),
    type = type,
    flags = 0,
    pathLength = pathLen,
    path = ByteString(),
    name = name,
    advertTimestamp = Instant.fromEpochSeconds(1_700_000_000),
    latitude = 0.0,
    longitude = 0.0,
    lastModified = Instant.fromEpochSeconds(1_700_000_000),
  )

private fun previewSelf(name: String = "node-peak") =
  SelfInfo(
    advertType = 1,
    txPowerDbm = 14,
    maxPowerDbm = 22,
    publicKey = previewPubKey(0xAB.toByte()),
    latitude = 53.0,
    longitude = -1.5,
    multiAcks = 0,
    advertLocationPolicy = 0,
    telemetryFlags = 0,
    manualAddContacts = 0,
    radio = RadioSettings(869_525_000, 125_000, 10, 5),
    name = name,
  )

/**
 * Design-parity subject for the Device screen (light).
 *
 * Checked against its design reference `design/DeviceScreen.light.html` via design-parity, rendered
 * on the CMP desktop target. The rendered reference | candidate | diff is published to the
 * [`design-parity/main`](https://github.com/yschimke/meshcore-mobile/tree/design-parity/main)
 * branch (regenerated on every push to main); see `docs/design-parity.md`.
 */
@Preview(
  showBackground = true,
  showSystemUi = true,
  device = Devices.PIXEL_7,
  name = "Device — populated",
)
@Composable
fun DeviceBodyPreview() {
  val alice = previewContact("alice", -1, 0x11)
  val room = previewContact("common-room", 0, 0x33, ContactType.ROOM)
  MeshcoreTheme {
    DeviceBody(
      self = previewSelf(),
      battery = BatteryInfo(3980, 512, 4096),
      radio = RadioSettings(869_525_000, 125_000, 10, 5),
      contacts =
        listOf(
          alice,
          previewContact("bob-repeater", 2, 0x22, ContactType.REPEATER),
          room,
          previewContact("soil-sensor-1", 3, 0x44, ContactType.SENSOR),
        ),
      channels =
        listOf(
          ChannelInfo(0, "General", ByteString()),
          ChannelInfo(1, "Emergency", ByteString()),
          ChannelInfo(2, COMMANDS_CHANNEL_NAME, ByteString()),
        ),
      contactedKeys = setOf(alice.publicKey.toHex(), room.publicKey.toHex()),
      contactedChannelIndices = setOf(0),
      lastMessage =
        LastMessageInfo.Dm(
          contactKeyHex = "112233445566778899aabbcc",
          contactName = "alice",
          text = "hey — are you on tonight?",
          snr = 6,
        ),
      onDisconnect = {},
    )
  }
}

/**
 * Design-parity subject for the Device screen (dark).
 *
 * `uiMode = NIGHT_YES` makes the candidate carry `theme: dark`, so it pairs with the dark reference
 * `design/DeviceScreen.dark.html`. See `docs/design-parity.md`.
 */
@Preview(
  showBackground = true,
  showSystemUi = true,
  device = Devices.PIXEL_7,
  uiMode = 0x20, // Configuration.UI_MODE_NIGHT_YES
  name = "Device — dark",
)
@Composable
fun DeviceBodyDarkPreview() {
  MeshcoreTheme(darkTheme = true) {
    DeviceBody(
      self = previewSelf(),
      battery = BatteryInfo(3980, 512, 4096),
      radio = RadioSettings(869_525_000, 125_000, 10, 5),
      contacts =
        listOf(
          previewContact("alice", -1, 0x11),
          previewContact("bob-repeater", 2, 0x22, ContactType.REPEATER),
          previewContact("common-room", 0, 0x33, ContactType.ROOM),
        ),
      lastMessage =
        LastMessageInfo.Dm(
          contactKeyHex = "112233445566778899aabbcc",
          contactName = "alice",
          text = "hey — are you on tonight?",
          snr = 6,
        ),
      onDisconnect = {},
    )
  }
}

private const val CACHED_WARNING = "Cached data — connect to refresh"

@Composable
private fun cachedDevicePreview(dark: Boolean) {
  val alice = previewContact("alice", -1, 0x11)
  MeshcoreTheme(darkTheme = dark) {
    DeviceBody(
      self = previewSelf(),
      battery = BatteryInfo(3980, 512, 4096),
      radio = RadioSettings(869_525_000, 125_000, 10, 5),
      contacts =
        listOf(
          alice,
          previewContact("bob-repeater", 2, 0x22, ContactType.REPEATER),
          previewContact("common-room", 0, 0x33, ContactType.ROOM),
        ),
      channels = listOf(ChannelInfo(0, "General", ByteString())),
      contactedKeys = setOf(alice.publicKey.toHex()),
      contactedChannelIndices = setOf(0),
      warnings = listOf(CACHED_WARNING),
      onDisconnect = {},
    )
  }
}

/**
 * Design-parity subject for the **cached** (offline) Device screen — the `CachedDeviceScreen`
 * state: `DeviceBody` carrying the "Cached data" warning banner. Reference
 * `design/CachedDevice.light.html`. See `docs/design-parity.md`.
 */
@Preview(
  showBackground = true,
  showSystemUi = true,
  device = Devices.PIXEL_7,
  name = "Cached device",
)
@Composable
fun CachedDeviceBodyPreview() = cachedDevicePreview(dark = false)

/** Cached Device screen, dark. Reference `design/CachedDevice.dark.html`. */
@Preview(
  showBackground = true,
  showSystemUi = true,
  device = Devices.PIXEL_7,
  uiMode = 0x20,
  name = "Cached device — dark",
)
@Composable
fun CachedDeviceBodyDarkPreview() = cachedDevicePreview(dark = true)
