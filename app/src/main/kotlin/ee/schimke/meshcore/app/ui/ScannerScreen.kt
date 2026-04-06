package ee.schimke.meshcore.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ee.schimke.meshcore.app.MeshcoreApp
import ee.schimke.meshcore.app.connection.ConnectionRequest
import ee.schimke.meshcore.app.connection.ConnectionUiState
import ee.schimke.meshcore.app.ui.theme.Dimens
import ee.schimke.meshcore.app.ui.theme.MeshcoreTheme
import ee.schimke.meshcore.components.ui.BleDeviceList
import ee.schimke.meshcore.components.ui.BleDeviceRow
import ee.schimke.meshcore.components.ui.BlePermissionPanel
import ee.schimke.meshcore.components.ui.BleScannerPanel
import ee.schimke.meshcore.components.ui.ScanStatusBar
import ee.schimke.meshcore.components.ui.TcpConnectPanel
import ee.schimke.meshcore.components.ui.UsbPortCard
import ee.schimke.meshcore.components.ui.UsbPortsPanel

// Stateful entry point. All network side effects live in
// [AppConnectionController]; this composable only forwards user
// intent and renders what the controller publishes. That means:
//   - no rememberCoroutineScope for network ops
//   - no direct MeshCoreManager / ManagerState references
//
// [onConnect] is invoked as soon as the controller transitions to
// Connecting or Connected, so navigation to DeviceScreen is driven
// by connection intent, not by screen click-handlers.
@Composable
fun ScannerScreen(
    onConnect: () -> Unit,
    onOpenThemePicker: () -> Unit,
) {
    val app = MeshcoreApp.get()
    val controller = app.connectionController
    val uiState by controller.state.collectAsState()
    val savedDevices by app.savedDevices.devices.collectAsState(initial = emptyList())
    val connectedDeviceId by controller.connectedDeviceId.collectAsState()
    val busy = uiState is ConnectionUiState.Connecting

    LaunchedEffect(uiState) {
        if (uiState is ConnectionUiState.Connecting || uiState is ConnectionUiState.Connected) {
            onConnect()
        }
    }

    ScannerBody(
        onOpenThemePicker = onOpenThemePicker,
        savedContent = {
            SavedDevicesPanel(
                devices = savedDevices,
                busy = busy,
                connectedDeviceId = connectedDeviceId,
                onConnect = { device ->
                    if (device.id == connectedDeviceId) {
                        onConnect() // navigate to device screen
                    } else if (!busy) {
                        controller.requestReconnect(device)
                    }
                },
                onForget = { device -> controller.forgetSavedDevice(device.id) },
                onToggleFavorite = { device -> controller.toggleFavorite(device.id) },
            )
        },
        bleContent = {
            BleScannerPanel(
                busy = busy,
                onConnectAdvertisement = { adv ->
                    if (!busy) controller.requestConnect(ConnectionRequest.Ble(adv))
                },
            )
        },
        usbContent = {
            UsbPortsPanel(
                busy = busy,
                listPorts = { app.usbPorts.list() },
                onConnect = { port ->
                    if (!busy) controller.requestConnect(ConnectionRequest.Usb(port))
                },
            )
        },
        tcpContent = {
            TcpConnectPanel(
                busy = busy,
                onConnect = { host, p ->
                    if (!busy) controller.requestConnect(ConnectionRequest.Tcp(host, p))
                },
            )
        },
    )

}

/**
 * Stateless scanner screen body. Owns only the selected tab (trivial UI
 * state) and delegates all transport-specific content to slot lambdas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerBody(
    savedContent: @Composable () -> Unit,
    bleContent: @Composable () -> Unit,
    usbContent: @Composable () -> Unit,
    tcpContent: @Composable () -> Unit,
    onOpenThemePicker: () -> Unit = {},
    modifier: Modifier = Modifier,
    initialTab: Int = 0,
) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "MeshCore",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                actions = {
                    IconButton(onClick = onOpenThemePicker) {
                        Icon(
                            imageVector = Icons.Rounded.Contrast,
                            contentDescription = "Theme",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            PrimaryTabRow(
                selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                TransportTab(selected = tab == 0, icon = Icons.Rounded.Star, label = "Saved") { tab = 0 }
                TransportTab(selected = tab == 1, icon = Icons.Rounded.Bluetooth, label = "BLE") { tab = 1 }
                TransportTab(selected = tab == 2, icon = Icons.Rounded.Usb, label = "USB") { tab = 2 }
                TransportTab(selected = tab == 3, icon = Icons.Rounded.Lan, label = "TCP") { tab = 3 }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Dimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.CardGap),
            ) {
                when (tab) {
                    0 -> savedContent()
                    1 -> bleContent()
                    2 -> usbContent()
                    3 -> tcpContent()
                }
            }
        }
    }
}

@Composable
private fun TransportTab(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = { Text(label, style = MaterialTheme.typography.labelLarge) },
        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
    )
}

// --- Preview fixtures -----------------------------------------------------

@Composable
private fun previewBleEmpty() {
    ScanStatusBar(shown = 0, meshOnly = true, onMeshOnlyChange = {})
    BleDeviceList(rows = emptyList(), busy = false, onPick = {}, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun previewBleFew() {
    ScanStatusBar(shown = 2, meshOnly = true, onMeshOnlyChange = {})
    BleDeviceList(
        rows = listOf(
            BleDeviceRow("C7:8D:8C:45:5F:78", "MeshCore-ABCD", -52),
            BleDeviceRow("A1:B2:C3:D4:E5:F6", "MeshCore-1234", -74),
        ),
        busy = false,
        onPick = {},
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun previewBleMany() {
    ScanStatusBar(shown = 10, meshOnly = true, onMeshOnlyChange = {})
    BleDeviceList(
        rows = (0 until 10).map { i ->
            BleDeviceRow(
                identifier = "AA:BB:CC:DD:EE:%02X".format(i),
                name = if (i == 3) null else "MeshCore-%04X".format(0xA000 + i),
                rssi = -40 - i * 5,
            )
        },
        busy = false,
        onPick = {},
        modifier = Modifier.fillMaxWidth().height(560.dp),
    )
}

@Composable
private fun previewBlePermission() {
    BlePermissionPanel(lastResult = null, onRequest = {})
}

@Composable
private fun previewUsbEmpty() {
    UsbPortsPanel(busy = false, listPorts = { emptyList() }, onConnect = {})
}

@Composable
private fun previewUsbFew() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Text(
                text = "USB serial",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        UsbPortCard(label = "CdcAcmSerialPortWrapper", busy = false, onConnect = {})
        UsbPortCard(label = "FtdiSerialPortWrapper", busy = false, onConnect = {})
    }
}

@Composable
private fun previewTcpIdle() {
    TcpConnectPanel(busy = false, onConnect = { _, _ -> })
}

@Composable
private fun previewEmpty() {
    Text("(preview slot)")
}

@Composable
private fun previewSavedEmpty() {
    SavedDevicesPanel(
        devices = emptyList(),
        busy = false,
        onConnect = {},
        onForget = {},
        onToggleFavorite = {},
    )
}

@Composable
private fun previewSavedPopulated() {
    SavedDevicesPanel(
        devices = listOf(
            ee.schimke.meshcore.app.data.SavedDevice(
                id = "ble:C7:8D:8C:45:5F:78",
                label = "MeshCore-ABCD",
                transport = ee.schimke.meshcore.app.data.SavedTransport.Ble(
                    "C7:8D:8C:45:5F:78", "MeshCore-ABCD",
                ),
                favorite = true,
                lastConnectedAtMs = 1_700_100_000_000,
            ),
            ee.schimke.meshcore.app.data.SavedDevice(
                id = "tcp:192.168.1.10:5000",
                label = "192.168.1.10:5000",
                transport = ee.schimke.meshcore.app.data.SavedTransport.Tcp("192.168.1.10", 5000),
                favorite = false,
                lastConnectedAtMs = 1_700_050_000_000,
            ),
        ),
        busy = false,
        onConnect = {},
        onForget = {},
        onToggleFavorite = {},
    )
}

// --- Saved tab previews ---------------------------------------------------

@Preview(showBackground = true, showSystemUi = true, device = Devices.PIXEL_7, name = "Scanner — Saved empty")
@Composable
fun ScannerSavedEmptyPreview() {
    MeshcoreTheme {
        ScannerBody(
            savedContent = { previewSavedEmpty() },
            bleContent = { previewEmpty() },
            usbContent = { previewEmpty() },
            tcpContent = { previewEmpty() },
            initialTab = 0,
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, device = Devices.PIXEL_7, name = "Scanner — Saved populated")
@Composable
fun ScannerSavedPopulatedPreview() {
    MeshcoreTheme {
        ScannerBody(
            savedContent = { previewSavedPopulated() },
            bleContent = { previewEmpty() },
            usbContent = { previewEmpty() },
            tcpContent = { previewEmpty() },
            initialTab = 0,
        )
    }
}

// --- BLE tab previews -----------------------------------------------------

@Preview(showBackground = true, showSystemUi = true, device = Devices.PIXEL_7, name = "Scanner — BLE permission needed")
@Composable
fun ScannerBlePermissionPreview() {
    MeshcoreTheme {
        ScannerBody(
            savedContent = { previewEmpty() },
            bleContent = { previewBlePermission() },
            usbContent = { previewEmpty() },
            tcpContent = { previewEmpty() },
            initialTab = 1,
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, device = Devices.PIXEL_7, name = "Scanner — BLE scanning, 0 devices")
@Composable
fun ScannerBleEmptyPreview() {
    MeshcoreTheme {
        ScannerBody(
            savedContent = { previewEmpty() },
            bleContent = { previewBleEmpty() },
            usbContent = { previewEmpty() },
            tcpContent = { previewEmpty() },
            initialTab = 1,
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, device = Devices.PIXEL_7, name = "Scanner — BLE scanning, 2 devices")
@Composable
fun ScannerBleFewPreview() {
    MeshcoreTheme {
        ScannerBody(
            savedContent = { previewEmpty() },
            bleContent = { previewBleFew() },
            usbContent = { previewEmpty() },
            tcpContent = { previewEmpty() },
            initialTab = 1,
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, device = Devices.PIXEL_7, name = "Scanner — BLE scanning, 10 devices (scrolls)")
@Composable
fun ScannerBleManyPreview() {
    MeshcoreTheme {
        ScannerBody(
            savedContent = { previewEmpty() },
            bleContent = { previewBleMany() },
            usbContent = { previewEmpty() },
            tcpContent = { previewEmpty() },
            initialTab = 1,
        )
    }
}

// --- USB tab previews -----------------------------------------------------

@Preview(showBackground = true, showSystemUi = true, device = Devices.PIXEL_7, name = "Scanner — USB, no ports")
@Composable
fun ScannerUsbEmptyPreview() {
    MeshcoreTheme {
        ScannerBody(
            savedContent = { previewEmpty() },
            bleContent = { previewEmpty() },
            usbContent = { previewUsbEmpty() },
            tcpContent = { previewEmpty() },
            initialTab = 2,
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, device = Devices.PIXEL_7, name = "Scanner — USB, 2 ports")
@Composable
fun ScannerUsbFewPreview() {
    MeshcoreTheme {
        ScannerBody(
            savedContent = { previewEmpty() },
            bleContent = { previewEmpty() },
            usbContent = { previewUsbFew() },
            tcpContent = { previewEmpty() },
            initialTab = 2,
        )
    }
}

// --- TCP tab previews -----------------------------------------------------

@Preview(showBackground = true, showSystemUi = true, device = Devices.PIXEL_7, name = "Scanner — TCP idle")
@Composable
fun ScannerTcpPreview() {
    MeshcoreTheme {
        ScannerBody(
            savedContent = { previewEmpty() },
            bleContent = { previewEmpty() },
            usbContent = { previewEmpty() },
            tcpContent = { previewTcpIdle() },
            initialTab = 3,
        )
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    device = Devices.PIXEL_7,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    name = "Scanner — Saved populated, dark",
)
@Composable
fun ScannerBleDarkPreview() {
    MeshcoreTheme(darkTheme = true) {
        ScannerBody(
            savedContent = { previewSavedPopulated() },
            bleContent = { previewEmpty() },
            usbContent = { previewEmpty() },
            tcpContent = { previewEmpty() },
            initialTab = 0,
        )
    }
}
