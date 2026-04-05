package ee.schimke.meshcore.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
import ee.schimke.meshcore.app.ui.DeviceScreen
import ee.schimke.meshcore.app.ui.ScannerScreen
import ee.schimke.meshcore.app.ui.theme.MeshcoreTheme
import ee.schimke.meshcore.app.ui.theme.ThemePickerDialog
import ee.schimke.meshcore.app.ui.theme.ThemeSettings
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// Type-safe destination keys for androidx.navigation3. Each NavKey is
// an @Serializable object/class so the back stack can survive process
// death via rememberNavBackStack's saveable bundling.
@Serializable private data object ScannerRoute : NavKey
@Serializable private data object DeviceRoute : NavKey

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
            val backStack = rememberNavBackStack(ScannerRoute)
            NavDisplay(
                backStack = backStack,
                onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                entryProvider = entryProvider {
                    entry<ScannerRoute> {
                        ScannerScreen(
                            onConnect = { if (backStack.lastOrNull() !is DeviceRoute) backStack.add(DeviceRoute) },
                            onOpenThemePicker = { pickerVisible = true },
                        )
                    }
                    entry<DeviceRoute> {
                        DeviceScreen(
                            onDisconnected = {
                                if (backStack.lastOrNull() is DeviceRoute) backStack.removeLastOrNull()
                            },
                            onOpenThemePicker = { pickerVisible = true },
                        )
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
