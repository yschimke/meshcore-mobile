package ee.schimke.meshcore.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object ScannerRoute : NavKey

@Serializable data object DeviceRoute : NavKey

@Serializable data class ContactRoute(val publicKeyHex: String) : NavKey

@Serializable data class ChannelRoute(val channelIndex: Int) : NavKey

@Serializable data class CommandsRoute(val channelIndex: Int) : NavKey

@Serializable data class DeviceSettingsRoute(val channelIndex: Int) : NavKey

@Serializable data class CachedDeviceRoute(val deviceId: String) : NavKey

@Serializable data object LicensesRoute : NavKey
