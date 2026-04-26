package ee.schimke.meshcore.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ee.schimke.meshcore.app.ui.theme.MeshcoreTheme
import ee.schimke.meshcore.data.repository.SavedDevice
import ee.schimke.meshcore.data.repository.SavedDeviceWithState
import ee.schimke.meshcore.data.repository.SavedTransport

/**
 * Stateless saved-devices list: shown as the first tab on the scanner
 * screen. Callers wire the connect/forget/favorite callbacks to the
 * [ee.schimke.meshcore.app.connection.AppConnectionController] so all
 * network operations stay off the UI scope.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedDevicesPanel(
    devices: List<SavedDeviceWithState>,
    busy: Boolean,
    onConnect: (SavedDevice) -> Unit,
    onForget: (SavedDevice) -> Unit,
    onToggleFavorite: (SavedDevice) -> Unit,
    modifier: Modifier = Modifier,
    connectedDeviceId: String? = null,
    onViewCached: (SavedDevice) -> Unit = {},
) {
    if (devices.isEmpty()) {
        SavedDevicesEmpty(modifier)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(devices, key = { it.device.id }) { item ->
            val device = item.device
            val itemModifier = Modifier.animateItem()
            val dismissState = rememberSwipeToDismissBoxState()
            LaunchedEffect(dismissState.currentValue) {
                if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                    onForget(device)
                }
            }
            SwipeToDismissBox(
                modifier = itemModifier,
                state = dismissState,
                enableDismissFromStartToEnd = false,
                backgroundContent = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                },
            ) {
                SavedDeviceRow(
                    device = device,
                    batteryMillivolts = item.batteryMillivolts,
                    contactsCount = item.contactsCount,
                    busy = busy,
                    isConnected = device.id == connectedDeviceId,
                    onConnect = { onConnect(device) },
                    onToggleFavorite = { onToggleFavorite(device) },
                    onViewCached = { onViewCached(device) },
                )
            }
        }
    }
}

@Composable
private fun SavedDeviceRow(
    device: SavedDevice,
    batteryMillivolts: Int?,
    contactsCount: Int,
    busy: Boolean,
    isConnected: Boolean,
    onConnect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onViewCached: () -> Unit,
) {
    val icon: ImageVector = when (device.transport) {
        is SavedTransport.Ble -> Icons.Rounded.Bluetooth
        is SavedTransport.Tcp -> Icons.Rounded.Lan
        is SavedTransport.Usb -> Icons.Rounded.Usb
    }
    val subtitle: String = buildString {
        if (isConnected) append("Connected · ")
        val batteryStr = batteryMillivolts?.let { "%.2fV".format(it / 1000.0) }
        val contactsStr = if (contactsCount > 0) "$contactsCount contacts" else null
        val parts = listOfNotNull(batteryStr, contactsStr)
        if (parts.isNotEmpty()) {
            append(parts.joinToString(" · "))
        } else {
            when (val t = device.transport) {
                is SavedTransport.Ble -> append(t.identifier)
                is SavedTransport.Tcp -> append("${t.host}:${t.port}")
                is SavedTransport.Usb -> append(t.className)
            }
        }
    }
    val containerColor = when {
        isConnected -> MaterialTheme.colorScheme.tertiaryContainer
        device.favorite -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = when {
        isConnected -> MaterialTheme.colorScheme.onTertiaryContainer
        device.favorite -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val subtleColor = when {
        isConnected -> MaterialTheme.colorScheme.onTertiaryContainer
        device.favorite -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isConnected || device.favorite) contentColor
                    else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = device.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = subtleColor,
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (device.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = if (device.favorite) "Unfavorite" else "Favorite",
                    tint = subtleColor,
                )
            }
            if (isConnected) {
                Button(onClick = onConnect) { Text("View") }
            } else {
                if (contactsCount > 0) {
                    IconButton(onClick = onViewCached) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = "View cached",
                            tint = subtleColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                FilledTonalButton(enabled = !busy, onClick = onConnect) { Text("Connect") }
            }
        }
    }
}

@Composable
private fun SavedDevicesEmpty(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.StarBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "No saved devices yet",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Connect once from the BLE, USB or TCP tab and it will appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// --- Previews -------------------------------------------------------------

private fun sampleDevices(): List<SavedDeviceWithState> = listOf(
    SavedDeviceWithState(
        device = SavedDevice(
            id = "ble:C7:8D:8C:45:5F:78",
            label = "node-peak",
            transport = SavedTransport.Ble("C7:8D:8C:45:5F:78", "MeshCore-ABCD"),
            favorite = true,
            lastConnectedAtMs = 1_700_100_000_000,
        ),
        batteryMillivolts = 3980,
        contactsCount = 5,
    ),
    SavedDeviceWithState(
        device = SavedDevice(
            id = "ble:A1:B2:C3:D4:E5:F6",
            label = "base-station",
            transport = SavedTransport.Ble("A1:B2:C3:D4:E5:F6", "MeshCore-1234"),
            favorite = false,
            lastConnectedAtMs = 1_700_050_000_000,
        ),
        batteryMillivolts = 3210,
        contactsCount = 2,
    ),
    SavedDeviceWithState(
        device = SavedDevice(
            id = "tcp:192.168.1.10:5000",
            label = "192.168.1.10:5000",
            transport = SavedTransport.Tcp("192.168.1.10", 5000),
            favorite = false,
            lastConnectedAtMs = 1_700_000_000_000,
        ),
    ),
)

@Preview(showBackground = true, name = "Saved devices — populated")
@Composable
fun SavedDevicesPopulatedPreview() {
    MeshcoreTheme {
        Column(Modifier.padding(16.dp)) {
            SavedDevicesPanel(
                devices = sampleDevices(),
                busy = false,
                connectedDeviceId = "ble:C7:8D:8C:45:5F:78",
                onConnect = {},
                onForget = {},
                onToggleFavorite = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Saved devices — empty")
@Composable
fun SavedDevicesEmptyPreview() {
    MeshcoreTheme {
        Column(Modifier.padding(16.dp)) {
            SavedDevicesPanel(
                devices = emptyList(),
                busy = false,
                onConnect = {},
                onForget = {},
                onToggleFavorite = {},
            )
        }
    }
}
