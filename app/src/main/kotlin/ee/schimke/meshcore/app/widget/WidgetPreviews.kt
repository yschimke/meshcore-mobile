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
        batteryPercent = "42%",
        batteryMv = "3650 mV",
        snr = "SNR 8",
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
        deviceName = "base-camp",
        contactCount = "3 contacts",
        frequencyMhz = "868.000 MHz",
    )
}

@Preview(name = "MeshStatusWidget — disconnected", widthDp = 220, heightDp = 100)
@Composable
fun MeshStatusWidgetDisconnectedPreview() = RemotePreview {
    MeshStatusWidgetContent(
        deviceName = "base-camp",
        contactCount = "3 contacts",
        frequencyMhz = "868.000 MHz",
    )
}

// --- Last message widget previews -------------------------------------------

@Preview(name = "LastMessageWidget — with message", widthDp = 250, heightDp = 80)
@Composable
fun LastMessageWidgetPopulatedPreview() = RemotePreview {
    LastMessageWidgetContent(message = "#1 Weather clear, proceeding to summit")
}

@Preview(name = "LastMessageWidget — no messages", widthDp = 250, heightDp = 80)
@Composable
fun LastMessageWidgetEmptyPreview() = RemotePreview {
    LastMessageWidgetContent(message = "(none yet)")
}

// --- Connection status widget previews --------------------------------------

@Preview(name = "ConnectionStatus — connected", widthDp = 220, heightDp = 100)
@Composable
fun ConnectionStatusConnectedPreview() = RemotePreview {
    ConnectionStatusWidgetContent(
        status = "Connected",
        deviceName = "base-camp",
        lastSeen = null,
    )
}

@Preview(name = "ConnectionStatus — disconnected", widthDp = 220, heightDp = 100)
@Composable
fun ConnectionStatusDisconnectedPreview() = RemotePreview {
    ConnectionStatusWidgetContent(
        status = "Disconnected",
        deviceName = "base-camp",
        lastSeen = "Last seen 2h ago",
    )
}

@Preview(name = "ConnectionStatus — stale", widthDp = 220, heightDp = 120)
@Composable
fun ConnectionStatusStalePreview() = RemotePreview {
    ConnectionStatusWidgetContent(
        status = "Disconnected",
        deviceName = "base-camp",
        lastSeen = "Last seen 6h ago",
        staleLabel = "Updated 6h ago",
    )
}
