package ee.schimke.meshcore.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ee.schimke.meshcore.app.MeshcoreApp
import ee.schimke.meshcore.data.repository.toBattery
import ee.schimke.meshcore.data.repository.toRadio
import ee.schimke.meshcore.data.repository.toSelfInfo

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
    val repository = MeshcoreApp.get().repository
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
        onRefreshContacts = {},
        onDisconnect = onBack,
        onOpenThemePicker = onOpenThemePicker,
        warnings = if (state != null) listOf("Cached data — connect to refresh") else emptyList(),
    )
}
