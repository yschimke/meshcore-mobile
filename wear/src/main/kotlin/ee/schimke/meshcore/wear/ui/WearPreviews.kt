package ee.schimke.meshcore.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import ee.schimke.composeai.preview.AnimatedPreview
import ee.schimke.meshcore.grpc.ContactMsg
import ee.schimke.meshcore.grpc.ContactType
import ee.schimke.meshcore.wear.ui.theme.MeshcoreWearTheme

// AppScaffold's default TimeText reads the wall clock, so each render
// shows a different "HH:MM" string and every wear preview ends up with
// a false diff. Pin the displayed time so PNGs stay byte-stable.
private object FixedTimeSource : TimeSource {
    @Composable override fun currentTime(): String = "10:10"
}

@Composable
private fun PreviewAppScaffold(content: @Composable () -> Unit) {
    AppScaffold(timeText = { TimeText(timeSource = FixedTimeSource) }) {
        content()
    }
}

// --- StatusScreen previews ---------------------------------------------------

@WearPreviewDevices
@Composable
fun StatusBodyLoadingPreview() {
    MeshcoreWearTheme {
        PreviewAppScaffold {
            StatusBody(state = WearUiState.Loading)
        }
    }
}

@WearPreviewDevices
@Composable
fun StatusBodyPhoneDisconnectedPreview() {
    MeshcoreWearTheme {
        PreviewAppScaffold {
            StatusBody(state = WearUiState.PhoneDisconnected)
        }
    }
}

@WearPreviewDevices
@Composable
fun StatusBodyRadioDisconnectedPreview() {
    MeshcoreWearTheme {
        PreviewAppScaffold {
            StatusBody(state = WearUiState.RadioDisconnected)
        }
    }
}

@WearPreviewDevices
@Composable
fun StatusBodyConnectedPreview() {
    MeshcoreWearTheme {
        PreviewAppScaffold {
            StatusBody(
                state = WearUiState.Connected(
                    deviceName = "MeshNode-Alpha-Ridge-Relay-East-Gate",
                    batteryPercent = 72,
                    contactCount = 5,
                    radioInfo = "915.000 MHz · SF11 · BW250k · CR4/8 · telemetry uplink active",
                ),
            )
        }
    }
}

@WearPreviewDevices
@Composable
fun StatusBodyConnectedLowBatteryPreview() {
    MeshcoreWearTheme {
        PreviewAppScaffold {
            StatusBody(
                state = WearUiState.Connected(
                    deviceName = "MeshNode-B",
                    batteryPercent = 18,
                    contactCount = 12,
                    radioInfo = "868.300 MHz · SF12 · BW125k",
                ),
            )
        }
    }
}

/**
 * The middle battery band. [StatusBody] tints the battery row in three steps, but the previews
 * only covered the outer two — 18% (low) and 72% (normal) — leaving the 20..29 `tertiary` branch
 * with no rendered coverage, so a regression there would not show up in a preview diff.
 */
@WearPreviewDevices
@Composable
fun StatusBodyConnectedMediumBatteryPreview() {
    MeshcoreWearTheme {
        PreviewAppScaffold {
            StatusBody(
                state = WearUiState.Connected(
                    deviceName = "MeshNode-C",
                    batteryPercent = 25,
                    contactCount = 7,
                    radioInfo = "869.525 MHz · SF10 · BW250k",
                ),
            )
        }
    }
}

@WearPreviewDevices
@Composable
fun StatusBodyErrorPreview() {
    MeshcoreWearTheme {
        PreviewAppScaffold {
            StatusBody(state = WearUiState.Error("Connection timeout"))
        }
    }
}

// --- ContactsScreen previews -------------------------------------------------

@WearPreviewDevices
@Composable
fun ContactsBodyEmptyPreview() {
    MeshcoreWearTheme {
        PreviewAppScaffold {
            ContactsBody(contacts = emptyList())
        }
    }
}

@WearPreviewDevices
@Composable
fun ContactsBodyFewPreview() {
    MeshcoreWearTheme {
        PreviewAppScaffold {
            ContactsBody(
                contacts = listOf(
                    fakeContact("Alice"),
                    fakeContact("Bob"),
                    fakeContact("Charlie"),
                ),
            )
        }
    }
}

// --- QuickReplyScreen previews -----------------------------------------------

@WearPreviewDevices
@Composable
fun QuickReplyBodyPreview() {
    MeshcoreWearTheme {
        PreviewAppScaffold {
            QuickReplyBody()
        }
    }
}

@Preview(
    name = "Interactive Toggle Chip",
    device = "id:wearos_large_round",
    showSystemUi = true,
    showBackground = true,
)
@Composable
fun InteractiveToggleChipPreview() {
    var telemetryEnabled by remember { mutableStateOf(false) }

    MeshcoreWearTheme {
        PreviewAppScaffold {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Button(
                    onClick = { telemetryEnabled = !telemetryEnabled },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                ) {
                    Text(if (telemetryEnabled) "Telemetry ON" else "Telemetry OFF")
                }
            }
        }
    }
}

@Preview(
    name = "Animated Circular Progress",
    device = "id:wearos_large_round",
    showSystemUi = true,
    showBackground = true,
)
@AnimatedPreview(durationMs = 1200, frameIntervalMs = 100, showCurves = false)
@Composable
fun AnimatedCircularProgressPreview() {
    MeshcoreWearTheme {
        PreviewAppScaffold {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

// --- Helpers ------------------------------------------------------------------

private fun fakeContact(name: String): ContactMsg =
    ContactMsg.newBuilder()
        .setName(name)
        .setType(ContactType.CHAT)
        .setPublicKey(com.google.protobuf.ByteString.copyFrom(ByteArray(32) { name.hashCode().ushr(it % 4 * 8).toByte() }))
        .build()
