package ee.schimke.meshcore.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import ee.schimke.meshcore.app.ui.CachedDeviceScreen
import ee.schimke.meshcore.app.ui.ChannelChatScreen
import ee.schimke.meshcore.app.ui.ContactChatScreen
import ee.schimke.meshcore.app.ui.DeviceScreen
import ee.schimke.meshcore.app.ui.ScannerScreen
import ee.schimke.meshcore.app.ui.theme.MeshcoreTheme
import ee.schimke.meshcore.app.ui.theme.ThemePickerDialog
import ee.schimke.meshcore.app.ui.theme.ThemeSettings
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// Type-safe destination keys for androidx.navigation3. Each NavKey is
// an @Serializable object/class so the back stack can survive process
// death via rememberNavBackStack's saveable bundling.
@Serializable private data object ScannerRoute : NavKey
@Serializable private data object DeviceRoute : NavKey
@Serializable private data class ContactRoute(val publicKeyHex: String) : NavKey
@Serializable private data class ChannelRoute(val channelIndex: Int) : NavKey
@Serializable private data class CachedDeviceRoute(val deviceId: String) : NavKey
@Serializable private data object LicensesRoute : NavKey

class MainActivity : ComponentActivity() {
    // Runtime BLE permissions are requested from the BLE tab on demand, not here;
    // that way users can deny without crashing the app on launch.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MeshcoreAppUi() }
    }
}

@Composable
private fun MeshcoreAppUi() {
    val app = MeshcoreApp.get()
    val themeSettings by app.themePreferences.settings.collectAsState(initial = ThemeSettings())
    val scope = rememberCoroutineScope()
    var pickerVisible by remember { mutableStateOf(false) }

    MeshcoreTheme(settings = themeSettings) {
        Surface {
            val backStack = rememberNavBackStack(DeviceRoute)

            // If no favourite device on launch, show the scanner immediately
            LaunchedEffect(Unit) {
                val fav = app.repository.observeFavorite().first()
                if (fav == null) {
                    backStack.add(ScannerRoute)
                }
            }

            NavDisplay(
                backStack = backStack,
                onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                entryProvider = entryProvider {
                    entry<DeviceRoute> {
                        DeviceScreen(
                            onDisconnected = {
                                // Push scanner on top so user can reconnect
                                if (backStack.lastOrNull() !is ScannerRoute) backStack.add(ScannerRoute)
                            },
                            onOpenThemePicker = { pickerVisible = true },
                            onNavigateToContact = { contact ->
                                backStack.add(ContactRoute(contact.publicKey.toHex()))
                            },
                            onNavigateToChannel = { channel ->
                                backStack.add(ChannelRoute(channel.index))
                            },
                        )
                    }
                    entry<ScannerRoute> {
                        ScannerScreen(
                            onConnect = {
                                // Pop back to device screen
                                while (backStack.lastOrNull() is ScannerRoute) backStack.removeLastOrNull()
                            },
                            onOpenThemePicker = { pickerVisible = true },
                            onViewCachedDevice = { deviceId -> backStack.add(CachedDeviceRoute(deviceId)) },
                            onOpenLicenses = { backStack.add(LicensesRoute) },
                        )
                    }
                    entry<ContactRoute> { route ->
                        ContactChatScreen(
                            publicKeyHex = route.publicKeyHex,
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<ChannelRoute> { route ->
                        ChannelChatScreen(
                            channelIndex = route.channelIndex,
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<CachedDeviceRoute> { route ->
                        CachedDeviceScreen(
                            deviceId = route.deviceId,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenThemePicker = { pickerVisible = true },
                        )
                    }
                    entry<LicensesRoute> {
                        LibrariesContainer()
                    }
                },
            )
            if (pickerVisible) {
                ThemePickerDialog(
                    current = themeSettings,
                    onModeSelect = { mode ->
                        scope.launch { app.themePreferences.setThemeMode(mode) }
                    },
                    onPaletteSelect = { palette ->
                        scope.launch { app.themePreferences.setThemePalette(palette) }
                    },
                    onDismiss = { pickerVisible = false },
                )
            }
        }
    }
}
