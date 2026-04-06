package ee.schimke.meshcore.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.rounded.Battery1Bar
import androidx.compose.material.icons.rounded.Battery3Bar
import androidx.compose.material.icons.rounded.Battery6Bar
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ee.schimke.meshcore.core.model.BatteryInfo
import ee.schimke.meshcore.core.model.ChannelInfo
import ee.schimke.meshcore.core.model.Contact
import ee.schimke.meshcore.core.model.ContactType
import ee.schimke.meshcore.core.model.RadioSettings
import ee.schimke.meshcore.core.model.SelfInfo

/**
 * Elevated "hero" card showing identity + radio + battery for the
 * currently connected MeshCore device. This is the one card per
 * screen that earns extra visual weight, hence `surfaceContainer` +
 * elevation.
 */
@Composable
fun DeviceSummaryCard(
    self: SelfInfo?,
    radio: RadioSettings?,
    battery: BatteryInfo?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (self == null && battery == null && radio == null) {
                // All data still loading
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "  Loading device info\u2026",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = self?.name ?: "Device",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                PubkeyLine(self?.publicKey?.toHex())
                radio?.let { RadioLine(it) }
                battery?.let { BatterySection(it) }
            }
        }
    }
}

@Composable
private fun PubkeyLine(hex: String?) {
    IconLabelRow(
        icon = Icons.Rounded.Fingerprint,
        contentDescription = "Public key",
    ) {
        Text(
            text = hex?.take(16) ?: "—",
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun RadioLine(radio: RadioSettings) {
    IconLabelRow(
        icon = Icons.Rounded.Wifi,
        contentDescription = "Radio",
    ) {
        Text(
            text = buildString {
                append("%.3f".format(radio.frequencyHz / 1_000_000.0))
                append(" MHz · BW ")
                append(radio.bandwidthHz / 1000)
                append(" kHz · SF ")
                append(radio.spreadingFactor)
                append(" · CR ")
                append(radio.codingRate)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun BatterySection(battery: BatteryInfo) {
    val percent = battery.estimatePercent().coerceIn(0, 100)
    val warn = percent < 30
    val tint = if (warn) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
    val icon = when {
        percent >= 80 -> Icons.Rounded.BatteryFull
        percent >= 50 -> Icons.Rounded.Battery6Bar
        percent >= 25 -> Icons.Rounded.Battery3Bar
        else -> Icons.Rounded.Battery1Bar
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = "Battery", tint = tint, modifier = Modifier.size(18.dp))
            Text(
                text = "  $percent% · ${battery.millivolts} mV",
                style = MaterialTheme.typography.bodyMedium,
                color = if (warn) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
            )
        }
        val animatedProgress by animateFloatAsState(
            targetValue = percent / 100f,
            label = "battery",
        )
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth(),
            color = if (warn) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        IconLabelRow(
            icon = Icons.Rounded.Storage,
            contentDescription = "Storage",
        ) {
            Text(
                text = "${battery.storageUsedKb} / ${battery.storageTotalKb} kB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IconLabelRow(
    icon: ImageVector,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text("  ", style = MaterialTheme.typography.bodyMedium)
        content()
    }
}

// --- Contacts -------------------------------------------------------------

private fun Contact.iconFor(): ImageVector = when (type) {
    ContactType.REPEATER -> Icons.Rounded.Router
    ContactType.ROOM -> Icons.Rounded.Groups
    ContactType.SENSOR -> Icons.Rounded.Sensors
    else -> Icons.Rounded.Person
}

private fun Contact.typeLabel(): String = when (type) {
    ContactType.REPEATER -> "Repeater"
    ContactType.ROOM -> "Room"
    ContactType.SENSOR -> "Sensor"
    else -> "Contact"
}

/**
 * Display-only row for a single contact. Uses a filled card with a
 * leading type icon in a tinted circle so contact kinds are
 * distinguishable at a glance.
 */
@Composable
fun ContactRow(
    contact: Contact,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick ?: {},
        enabled = onClick != null,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(36.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = contact.iconFor(),
                        contentDescription = contact.typeLabel(),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp).align(Alignment.CenterVertically),
                    )
                }
            }
            Column(
                modifier = Modifier.padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = buildString {
                        append(contact.typeLabel())
                        append(" · ")
                        append(if (contact.isFlood) "flood" else "${contact.pathLength} hops")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Scrollable list of contacts. `state` is hoisted so callers can
 * attach a scrollbar overlay (see `Modifier.verticalScrollbar` in the
 * app module).
 */
@Composable
fun ContactList(
    contacts: List<Contact>,
    onContactClick: ((Contact) -> Unit)? = null,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
) {
    LazyColumn(
        modifier = modifier.verticalScrollbar(state),
        state = state,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(contacts, key = { it.publicKey.hashCode() }) { contact ->
            ContactRow(
                contact = contact,
                onClick = onContactClick?.let { { it(contact) } },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

/**
 * Inline helper shown when a list of contacts is empty. Kept here
 * because the empty state is part of the ContactList contract.
 */
@Composable
fun ContactListEmpty(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = "No contacts yet",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- Channels ----------------------------------------------------------------

@Composable
fun ChannelRow(
    channel: ChannelInfo,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick ?: {},
        enabled = onClick != null,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.size(36.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Groups,
                        contentDescription = "Channel",
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp).align(Alignment.CenterVertically),
                    )
                }
            }
            Column(
                modifier = Modifier.padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = channel.name.ifBlank { "Channel ${channel.index}" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Channel ${channel.index}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ChannelList(
    channels: List<ChannelInfo>,
    onChannelClick: ((ChannelInfo) -> Unit)? = null,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
) {
    LazyColumn(
        modifier = modifier.verticalScrollbar(state),
        state = state,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(channels, key = { it.index }) { channel ->
            ChannelRow(
                channel = channel,
                onClick = onChannelClick?.let { { it(channel) } },
                modifier = Modifier.animateItem(),
            )
        }
    }
}
