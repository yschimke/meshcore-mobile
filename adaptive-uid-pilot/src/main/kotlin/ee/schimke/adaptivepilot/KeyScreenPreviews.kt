package ee.schimke.adaptivepilot

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "scanner Phone Light", widthDp = 412, heightDp = 720)
@Preview(name = "scanner Tablet Light", widthDp = 840, heightDp = 720)
@Composable
fun ScannerLightPreview() = KeyScreen("scanner", dark = false)

@Preview(name = "scanner Phone Detail Light", widthDp = 412, heightDp = 720)
@Composable
fun ScannerDetailLightPreview() = KeyScreen("scanner", dark = false, initialDetail = true)

@Preview(name = "scanner Tablet Secondary Light", widthDp = 840, heightDp = 720)
@Composable
fun ScannerSecondaryLightPreview() =
  KeyScreen("scanner", dark = false, initialDetail = true, initialSelectedIndex = 1)

@Preview(name = "scanner Phone Dark", widthDp = 412, heightDp = 720)
@Preview(name = "scanner Tablet Dark", widthDp = 840, heightDp = 720)
@Composable
fun ScannerDarkPreview() = KeyScreen("scanner", dark = true)

@Preview(name = "scanner Phone Detail Dark", widthDp = 412, heightDp = 720)
@Composable
fun ScannerDetailDarkPreview() = KeyScreen("scanner", dark = true, initialDetail = true)

@Preview(name = "scanner Tablet Secondary Dark", widthDp = 840, heightDp = 720)
@Composable
fun ScannerSecondaryDarkPreview() =
  KeyScreen("scanner", dark = true, initialDetail = true, initialSelectedIndex = 1)

@Preview(name = "device Phone Light", widthDp = 412, heightDp = 720)
@Preview(name = "device Tablet Light", widthDp = 840, heightDp = 720)
@Composable
fun DeviceLightPreview() = KeyScreen("device", dark = false)

@Preview(name = "device Phone Detail Light", widthDp = 412, heightDp = 720)
@Composable
fun DeviceDetailLightPreview() = KeyScreen("device", dark = false, initialDetail = true)

@Preview(name = "device Tablet Secondary Light", widthDp = 840, heightDp = 720)
@Composable
fun DeviceSecondaryLightPreview() =
  KeyScreen("device", dark = false, initialDetail = true, initialSelectedIndex = 1)

@Preview(name = "device Phone Dark", widthDp = 412, heightDp = 720)
@Preview(name = "device Tablet Dark", widthDp = 840, heightDp = 720)
@Composable
fun DeviceDarkPreview() = KeyScreen("device", dark = true)

@Preview(name = "device Phone Detail Dark", widthDp = 412, heightDp = 720)
@Composable
fun DeviceDetailDarkPreview() = KeyScreen("device", dark = true, initialDetail = true)

@Preview(name = "device Tablet Secondary Dark", widthDp = 840, heightDp = 720)
@Composable
fun DeviceSecondaryDarkPreview() =
  KeyScreen("device", dark = true, initialDetail = true, initialSelectedIndex = 1)

@Preview(name = "channels Phone Light", widthDp = 412, heightDp = 720)
@Preview(name = "channels Tablet Light", widthDp = 840, heightDp = 720)
@Composable
fun ChannelsLightPreview() = KeyScreen("channels", dark = false)

@Preview(name = "channels Phone Detail Light", widthDp = 412, heightDp = 720)
@Composable
fun ChannelsDetailLightPreview() = KeyScreen("channels", dark = false, initialDetail = true)

@Preview(name = "channels Tablet Secondary Light", widthDp = 840, heightDp = 720)
@Composable
fun ChannelsSecondaryLightPreview() =
  KeyScreen("channels", dark = false, initialDetail = true, initialSelectedIndex = 1)

@Preview(name = "channels Phone Dark", widthDp = 412, heightDp = 720)
@Preview(name = "channels Tablet Dark", widthDp = 840, heightDp = 720)
@Composable
fun ChannelsDarkPreview() = KeyScreen("channels", dark = true)

@Preview(name = "channels Phone Detail Dark", widthDp = 412, heightDp = 720)
@Composable
fun ChannelsDetailDarkPreview() = KeyScreen("channels", dark = true, initialDetail = true)

@Preview(name = "channels Tablet Secondary Dark", widthDp = 840, heightDp = 720)
@Composable
fun ChannelsSecondaryDarkPreview() =
  KeyScreen("channels", dark = true, initialDetail = true, initialSelectedIndex = 1)

@Preview(name = "commands Phone Light", widthDp = 412, heightDp = 720)
@Preview(name = "commands Tablet Light", widthDp = 840, heightDp = 720)
@Composable
fun CommandsLightPreview() = KeyScreen("commands", dark = false)

@Preview(name = "commands Phone Detail Light", widthDp = 412, heightDp = 720)
@Composable
fun CommandsDetailLightPreview() = KeyScreen("commands", dark = false, initialDetail = true)

@Preview(name = "commands Tablet Secondary Light", widthDp = 840, heightDp = 720)
@Composable
fun CommandsSecondaryLightPreview() =
  KeyScreen("commands", dark = false, initialDetail = true, initialSelectedIndex = 1)

@Preview(name = "commands Phone Dark", widthDp = 412, heightDp = 720)
@Preview(name = "commands Tablet Dark", widthDp = 840, heightDp = 720)
@Composable
fun CommandsDarkPreview() = KeyScreen("commands", dark = true)

@Preview(name = "commands Phone Detail Dark", widthDp = 412, heightDp = 720)
@Composable
fun CommandsDetailDarkPreview() = KeyScreen("commands", dark = true, initialDetail = true)

@Preview(name = "commands Tablet Secondary Dark", widthDp = 840, heightDp = 720)
@Composable
fun CommandsSecondaryDarkPreview() =
  KeyScreen("commands", dark = true, initialDetail = true, initialSelectedIndex = 1)

@Preview(name = "settings Phone Light", widthDp = 412, heightDp = 720)
@Preview(name = "settings Tablet Light", widthDp = 840, heightDp = 720)
@Composable
fun SettingsLightPreview() = KeyScreen("settings", dark = false)

@Preview(name = "settings Phone Detail Light", widthDp = 412, heightDp = 720)
@Composable
fun SettingsDetailLightPreview() = KeyScreen("settings", dark = false, initialDetail = true)

@Preview(name = "settings Tablet Secondary Light", widthDp = 840, heightDp = 720)
@Composable
fun SettingsSecondaryLightPreview() =
  KeyScreen("settings", dark = false, initialDetail = true, initialSelectedIndex = 1)

@Preview(name = "settings Phone Dark", widthDp = 412, heightDp = 720)
@Preview(name = "settings Tablet Dark", widthDp = 840, heightDp = 720)
@Composable
fun SettingsDarkPreview() = KeyScreen("settings", dark = true)

@Preview(name = "settings Phone Detail Dark", widthDp = 412, heightDp = 720)
@Composable
fun SettingsDetailDarkPreview() = KeyScreen("settings", dark = true, initialDetail = true)

@Preview(name = "settings Tablet Secondary Dark", widthDp = 840, heightDp = 720)
@Composable
fun SettingsSecondaryDarkPreview() =
  KeyScreen("settings", dark = true, initialDetail = true, initialSelectedIndex = 1)

@Preview(name = "cached Phone Light", widthDp = 412, heightDp = 720)
@Preview(name = "cached Tablet Light", widthDp = 840, heightDp = 720)
@Composable
fun CachedLightPreview() = KeyScreen("cached", dark = false)

@Preview(name = "cached Phone Detail Light", widthDp = 412, heightDp = 720)
@Composable
fun CachedDetailLightPreview() = KeyScreen("cached", dark = false, initialDetail = true)

@Preview(name = "cached Tablet Secondary Light", widthDp = 840, heightDp = 720)
@Composable
fun CachedSecondaryLightPreview() =
  KeyScreen("cached", dark = false, initialDetail = true, initialSelectedIndex = 1)

@Preview(name = "cached Phone Dark", widthDp = 412, heightDp = 720)
@Preview(name = "cached Tablet Dark", widthDp = 840, heightDp = 720)
@Composable
fun CachedDarkPreview() = KeyScreen("cached", dark = true)

@Preview(name = "cached Phone Detail Dark", widthDp = 412, heightDp = 720)
@Composable
fun CachedDetailDarkPreview() = KeyScreen("cached", dark = true, initialDetail = true)

@Preview(name = "cached Tablet Secondary Dark", widthDp = 840, heightDp = 720)
@Composable
fun CachedSecondaryDarkPreview() =
  KeyScreen("cached", dark = true, initialDetail = true, initialSelectedIndex = 1)

@Preview(name = "TCP failure Phone Light", widthDp = 412, heightDp = 720)
@Preview(name = "TCP failure Tablet Light", widthDp = 840, heightDp = 720)
@Composable
fun ScannerFailureLightPreview() =
  KeyScreen("scanner", initialDetail = true, initialSelectedIndex = 1, failure = true)

@Preview(name = "TCP failure Phone Dark", widthDp = 412, heightDp = 720)
@Preview(name = "TCP failure Tablet Dark", widthDp = 840, heightDp = 720)
@Composable
fun ScannerFailureDarkPreview() =
  KeyScreen("scanner", dark = true, initialDetail = true, initialSelectedIndex = 1, failure = true)
