package ee.schimke.meshcore.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import ee.schimke.meshcore.wear.ui.theme.WearDimens
import ee.schimke.meshcore.wear.ui.theme.WearMonoBody

// --- Stateful entry point ---------------------------------------------------

@Composable
fun StatusScreen(
    viewModel: WearViewModel = viewModel(),
    onViewContacts: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    StatusBody(state = state, onViewContacts = onViewContacts)
}

// --- Stateless body (previewable) -------------------------------------------

@Composable
fun StatusBody(
    state: WearUiState,
    onViewContacts: () -> Unit = {},
) {
    val columnState = rememberTransformingLazyColumnState()

    TransformingLazyColumn(
        state = columnState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = WearDimens.ScreenPadding),
    ) {
        when (state) {
            is WearUiState.Loading -> {
                item {
                    CircularProgressIndicator()
                }
                item {
                    Text(
                        text = "Connecting...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            is WearUiState.PhoneDisconnected -> {
                item {
                    Icon(
                        imageVector = Icons.Rounded.PhoneAndroid,
                        contentDescription = "Phone disconnected",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(WearDimens.IconSize),
                    )
                }
                item {
                    Text(
                        text = "Phone not reachable",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            is WearUiState.RadioDisconnected -> {
                item {
                    Icon(
                        imageVector = Icons.Rounded.BluetoothDisabled,
                        contentDescription = "Radio disconnected",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(WearDimens.IconSize),
                    )
                }
                item {
                    Text(
                        text = "Radio not connected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            is WearUiState.Connected -> {
                // Connection indicator + device name
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(WearDimens.S),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bluetooth,
                            contentDescription = "Connected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(WearDimens.IconSize),
                        )
                        Text(
                            text = state.deviceName,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                // Battery
                if (state.batteryPercent != null) {
                    item {
                        val batteryColor = if (state.batteryPercent < 30) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(WearDimens.S),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BatteryFull,
                                contentDescription = "Battery",
                                tint = batteryColor,
                                modifier = Modifier.size(WearDimens.IconSize),
                            )
                            Text(
                                text = "${state.batteryPercent}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = batteryColor,
                            )
                        }
                    }
                }

                // Radio info
                if (state.radioInfo != null) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(WearDimens.S),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Sensors,
                                contentDescription = "Radio",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(WearDimens.IconSize),
                            )
                            Text(
                                text = state.radioInfo,
                                style = WearMonoBody,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Contacts count (tappable)
                item {
                    Button(
                        onClick = onViewContacts,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.filledTonalButtonColors(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(WearDimens.S),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Groups,
                                contentDescription = null,
                                modifier = Modifier.size(WearDimens.IconSize),
                            )
                            Text(
                                text = "${state.contactCount} contacts",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }

                // Send message button
                item {
                    Spacer(modifier = Modifier.height(WearDimens.CardGap))
                    Button(
                        onClick = onViewContacts,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(WearDimens.S),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Send,
                                contentDescription = null,
                                modifier = Modifier.size(WearDimens.IconSize),
                            )
                            Text("Send Message")
                        }
                    }
                }
            }

            is WearUiState.Error -> {
                item {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
