package ee.schimke.meshcore.components.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import ee.schimke.meshcore.components.ui.theme.MeshcoreTheme

@Composable
private fun appSettingsPreview(developerMode: Boolean) {
  MeshcoreTheme {
    AppSettingsBody(
      versionName = "1.4.0",
      developerMode = developerMode,
      onDeveloperModeChange = {},
      onOpenLicenses = {},
      onBack = {},
      initialDeveloperExpanded = developerMode,
    )
  }
}

/** App Settings / About as a normal user sees it: no Developer section. */
@Preview(showBackground = true, showSystemUi = true, device = Devices.PIXEL_7, name = "Light")
@Preview(
  showBackground = true,
  showSystemUi = true,
  device = Devices.PIXEL_7,
  uiMode = 0x20,
  name = "Dark",
)
@Composable
fun AppSettingsPreview() = appSettingsPreview(developerMode = false)

/** App Settings after the version row has been tapped seven times. */
@Preview(showBackground = true, showSystemUi = true, device = Devices.PIXEL_7, name = "Developer")
@Composable
fun AppSettingsDeveloperPreview() = appSettingsPreview(developerMode = true)
