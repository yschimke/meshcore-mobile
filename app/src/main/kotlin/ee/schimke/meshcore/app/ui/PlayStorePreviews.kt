package ee.schimke.meshcore.app.ui

// =============================================================================
// PLAY STORE LISTING SCREENSHOTS
// -----------------------------------------------------------------------------
// Each `@Preview` in this file is rendered into the Play Console listing under
// `app/src/main/play/listings/en-GB/graphics/`. To refresh:
//
//     ./scripts/refresh-play-screenshots.sh
//
// (which runs `:app:renderPreviews` and copies the resulting PNGs into the
// listing directories).
//
// Device specs are tuned to land at the placeholder dimensions Play uses:
//
//   * Phone (Pixel 2):    1080 x 1920  (411 x 731 dp at 420 dpi, native 9:16)
//   * 7-inch tablet:      1200 x 1920  (600 x 960 dp at 320 dpi)
//   * 10-inch tablet:     1600 x 2560  (800 x 1280 dp at 320 dpi)
//
// Pixel 2 is intentionally chosen over taller modern phones (Pixel 7/8a are
// 9:20) — Play rejects screenshots whose long side is more than twice the
// short side, and 9:16 is the canonical Play phone aspect.
// =============================================================================

import ee.schimke.meshcore.components.ui.DeviceBody
import ee.schimke.meshcore.components.ui.LastMessageInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import ee.schimke.meshcore.app.ui.theme.MeshcoreTheme
import ee.schimke.meshcore.components.ui.BleDeviceList
import ee.schimke.meshcore.components.ui.BleDeviceRow
import ee.schimke.meshcore.components.ui.ScanStatusBar
import ee.schimke.meshcore.core.model.BatteryInfo
import ee.schimke.meshcore.core.model.ChannelInfo
import ee.schimke.meshcore.core.model.Contact
import ee.schimke.meshcore.core.model.ContactType
import ee.schimke.meshcore.core.model.PublicKey
import ee.schimke.meshcore.core.model.RadioSettings
import ee.schimke.meshcore.core.model.SelfInfo
import ee.schimke.meshcore.data.repository.SavedDevice
import ee.schimke.meshcore.data.repository.SavedDeviceWithState
import ee.schimke.meshcore.data.repository.SavedTransport
import kotlin.time.Instant
import kotlinx.io.bytestring.ByteString

private const val TABLET_7_INCH_PORTRAIT =
    "spec:width=600dp,height=960dp,dpi=320"
private const val TABLET_10_INCH_PORTRAIT =
    "spec:width=800dp,height=1280dp,dpi=320"

// --- Fixtures (kept here so listing artwork doesn't depend on test fixtures) -

private fun playStorePubKey(fill: Byte): PublicKey =
    PublicKey.fromBytes(ByteString(*ByteArray(32) { fill }))

private fun playStoreContact(
    name: String,
    pathLen: Int,
    fill: Byte,
    type: ContactType = ContactType.CHAT,
): Contact =
    Contact(
        publicKey = playStorePubKey(fill),
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

private fun playStoreSelf(name: String = "node-peak"): SelfInfo =
    SelfInfo(
        advertType = 1,
        txPowerDbm = 14,
        maxPowerDbm = 22,
        publicKey = playStorePubKey(0xAB.toByte()),
        latitude = 53.0,
        longitude = -1.5,
        multiAcks = 0,
        advertLocationPolicy = 0,
        telemetryFlags = 0,
        manualAddContacts = 0,
        radio = RadioSettings(869_525_000, 125_000, 10, 5),
        name = name,
    )

private fun playStoreContacts(): List<Contact> {
    val alice = playStoreContact("alice", -1, 0x11)
    val bob = playStoreContact("bob-repeater", 2, 0x22, ContactType.REPEATER)
    val room = playStoreContact("common-room", 0, 0x33, ContactType.ROOM)
    val sensor = playStoreContact("soil-sensor-1", 3, 0x44, ContactType.SENSOR)
    val charlie = playStoreContact("charlie", 1, 0x55)
    return listOf(alice, bob, room, sensor, charlie)
}

private fun playStoreChannels(): List<ChannelInfo> =
    listOf(
        ChannelInfo(0, "General", ByteString()),
        ChannelInfo(1, "Emergency", ByteString()),
    )

@Composable
private fun PlayStoreHomeBody(darkTheme: Boolean) {
    val contacts = playStoreContacts()
    val alice = contacts.first()
    MeshcoreTheme(darkTheme = darkTheme) {
        DeviceBody(
            self = playStoreSelf(),
            battery = BatteryInfo(3980, 512, 4096),
            radio = RadioSettings(869_525_000, 125_000, 10, 5),
            contacts = contacts,
            channels = playStoreChannels(),
            // Favour every contact + channel so each section in the screenshot
            // has a populated "Favourited"/"Joined" view — empty rows look
            // weak in the listing.
            contactedKeys = contacts.map { it.publicKey.toHex() }.toSet(),
            contactedChannelIndices = setOf(0, 1),
            lastMessage =
                LastMessageInfo.Dm(
                    contactKeyHex = alice.publicKey.toHex(),
                    contactName = alice.name,
                    text = "hey — are you on tonight?",
                    snr = 6,
                ),
            onDisconnect = {},
        )
    }
}

@Composable
private fun PlayStoreSavedDevicesBody() {
    MeshcoreTheme {
        ScannerBody(
            initialTab = 0,
            savedContent = {
                SavedDevicesPanel(
                    devices =
                        listOf(
                            SavedDeviceWithState(
                                device =
                                    SavedDevice(
                                        id = "ble:C7:8D:8C:45:5F:78",
                                        label = "MeshCore-ABCD",
                                        transport =
                                            SavedTransport.Ble(
                                                "C7:8D:8C:45:5F:78",
                                                "MeshCore-ABCD",
                                            ),
                                        favorite = true,
                                        lastConnectedAtMs = 1_700_100_000_000,
                                    ),
                                batteryMillivolts = 3980,
                                contactsCount = 5,
                            ),
                            SavedDeviceWithState(
                                device =
                                    SavedDevice(
                                        id = "tcp:192.168.1.10:5000",
                                        label = "node-shed",
                                        transport =
                                            SavedTransport.Tcp("192.168.1.10", 5000),
                                        favorite = false,
                                        lastConnectedAtMs = 1_700_050_000_000,
                                    ),
                            ),
                        ),
                    busy = false,
                    onConnect = {},
                    onForget = {},
                    onToggleFavorite = {},
                )
            },
            bleContent = {},
            usbContent = {},
            tcpContent = {},
        )
    }
}

@Composable
private fun PlayStoreBleScanningBody() {
    MeshcoreTheme {
        ScannerBody(
            initialTab = 1,
            savedContent = {},
            bleContent = {
                ScanStatusBar(shown = 4, meshOnly = true, onMeshOnlyChange = {})
                BleDeviceList(
                    rows =
                        listOf(
                            BleDeviceRow("C7:8D:8C:45:5F:78", "MeshCore-ABCD", -52),
                            BleDeviceRow("A1:B2:C3:D4:E5:F6", "MeshCore-1234", -68),
                            BleDeviceRow("DE:AD:BE:EF:00:01", "MeshCore-EE10", -74),
                            BleDeviceRow("12:34:56:78:9A:BC", "MeshCore-7F2D", -83),
                        ),
                    busy = true,
                    onPick = {},
                )
            },
            usbContent = {},
            tcpContent = {},
        )
    }
}

// --- Phone (Pixel 8a) --------------------------------------------------------

@Preview(
    name = "Play Store — phone home (light)",
    showBackground = true,
    showSystemUi = true,
    device = Devices.PIXEL_2,
)
@Composable
fun PlayStorePhoneHomeLight() {
    PlayStoreHomeBody(darkTheme = false)
}

@Preview(
    name = "Play Store — phone home (dark)",
    showBackground = true,
    showSystemUi = true,
    device = Devices.PIXEL_2,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun PlayStorePhoneHomeDark() {
    PlayStoreHomeBody(darkTheme = true)
}

@Preview(
    name = "Play Store — phone scanner (saved)",
    showBackground = true,
    showSystemUi = true,
    device = Devices.PIXEL_2,
)
@Composable
fun PlayStorePhoneScannerSaved() {
    PlayStoreSavedDevicesBody()
}

@Preview(
    name = "Play Store — phone scanner (BLE)",
    showBackground = true,
    showSystemUi = true,
    device = Devices.PIXEL_2,
)
@Composable
fun PlayStorePhoneScannerBle() {
    PlayStoreBleScanningBody()
}

// --- 7-inch tablet -----------------------------------------------------------

@Preview(
    name = "Play Store — 7-inch tablet home",
    showBackground = true,
    showSystemUi = true,
    device = TABLET_7_INCH_PORTRAIT,
)
@Composable
fun PlayStoreTabletSevenHome() {
    PlayStoreHomeBody(darkTheme = false)
}

// --- 10-inch tablet ----------------------------------------------------------

@Preview(
    name = "Play Store — 10-inch tablet home",
    showBackground = true,
    showSystemUi = true,
    device = TABLET_10_INCH_PORTRAIT,
)
@Composable
fun PlayStoreTabletTenHome() {
    PlayStoreHomeBody(darkTheme = false)
}
