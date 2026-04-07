package ee.schimke.meshcore.wear.ui

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import ee.schimke.meshcore.grpc.ContactMsg
import ee.schimke.meshcore.grpc.ContactType
import ee.schimke.meshcore.wear.ui.theme.MeshcoreWearTheme

// --- StatusScreen previews ---------------------------------------------------

@WearPreviewDevices
@Composable
fun StatusBodyLoadingPreview() {
    MeshcoreWearTheme {
        AppScaffold {
            StatusBody(state = WearUiState.Loading)
        }
    }
}

@WearPreviewDevices
@Composable
fun StatusBodyPhoneDisconnectedPreview() {
    MeshcoreWearTheme {
        AppScaffold {
            StatusBody(state = WearUiState.PhoneDisconnected)
        }
    }
}

@WearPreviewDevices
@Composable
fun StatusBodyRadioDisconnectedPreview() {
    MeshcoreWearTheme {
        AppScaffold {
            StatusBody(state = WearUiState.RadioDisconnected)
        }
    }
}

@WearPreviewDevices
@Composable
fun StatusBodyConnectedPreview() {
    MeshcoreWearTheme {
        AppScaffold {
            StatusBody(
                state = WearUiState.Connected(
                    deviceName = "MeshNode-A",
                    batteryPercent = 72,
                    contactCount = 5,
                    radioInfo = "915.000 MHz · SF11 · BW250k",
                ),
            )
        }
    }
}

@WearPreviewDevices
@Composable
fun StatusBodyConnectedLowBatteryPreview() {
    MeshcoreWearTheme {
        AppScaffold {
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

@WearPreviewDevices
@Composable
fun StatusBodyErrorPreview() {
    MeshcoreWearTheme {
        AppScaffold {
            StatusBody(state = WearUiState.Error("Connection timeout"))
        }
    }
}

// --- ContactsScreen previews -------------------------------------------------

@WearPreviewDevices
@Composable
fun ContactsBodyEmptyPreview() {
    MeshcoreWearTheme {
        AppScaffold {
            ContactsBody(contacts = emptyList())
        }
    }
}

@WearPreviewDevices
@Composable
fun ContactsBodyFewPreview() {
    MeshcoreWearTheme {
        AppScaffold {
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
        AppScaffold {
            QuickReplyBody()
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
