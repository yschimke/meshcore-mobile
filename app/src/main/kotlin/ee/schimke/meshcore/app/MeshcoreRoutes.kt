package ee.schimke.meshcore.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable internal data object ScannerRoute : NavKey

@Serializable internal data object DeviceRoute : NavKey

@Serializable internal data class ContactRoute(val publicKeyHex: String) : NavKey

@Serializable internal data class ChannelRoute(val channelIndex: Int) : NavKey

@Serializable internal data class CommandsRoute(val channelIndex: Int) : NavKey

@Serializable internal data class DeviceSettingsRoute(val channelIndex: Int) : NavKey

@Serializable internal data class CachedDeviceRoute(val deviceId: String) : NavKey

@Serializable internal data object LicensesRoute : NavKey
