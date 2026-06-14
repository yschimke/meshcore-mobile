package ee.schimke.meshcore.components.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.BluetoothSearching
import androidx.compose.material.icons.filled.SignalCellular0Bar
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeviceUnknown
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ee.schimke.meshcore.core.protocol.MeshCoreConstants

/** Display-only row so [BleDeviceList] can be previewed without Kable types. */
data class BleDeviceRow(val identifier: String, val name: String?, val rssi: Int)

/**
 * Presentational BLE scanner content: renders the pairing tip, scan
 * status, an optional error card, and the device list. It owns no
 * scanning or permission state — the integration layer (which depends
 * on `meshcore-transport-ble`) drives those and hands neutral
 * [BleDeviceRow]s in. Keeping it transport-free means a TCP-only
 * consumer of this module never pulls in BLE/USB transports.
 */
@Composable
fun BleScannerContent(
    rows: List<BleDeviceRow>,
    busy: Boolean,
    meshOnly: Boolean,
    onMeshOnlyChange: (Boolean) -> Unit,
    onPick: (BleDeviceRow) -> Unit,
    modifier: Modifier = Modifier,
    scanError: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BlePairingTipBanner()

        ScanStatusBar(
            shown = rows.size,
            meshOnly = meshOnly,
            onMeshOnlyChange = onMeshOnlyChange,
        )
        AnimatedVisibility(
            visible = scanError != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            scanError?.let {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.BluetoothSearching,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = "Scan error: $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        BleDeviceList(rows = rows, busy = busy, onPick = onPick)
    }
}

/** Filter for whether a scanned BLE [name] looks like a MeshCore radio. */
fun isMeshCoreName(name: String?): Boolean =
    name != null && MeshCoreConstants.BLE_NAME_PREFIXES.any { name.startsWith(it) }

@Composable
fun ScanStatusBar(
    shown: Int,
    meshOnly: Boolean,
    onMeshOnlyChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.BluetoothSearching,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Scanning",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (shown == 1) "1 device" else "$shown devices",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "MeshCore only",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(8.dp))
        Switch(checked = meshOnly, onCheckedChange = onMeshOnlyChange)
    }
}

@Composable
fun BlePermissionPanel(
    lastResult: Map<String, Boolean>?,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Bluetooth permission needed",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Text(
                text = "MeshCore needs the Nearby devices permission to scan for radios over Bluetooth LE.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            lastResult?.let { res ->
                val missing = res.filterValues { !it }.keys
                if (missing.isNotEmpty()) {
                    Text(
                        text = "Still missing: ${missing.joinToString { it.substringAfterLast('.') }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            Button(onClick = onRequest) {
                Text(if (lastResult == null) "Grant permission" else "Try again")
            }
        }
    }
}

private fun signalIcon(rssi: Int): ImageVector = when {
    rssi >= -60 -> Icons.Filled.SignalCellular4Bar
    rssi >= -85 -> Icons.Filled.SignalCellularAlt
    else -> Icons.Filled.SignalCellular0Bar
}

@Composable
fun BleDeviceList(
    rows: List<BleDeviceRow>,
    busy: Boolean,
    onPick: (BleDeviceRow) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
) {
    if (rows.isEmpty()) {
        BleScanEmptyState(modifier)
        return
    }
    LazyColumn(
        modifier = modifier.verticalScrollbar(state),
        state = state,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(rows, key = { it.identifier }) { d -> BleDeviceRowCard(d, busy, onPick, Modifier.animateItem()) }
    }
}

@Composable
private fun BleDeviceRowCard(row: BleDeviceRow, busy: Boolean, onPick: (BleDeviceRow) -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (row.name == null) Icons.Rounded.DeviceUnknown else Icons.Rounded.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = row.name ?: "(unnamed advert)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = row.identifier,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(
                    imageVector = signalIcon(row.rssi),
                    contentDescription = "RSSI ${row.rssi} dBm",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "${row.rssi} dBm",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(8.dp))
            FilledTonalButton(enabled = !busy, onClick = { onPick(row) }) {
                Text("Connect")
            }
        }
    }
}

@Composable
fun BlePairingTipBanner(modifier: Modifier = Modifier) {
    var dismissed by remember { mutableStateOf(false) }
    AnimatedVisibility(
        visible = !dismissed,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp).padding(top = 2.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "The default pin for devices without a screen is 123456. Trouble pairing? Forget the bluetooth device in system settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { dismissed = true }, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
    }
}

@Composable
private fun BleScanEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.BluetoothSearching,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Scanning…",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Move closer to a MeshCore radio.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

