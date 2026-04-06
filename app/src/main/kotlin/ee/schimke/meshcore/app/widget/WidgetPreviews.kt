@file:SuppressLint("RestrictedApi")

package ee.schimke.meshcore.app.widget

import android.annotation.SuppressLint
import androidx.compose.remote.tooling.preview.RemotePreview
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

// --- Battery widget previews ------------------------------------------------

@Preview(name = "BatteryWidget — populated", widthDp = 180, heightDp = 100)
@Composable
fun BatteryWidgetPopulatedPreview() = RemotePreview {
    BatteryWidgetContent(
        batteryPercent = "78%",
        batteryMv = "3980 mV",
        snr = "SNR 12",
    )
}

@Preview(name = "BatteryWidget — no data", widthDp = 180, heightDp = 100)
@Composable
fun BatteryWidgetEmptyPreview() = RemotePreview {
    BatteryWidgetContent(
        batteryPercent = "—",
        batteryMv = null,
        snr = null,
    )
}

// --- Mesh status widget previews --------------------------------------------

@Preview(name = "MeshStatusWidget — connected", widthDp = 220, heightDp = 100)
@Composable
fun MeshStatusWidgetConnectedPreview() = RemotePreview {
    MeshStatusWidgetContent(
        deviceName = "node-peak",
        contactCount = "5 contacts",
        frequencyMhz = "869.525 MHz",
    )
}

@Preview(name = "MeshStatusWidget — disconnected", widthDp = 220, heightDp = 100)
@Composable
fun MeshStatusWidgetDisconnectedPreview() = RemotePreview {
    MeshStatusWidgetContent(
        deviceName = "Not connected",
        contactCount = "0 contacts",
        frequencyMhz = null,
    )
}

// --- Last message widget previews -------------------------------------------

@Preview(name = "LastMessageWidget — with message", widthDp = 250, heightDp = 80)
@Composable
fun LastMessageWidgetPopulatedPreview() = RemotePreview {
    LastMessageWidgetContent(message = "Hello from the summit!")
}

@Preview(name = "LastMessageWidget — no messages", widthDp = 250, heightDp = 80)
@Composable
fun LastMessageWidgetEmptyPreview() = RemotePreview {
    LastMessageWidgetContent(message = "(none yet)")
}

// --- Quick send widget previews ---------------------------------------------

@Preview(name = "QuickSendWidget", widthDp = 140, heightDp = 60)
@Composable
fun QuickSendWidgetPreview() = RemotePreview {
    QuickSendWidgetContent()
}
