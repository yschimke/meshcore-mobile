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

/**
 * German (`de`) locale variant of [DeviceSettingsPreview] — the `catalog.spec.json`
 * `Settings/Ready` `props:{locale:"de"}` variant. Renders the same screen with a `localeTag`
 * override so `DeviceSettingsBody`'s `stringResource(...)` chrome resolves German copy; requires
 * the desktop render-engine locale fix (compose-preview plugin ≥ 0.17.1). Sender/device data stays
 * literal.
 */
@Preview(
  showBackground = true,
  showSystemUi = true,
  device = Devices.PIXEL_7,
  locale = "de",
  name = "Device settings — German",
)
@Composable
fun DeviceSettingsGermanPreview() = settingsPreview(dark = false)
