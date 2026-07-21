package ee.schimke.meshcore.app.ui

import ee.schimke.meshcore.components.ui.DeviceBody
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import ee.schimke.meshcore.app.di.LocalAppGraph
import ee.schimke.meshcore.components.generated.resources.Res
import ee.schimke.meshcore.components.generated.resources.warning_cached_data
import ee.schimke.meshcore.data.repository.toBattery
import ee.schimke.meshcore.data.repository.toRadio
import ee.schimke.meshcore.data.repository.toSelfInfo
import org.jetbrains.compose.resources.stringResource

/**
 * Read-only device screen that shows cached data from Room for a saved
 * device that isn't currently connected. Reuses the stateless [DeviceBody]
 * composable with all callbacks disabled.
 */
@Composable
fun CachedDeviceScreen(
    deviceId: String,
    onBack: () -> Unit,
    onOpenThemePicker: () -> Unit = {},
) {
    val repository = LocalAppGraph.current.repository
    val state by repository.observeDeviceState(deviceId).collectAsState(initial = null)
    val contacts by repository.observeContacts(deviceId).collectAsState(initial = emptyList())
    val channels by repository.observeChannels(deviceId).collectAsState(initial = emptyList())

    val selfInfo = state?.toSelfInfo()
    val battery = state?.toBattery()
    val radio = state?.toRadio()

    DeviceBody(
        self = selfInfo,
        battery = battery,
        radio = radio,
        contacts = contacts,
        channels = channels,
        onDisconnect = onBack,
        onOpenThemePicker = onOpenThemePicker,
        warnings =
            if (state != null) listOf(stringResource(Res.string.warning_cached_data))
            else emptyList(),
    )
}
