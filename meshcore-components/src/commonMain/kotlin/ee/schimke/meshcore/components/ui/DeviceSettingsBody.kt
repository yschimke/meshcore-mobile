package ee.schimke.meshcore.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ee.schimke.meshcore.components.generated.resources.Res
import ee.schimke.meshcore.components.generated.resources.cd_back
import ee.schimke.meshcore.components.generated.resources.label_device_settings
import ee.schimke.meshcore.components.generated.resources.settings_buzzer
import ee.schimke.meshcore.components.generated.resources.settings_buzzer_off
import ee.schimke.meshcore.components.generated.resources.settings_buzzer_on
import ee.schimke.meshcore.components.generated.resources.settings_buzzer_unknown
import ee.schimke.meshcore.components.generated.resources.settings_buzzer_updating
import ee.schimke.meshcore.components.generated.resources.settings_discovering
import ee.schimke.meshcore.components.generated.resources.settings_none
import ee.schimke.meshcore.components.ui.icons.MeshIcons
import org.jetbrains.compose.resources.stringResource

/**
 * Render state for the device settings screen. The stateful `DeviceSettingsScreen` wrapper in
 * `:app` drives the `/help` + `/buz` discovery and maps it to this; the stateless
 * [DeviceSettingsBody] renders it (the design-parity subject).
 */
sealed interface DeviceSettingsUiState {
  /** Probing the device's capabilities (`/help`). */
  data object Discovering : DeviceSettingsUiState

  /** Discovery failed (e.g. no response). */
  data class Error(val message: String) : DeviceSettingsUiState

  /**
   * Discovery done. [buzzerMode] is `null` (unknown), `"off"`, or a mode like `"rtttl"`;
   * [buzzerLoading] is true while a toggle is in flight.
   */
  data class Ready(
    val hasBuzzer: Boolean,
    val buzzerMode: String? = null,
    val buzzerLoading: Boolean = false,
  ) : DeviceSettingsUiState
}

/**
 * Stateless device-settings screen body: a top bar plus the discovery state's content (a spinner
 * while discovering, an error message, or the settings rows — currently the buzzer toggle). The
 * `:app` wrapper owns the command transport.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSettingsBody(
  state: DeviceSettingsUiState,
  onToggleBuzzer: (wantOn: Boolean) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = {
          Text(
            stringResource(Res.string.label_device_settings),
            style = MaterialTheme.typography.titleMedium,
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(MeshIcons.ArrowBack, stringResource(Res.string.cd_back))
          }
        },
        actions = {
          Icon(
            MeshIcons.Settings,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
          )
        },
        colors =
          TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
      )
    },
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
      when (state) {
        is DeviceSettingsUiState.Discovering ->
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(12.dp))
            Text(
              stringResource(Res.string.settings_discovering),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        is DeviceSettingsUiState.Error ->
          Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(vertical = 24.dp),
          )
        is DeviceSettingsUiState.Ready ->
          if (!state.hasBuzzer) {
            Text(
              text = stringResource(Res.string.settings_none),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(vertical = 24.dp),
            )
          } else {
            Spacer(Modifier.size(8.dp))
            BuzzerRow(
              mode = state.buzzerMode,
              loading = state.buzzerLoading,
              onToggle = onToggleBuzzer,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
          }
      }
    }
  }
}

@Composable
private fun BuzzerRow(mode: String?, loading: Boolean, onToggle: (wantOn: Boolean) -> Unit) {
  val isOn = mode != null && mode != "off"

  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(Res.string.settings_buzzer),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text =
          when {
            loading -> stringResource(Res.string.settings_buzzer_updating)
            mode == null -> stringResource(Res.string.settings_buzzer_unknown)
            mode == "off" -> stringResource(Res.string.settings_buzzer_off)
            else -> stringResource(Res.string.settings_buzzer_on, mode)
          },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (loading) {
      CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
      Spacer(Modifier.size(12.dp))
    }
    Switch(checked = isOn, onCheckedChange = { onToggle(it) }, enabled = !loading && mode != null)
  }
}
