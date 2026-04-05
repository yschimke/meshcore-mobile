package ee.schimke.meshcore.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import ee.schimke.meshcore.app.ui.theme.MeshcoreTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ee.schimke.meshcore.core.model.BatteryInfo
import ee.schimke.meshcore.core.model.Contact
import ee.schimke.meshcore.core.model.ContactType
import ee.schimke.meshcore.core.model.PublicKey
import ee.schimke.meshcore.core.model.RadioSettings
import ee.schimke.meshcore.core.model.SelfInfo
import ee.schimke.meshcore.mobile.ui.BleDeviceRow
import ee.schimke.meshcore.mobile.ui.BleDeviceList
import ee.schimke.meshcore.mobile.ui.BlePermissionPanel
import ee.schimke.meshcore.mobile.ui.ContactList
import ee.schimke.meshcore.mobile.ui.ContactRow
import ee.schimke.meshcore.mobile.ui.DeviceSummaryCard
import ee.schimke.meshcore.mobile.ui.TcpConnectPanel
import kotlin.time.Instant
import kotlinx.io.bytestring.ByteString

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

@Preview(showBackground = true, name = "DeviceSummaryCard — populated")
@Composable
fun DeviceSummaryCardPopulatedPreview() {
    MeshcoreTheme {
        Column(Modifier.padding(16.dp)) {
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
}

@Preview(showBackground = true, name = "DeviceSummaryCard — loading")
@Composable
fun DeviceSummaryCardLoadingPreview() {
    MeshcoreTheme {
        Column(Modifier.padding(16.dp)) {
            DeviceSummaryCard(self = null, radio = null, battery = null)
        }
    }
}

// --- ContactRow / ContactList --------------------------------------------

@Preview(showBackground = true, name = "ContactRow — variants")
@Composable
fun ContactRowVariantsPreview() {
    MeshcoreTheme {
        Column(Modifier.padding(16.dp)) {
            ContactRow(contact("alice", pathLen = -1, fill = 0x11, type = ContactType.CHAT))
            ContactRow(contact("bob-repeater", pathLen = 2, fill = 0x22, type = ContactType.REPEATER))
            ContactRow(contact("common-room", pathLen = 0, fill = 0x33, type = ContactType.ROOM))
            ContactRow(contact("soil-sensor-1", pathLen = 3, fill = 0x44, type = ContactType.SENSOR))
        }
    }
}

@Preview(showBackground = true, name = "ContactList — empty")
@Composable
fun ContactListEmptyPreview() {
    MeshcoreTheme {
        Column(Modifier.padding(16.dp)) {
            Text("Contacts (0)", style = MaterialTheme.typography.titleSmall)
            ContactList(
                contacts = emptyList(),
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
        }
    }
}

@Preview(showBackground = true, name = "ContactList — 2 items")
@Composable
fun ContactListFewPreview() {
    MeshcoreTheme {
        Column(Modifier.padding(16.dp)) {
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

@Preview(showBackground = true, name = "ContactList — 10 items (scrolls)")
@Composable
fun ContactListManyPreview() {
    MeshcoreTheme {
        Column(Modifier.padding(16.dp)) {
            Text("Contacts (10)", style = MaterialTheme.typography.titleSmall)
            ContactList(
                contacts = tenContacts(),
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
        }
    }
}

// --- BleDeviceList --------------------------------------------------------

@Preview(showBackground = true, name = "BleDeviceList — empty")
@Composable
fun BleDeviceListEmptyPreview() {
    MeshcoreTheme {
        Column(Modifier.padding(16.dp)) {
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

@Preview(showBackground = true, name = "BleDeviceList — 2 devices")
@Composable
fun BleDeviceListFewPreview() {
    MeshcoreTheme {
        Column(Modifier.padding(16.dp)) {
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

@Preview(showBackground = true, name = "BleDeviceList — 10 devices (scrolls)")
@Composable
fun BleDeviceListManyPreview() {
    MeshcoreTheme {
        Column(Modifier.padding(16.dp)) {
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

@Preview(showBackground = true, name = "TcpConnectPanel — idle")
@Composable
fun TcpConnectPanelIdlePreview() {
    MeshcoreTheme {
        Column(Modifier.padding(16.dp)) {
            TcpConnectPanel(busy = false, onConnect = { _, _ -> })
        }
    }
}

@Preview(showBackground = true, name = "TcpConnectPanel — busy")
@Composable
fun TcpConnectPanelBusyPreview() {
    MeshcoreTheme {
        Column(Modifier.padding(16.dp)) {
            TcpConnectPanel(busy = true, onConnect = { _, _ -> })
        }
    }
}

// --- BlePermissionPanel ---------------------------------------------------

@Preview(showBackground = true, name = "BlePermissionPanel — first request")
@Composable
fun BlePermissionPanelFirstPreview() {
    MeshcoreTheme {
        Column(Modifier.padding(16.dp)) {
            BlePermissionPanel(lastResult = null, onRequest = {})
        }
    }
}

@Preview(showBackground = true, name = "BlePermissionPanel — denied")
@Composable
fun BlePermissionPanelDeniedPreview() {
    MeshcoreTheme {
        Column(Modifier.padding(16.dp)) {
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
