package ee.schimke.meshcore.components.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import ee.schimke.meshcore.components.ui.theme.MeshcoreTheme

// Design-parity preview subjects for the Device Settings screen (the discovered
// "Ready" state with the buzzer toggle), on the CMP desktop render path. See
// docs/design-parity.md.

private val readyState =
  DeviceSettingsUiState.Ready(hasBuzzer = true, buzzerMode = "rtttl", buzzerLoading = false)

@Composable
private fun settingsPreview(dark: Boolean) {
  MeshcoreTheme(darkTheme = dark) {
    DeviceSettingsBody(state = readyState, onToggleBuzzer = {}, onBack = {})
  }
}

/**
 * Design-parity subject: Device Settings, discovered (light). Reference
 * `design/DeviceSettings.light.html`.
 */
@Preview(
  showBackground = true,
  showSystemUi = true,
  device = Devices.PIXEL_7,
  name = "Device settings",
)
@Composable
fun DeviceSettingsPreview() = settingsPreview(dark = false)

/** Device Settings, discovered (dark). Reference `design/DeviceSettings.dark.html`. */
@Preview(
  showBackground = true,
  showSystemUi = true,
  device = Devices.PIXEL_7,
  uiMode = 0x20,
  name = "Device settings — dark",
)
@Composable
fun DeviceSettingsDarkPreview() = settingsPreview(dark = true)
