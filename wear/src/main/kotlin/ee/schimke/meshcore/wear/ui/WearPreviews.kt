package ee.schimke.meshcore.wear.ui

import androidx.compose.runtime.Composable
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import ee.schimke.meshcore.grpc.ContactMsg
import ee.schimke.meshcore.grpc.ContactType
import ee.schimke.meshcore.wear.ui.theme.MeshcoreWearTheme

// --- StatusScreen previews ---------------------------------------------------

@WearPreviewDevices
@Composable
fun StatusBodyLoadingPreview() {
    MeshcoreWearTheme {
        StatusBody(state = WearUiState.Loading)
    }
}

@WearPreviewDevices
@Composable
fun StatusBodyPhoneDisconnectedPreview() {
    MeshcoreWearTheme {
        StatusBody(state = WearUiState.PhoneDisconnected)
    }
}

@WearPreviewDevices
@Composable
fun StatusBodyRadioDisconnectedPreview() {
    MeshcoreWearTheme {
        StatusBody(state = WearUiState.RadioDisconnected)
    }
}

@WearPreviewDevices
@Composable
fun StatusBodyConnectedPreview() {
    MeshcoreWearTheme {
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

@WearPreviewDevices
@Composable
fun StatusBodyConnectedLowBatteryPreview() {
    MeshcoreWearTheme {
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

@WearPreviewDevices
@Composable
fun StatusBodyErrorPreview() {
    MeshcoreWearTheme {
        StatusBody(state = WearUiState.Error("Connection timeout"))
    }
}

// --- ContactsScreen previews -------------------------------------------------

@WearPreviewDevices
@Composable
fun ContactsBodyEmptyPreview() {
    MeshcoreWearTheme {
        ContactsBody(contacts = emptyList())
    }
}

@WearPreviewDevices
@Composable
fun ContactsBodyFewPreview() {
    MeshcoreWearTheme {
        ContactsBody(
            contacts = listOf(
                fakeContact("Alice"),
                fakeContact("Bob"),
                fakeContact("Charlie"),
            ),
        )
    }
}

// --- QuickReplyScreen previews -----------------------------------------------

@WearPreviewDevices
@Composable
fun QuickReplyBodyPreview() {
    MeshcoreWearTheme {
        QuickReplyBody()
    }
}

// --- Helpers ------------------------------------------------------------------

private fun fakeContact(name: String): ContactMsg =
    ContactMsg.newBuilder()
        .setName(name)
        .setType(ContactType.CHAT)
        .setPublicKey(com.google.protobuf.ByteString.copyFrom(ByteArray(32)))
        .build()
