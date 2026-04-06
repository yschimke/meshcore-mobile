@file:SuppressLint("RestrictedApi")

package ee.schimke.meshcore.app.widget

import android.annotation.SuppressLint
import androidx.compose.remote.tooling.preview.RemotePreview
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "DeviceInfo — populated", widthDp = 290, heightDp = 160)
@Composable
fun DeviceInfoWidgetPopulatedPreview() = RemotePreview {
    DeviceInfoWidgetContent(
        deviceName = "node-peak",
        pubkeyPrefix = "ab1234567890cdef",
        radioInfo = "869.525 MHz · BW 125 kHz · SF 10 · CR 5",
        batteryLine = "81% · 3980 mV",
        batteryProgress = 0.81f,
        storageLine = "512 / 4096 kB",
    )
}

@Preview(name = "DeviceInfo — no data", widthDp = 290, heightDp = 160)
@Composable
fun DeviceInfoWidgetEmptyPreview() = RemotePreview {
    DeviceInfoWidgetContent(
        deviceName = "No device",
        pubkeyPrefix = null,
        radioInfo = null,
        batteryLine = null,
        batteryProgress = null,
        storageLine = null,
    )
}

@Preview(name = "DeviceInfo — stale", widthDp = 290, heightDp = 160)
@Composable
fun DeviceInfoWidgetStalePreview() = RemotePreview {
    DeviceInfoWidgetContent(
        deviceName = "node-peak",
        pubkeyPrefix = "ab1234567890cdef",
        radioInfo = "869.525 MHz",
        batteryLine = "81% · 3980 mV",
        batteryProgress = 0.81f,
        storageLine = "512 / 4096 kB",
        staleLabel = "Updated 3h ago",
    )
}
