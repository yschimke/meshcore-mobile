package ee.schimke.meshcore.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import ee.schimke.meshcore.app.ui.theme.MeshcoreTheme
import ee.schimke.meshcore.core.model.BatteryInfo
import ee.schimke.meshcore.core.model.ChannelInfo
import ee.schimke.meshcore.core.model.Contact
import ee.schimke.meshcore.core.model.ContactType
import ee.schimke.meshcore.core.model.PublicKey
import ee.schimke.meshcore.core.model.RadioSettings
import ee.schimke.meshcore.core.model.SelfInfo
import kotlin.time.Instant
import kotlinx.io.bytestring.ByteString

// --- Preview helpers --------------------------------------------------------

private fun previewPubKey(fill: Byte): PublicKey =
    PublicKey.fromBytes(ByteString(*ByteArray(32) { fill }))

private fun previewContact(name: String, pathLen: Int, fill: Byte, type: ContactType = ContactType.CHAT): Contact =
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

private fun previewSelf(name: String = "node-peak") = SelfInfo(
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

// --- Previews ---------------------------------------------------------------

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
            contacts = listOf(
                alice,
                previewContact("bob-repeater", 2, 0x22, ContactType.REPEATER),
                room,
                previewContact("soil-sensor-1", 3, 0x44, ContactType.SENSOR),
            ),
            channels = listOf(
                ChannelInfo(0, "General", ByteString()),
                ChannelInfo(1, "Emergency", ByteString()),
            ),
            contactedKeys = setOf(alice.publicKey.toHex(), room.publicKey.toHex()),
            lastMessage = LastMessageInfo.Dm(
                contactKeyHex = "112233445566778899aabbcc",
                contactName = "alice",
                text = "hey — are you on tonight?",
                snr = 6,
            ),

            onDisconnect = {},
        )
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    device = Devices.PIXEL_7,
    name = "Device — loading",
)
@Composable
fun DeviceBodyLoadingPreview() {
    MeshcoreTheme {
        DeviceBody(
            self = null,
            battery = null,
            radio = null,
            contacts = emptyList(),
            contactsLoading = true,

            onDisconnect = {},
        )
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    device = Devices.PIXEL_7,
    name = "Device — contacts loading",
)
@Composable
fun DeviceBodyNoContactsPreview() {
    MeshcoreTheme {
        DeviceBody(
            self = previewSelf(),
            battery = BatteryInfo(3980, 512, 4096),
            radio = RadioSettings(869_525_000, 125_000, 10, 5),
            contacts = emptyList(),
            contactsLoading = true,

            onDisconnect = {},
        )
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    device = Devices.PIXEL_7,
    name = "Device — low battery",
)
@Composable
fun DeviceBodyLowBatteryPreview() {
    MeshcoreTheme {
        DeviceBody(
            self = previewSelf(),
            battery = BatteryInfo(3210, 3800, 4096),
            radio = RadioSettings(869_525_000, 125_000, 10, 5),
            contacts = listOf(
                previewContact("alice", -1, 0x11),
                previewContact("bob-repeater", 2, 0x22, ContactType.REPEATER),
            ),

            onDisconnect = {},
        )
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    device = Devices.PIXEL_7,
    name = "Device — many contacts (scrolls)",
)
@Composable
fun DeviceBodyManyContactsPreview() {
    val types = listOf(
        ContactType.CHAT,
        ContactType.REPEATER,
        ContactType.ROOM,
        ContactType.SENSOR,
    )
    val contacts = (0 until 12).map { i ->
        previewContact(
            name = listOf(
                "alice", "bob-repeater", "charlie", "dana",
                "eve-hq", "frank", "common-room", "garden-sensor",
                "hiker-ian", "julia-summit", "kappa-repeater", "lighthouse",
            )[i],
            pathLen = i % 4,
            fill = (0x11 + i).toByte(),
            type = types[i % types.size],
        )
    }
    MeshcoreTheme {
        DeviceBody(
            self = previewSelf("base-station"),
            battery = BatteryInfo(4050, 900, 4096),
            radio = RadioSettings(869_525_000, 125_000, 10, 5),
            contacts = contacts,
            lastMessage = LastMessageInfo.Channel(
                channelIndex = 0,
                channelName = "test",
                sender = "eve-hq",
                text = "weather check in 5",
                snr = 8,
            ),

            onDisconnect = {},
        )
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    device = Devices.PIXEL_7,
    name = "Device — status connecting",
)
@Composable
fun DeviceStatusConnectingPreview() {
    MeshcoreTheme {
        DeviceStatusView(
            title = "Connecting",
            status = DeviceConnectStatus.Connecting(
                startedAtMs = System.currentTimeMillis() - 7_000L,
                timeoutMs = 20_000L,
            ),
            onCancel = {},
        )
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    device = Devices.PIXEL_7,
    name = "Device — status failed",
)
@Composable
fun DeviceStatusFailedPreview() {
    MeshcoreTheme {
        val cause = IllegalStateException(
            "BLE GATT timed out after 20s (device XX:XX:XX:XX:5F:78)",
            java.util.concurrent.TimeoutException("no LoginSuccess within 5000ms"),
        )
        DeviceStatusView(
            title = "Connection failed",
            status = DeviceConnectStatus.Failed(cause),
            onCancel = {},
        )
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    device = Devices.PIXEL_7,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    name = "Device — dark",
)
@Composable
fun DeviceBodyDarkPreview() {
    MeshcoreTheme(darkTheme = true) {
        DeviceBody(
            self = previewSelf(),
            battery = BatteryInfo(3980, 512, 4096),
            radio = RadioSettings(869_525_000, 125_000, 10, 5),
            contacts = listOf(
                previewContact("alice", -1, 0x11),
                previewContact("bob-repeater", 2, 0x22, ContactType.REPEATER),
                previewContact("common-room", 0, 0x33, ContactType.ROOM),
            ),
            lastMessage = LastMessageInfo.Dm(
                contactKeyHex = "112233445566778899aabbcc",
                contactName = "alice",
                text = "hey — are you on tonight?",
                snr = 6,
            ),

            onDisconnect = {},
        )
    }
}
