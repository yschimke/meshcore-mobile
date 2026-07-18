package ee.schimke.meshcore.app.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Wallpapers
import androidx.compose.ui.unit.dp
import ee.schimke.meshcore.app.ui.theme.MeshcoreTheme
import ee.schimke.meshcore.core.model.BatteryInfo
import ee.schimke.meshcore.core.model.Contact
import ee.schimke.meshcore.core.model.ContactType
import ee.schimke.meshcore.core.model.PublicKey
import ee.schimke.meshcore.core.model.RadioSettings
import ee.schimke.meshcore.core.model.SelfInfo
import ee.schimke.meshcore.components.ui.BleDeviceRow
import ee.schimke.meshcore.components.ui.BleDeviceList
import ee.schimke.meshcore.components.ui.BlePermissionPanel
import ee.schimke.meshcore.components.ui.ContactList
import ee.schimke.meshcore.components.ui.ContactRow
import ee.schimke.meshcore.components.ui.DeviceSummaryCard
import ee.schimke.meshcore.components.ui.TcpConnectPanel
import kotlin.time.Instant
import kotlinx.io.bytestring.ByteString

// Each catalog component renders in BOTH light and dark via @MeshcoreModes (a `name = "Light"` +
// `name = "Dark", uiMode = NIGHT_YES` multipreview). MeshcoreTheme's default `darkTheme =
// isSystemInDarkTheme()` follows the render's uiMode, so the Dark variant is a real dark render —
// the design-catalog export folds the pair into one component the preview server's Light/Dark
// toggle swaps in place. Each preview keeps its own widthDp (the catalog render width), so wrap the
// two @Preview lines rather than a width-less shared annotation.

// --- Shared fixtures ------------------------------------------------------

private fun pk(fill: Byte): PublicKey =
    PublicKey.fromBytes(ByteString(*ByteArray(32) { fill }))

private fun contact(
    name: String,
    pathLen: Int = 1,
    fill: Byte = 0x11,
    type: ContactType = ContactType.CHAT,
): Contact = Contact(
    publicKey = pk(fill),
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

private fun bleRow(name: String?, id: String, rssi: Int) = BleDeviceRow(id, name, rssi)

private fun tenContacts(): List<Contact> {
    val names = listOf(
        "alice", "bob-repeater", "charlie", "dana", "eve-hq",
        "frank", "common-room", "garden-sensor", "hiker-ian", "julia-summit",
    )
    val types = listOf(
        ContactType.CHAT, ContactType.REPEATER, ContactType.CHAT,
        ContactType.CHAT, ContactType.CHAT, ContactType.CHAT,
        ContactType.ROOM, ContactType.SENSOR, ContactType.CHAT, ContactType.CHAT,
    )
    return names.mapIndexed { i, n ->
        contact(n, pathLen = i % 4, fill = (0x10 + i).toByte(), type = types[i])
    }
}

// --- DeviceSummaryCard ----------------------------------------------------

@Preview(name = "Light", widthDp = 310, heightDp = 170)
@Preview(name = "Dark", widthDp = 310, heightDp = 170, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun DeviceSummaryCardPopulatedPreview() {
    MeshcoreTheme {
            DeviceSummaryCard(
                self = SelfInfo(
                    advertType = 1,
                    txPowerDbm = 14,
                    maxPowerDbm = 22,
                    publicKey = pk(0xAB.toByte()),
                    latitude = 53.0,
                    longitude = -1.5,
                    multiAcks = 0,
                    advertLocationPolicy = 0,
                    telemetryFlags = 0,
                    manualAddContacts = 0,
                    radio = RadioSettings(869_525_000, 125_000, 10, 5),
                    name = "node-peak",
                ),
                radio = RadioSettings(869_525_000, 125_000, 10, 5),
                battery = BatteryInfo(3980, 512, 4096),
            )
        }
}

@Preview(showBackground = true, name = "Light")
@Preview(showBackground = true, name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun DeviceSummaryCardLoadingPreview() {
    MeshcoreTheme {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            DeviceSummaryCard(self = null, radio = null, battery = null)
        }
    }
}

// Low-battery state: DeviceSummaryCard's BatterySection switches to the tertiary "warn" tint and the
// low-battery icon below 30%, which no standalone card preview otherwise exercises.
@Preview(showBackground = true, name = "Light")
@Preview(showBackground = true, name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun DeviceSummaryCardLowBatteryPreview() {
    MeshcoreTheme {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            DeviceSummaryCard(
                self = SelfInfo(
                    advertType = 1,
                    txPowerDbm = 14,
                    maxPowerDbm = 22,
                    publicKey = pk(0xAB.toByte()),
                    latitude = 53.0,
                    longitude = -1.5,
                    multiAcks = 0,
                    advertLocationPolicy = 0,
                    telemetryFlags = 0,
                    manualAddContacts = 0,
                    radio = RadioSettings(869_525_000, 125_000, 10, 5),
                    name = "node-cabin",
                ),
                radio = RadioSettings(869_525_000, 125_000, 10, 5),
                battery = BatteryInfo(3210, 3800, 4096),
            )
        }
    }
}

// --- ContactRow / ContactList --------------------------------------------

// One preview per contact kind (chat / repeater / room / sensor) so the catalog exports them as a
// ContactRow component set keyed on type, instead of one stacked "variants" sticker.

@Preview(showBackground = true, name = "Light", widthDp = 340)
@Preview(showBackground = true, name = "Dark", widthDp = 340, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ContactRowChatPreview() {
    MeshcoreTheme {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            ContactRow(contact("alice", pathLen = -1, fill = 0x11, type = ContactType.CHAT))
        }
    }
}

@Preview(showBackground = true, name = "Light", widthDp = 340)
@Preview(showBackground = true, name = "Dark", widthDp = 340, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ContactRowRepeaterPreview() {
    MeshcoreTheme {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            ContactRow(contact("bob-repeater", pathLen = 2, fill = 0x22, type = ContactType.REPEATER))
        }
    }
}

@Preview(showBackground = true, name = "Light", widthDp = 340)
@Preview(showBackground = true, name = "Dark", widthDp = 340, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ContactRowRoomPreview() {
    MeshcoreTheme {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            ContactRow(contact("common-room", pathLen = 0, fill = 0x33, type = ContactType.ROOM))
        }
    }
}

@Preview(showBackground = true, name = "Light", widthDp = 340)
@Preview(showBackground = true, name = "Dark", widthDp = 340, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ContactRowSensorPreview() {
    MeshcoreTheme {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            ContactRow(contact("soil-sensor-1", pathLen = 3, fill = 0x44, type = ContactType.SENSOR))
        }
    }
}

@Preview(showBackground = true, name = "Light", widthDp = 340)
@Preview(showBackground = true, name = "Dark", widthDp = 340, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ContactListEmptyPreview() {
    MeshcoreTheme {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Contacts (0)", style = MaterialTheme.typography.titleSmall)
            ContactList(
                contacts = emptyList(),
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
        }
    }
}

@Preview(showBackground = true, name = "Light", widthDp = 340)
@Preview(showBackground = true, name = "Dark", widthDp = 340, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ContactListFewPreview() {
    MeshcoreTheme {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Contacts (2)", style = MaterialTheme.typography.titleSmall)
            ContactList(
                contacts = listOf(
                    contact("alice", -1, 0x11),
                    contact("bob-repeater", 2, 0x22, ContactType.REPEATER),
                ),
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
        }
    }
}

@Preview(showBackground = true, name = "Light", widthDp = 340)
@Preview(showBackground = true, name = "Dark", widthDp = 340, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ContactListManyPreview() {
    MeshcoreTheme {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Contacts (10)", style = MaterialTheme.typography.titleSmall)
            ContactList(
                contacts = tenContacts(),
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
        }
    }
}

// --- BleDeviceList --------------------------------------------------------

@Preview(showBackground = true, name = "Light", widthDp = 340)
@Preview(showBackground = true, name = "Dark", widthDp = 340, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun BleDeviceListEmptyPreview() {
    MeshcoreTheme {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Scanning… 0 devices", style = MaterialTheme.typography.titleSmall)
            BleDeviceList(
                rows = emptyList(),
                busy = false,
                onPick = {},
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
        }
    }
}

@Preview(showBackground = true, name = "Light", widthDp = 340)
@Preview(showBackground = true, name = "Dark", widthDp = 340, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun BleDeviceListFewPreview() {
    MeshcoreTheme {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            BleDeviceList(
                rows = listOf(
                    bleRow("MeshCore-ABCD", "C7:8D:8C:45:5F:78", -52),
                    bleRow("MeshCore-1234", "A1:B2:C3:D4:E5:F6", -74),
                ),
                busy = false,
                onPick = {},
                modifier = Modifier.fillMaxWidth().height(320.dp),
            )
        }
    }
}

@Preview(showBackground = true, name = "Light", widthDp = 340)
@Preview(showBackground = true, name = "Dark", widthDp = 340, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun BleDeviceListManyPreview() {
    MeshcoreTheme {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            BleDeviceList(
                rows = (0 until 10).map { i ->
                    bleRow(
                        name = if (i == 3) null else "MeshCore-%04X".format(0xA000 + i),
                        id = "AA:BB:CC:DD:EE:%02X".format(i),
                        rssi = -40 - i * 5,
                    )
                },
                busy = false,
                onPick = {},
                modifier = Modifier.fillMaxWidth().height(520.dp),
            )
        }
    }
}

// --- TcpConnectPanel ------------------------------------------------------

@Preview(showBackground = true, name = "Light", widthDp = 340)
@Preview(showBackground = true, name = "Dark", widthDp = 340, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun TcpConnectPanelIdlePreview() {
    MeshcoreTheme {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            TcpConnectPanel(busy = false, onConnect = { _, _ -> })
        }
    }
}

@Preview(showBackground = true, name = "Light", widthDp = 340)
@Preview(showBackground = true, name = "Dark", widthDp = 340, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun TcpConnectPanelBusyPreview() {
    MeshcoreTheme {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            TcpConnectPanel(busy = true, onConnect = { _, _ -> })
        }
    }
}

// --- BlePermissionPanel ---------------------------------------------------

@Preview(showBackground = true, name = "Light", widthDp = 340)
@Preview(showBackground = true, name = "Dark", widthDp = 340, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun BlePermissionPanelFirstPreview() {
    MeshcoreTheme {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            BlePermissionPanel(lastResult = null, onRequest = {})
        }
    }
}

@Preview(showBackground = true, name = "Light", widthDp = 340)
@Preview(showBackground = true, name = "Dark", widthDp = 340, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun BlePermissionPanelDeniedPreview() {
    MeshcoreTheme {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            BlePermissionPanel(
                lastResult = mapOf(
                    "android.permission.BLUETOOTH_SCAN" to false,
                    "android.permission.BLUETOOTH_CONNECT" to true,
                ),
                onRequest = {},
            )
        }
    }
}
