package ee.schimke.meshcore.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import ee.schimke.meshcore.app.di.LocalAppGraph
import ee.schimke.meshcore.app.di.appGraph
import ee.schimke.meshcore.app.ui.AppSettingsScreen
import ee.schimke.meshcore.app.ui.CachedDeviceScreen
import ee.schimke.meshcore.app.ui.ChannelChatScreen
import ee.schimke.meshcore.app.ui.CommandsScreen
import ee.schimke.meshcore.app.ui.ContactChatScreen
import ee.schimke.meshcore.app.ui.DeviceScreen
import ee.schimke.meshcore.app.ui.DeviceSettingsScreen
import ee.schimke.meshcore.app.ui.ScannerScreen
import ee.schimke.meshcore.app.ui.theme.MeshcoreTheme
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
  // Runtime BLE permissions are requested from the BLE tab on demand, not here;
  // that way users can deny without crashing the app on launch.
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val graph = appGraph()
    setContent { CompositionLocalProvider(LocalAppGraph provides graph) { MeshcoreAppUi() } }
  }
}

@Composable
private fun MeshcoreAppUi() {
  val app = LocalAppGraph.current
  val developerMode by app.themePreferences.developerMode.collectAsState(initial = false)

  MeshcoreTheme {
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
        // On large screens and foldables the Device lists and the open conversation show side by
        // side; compact windows keep single-pane navigation. See ConversationListDetail.kt.
        sceneStrategies = listOf(rememberConversationSceneStrategy()),
        entryProvider =
          entryProvider {
            entry<DeviceRoute>(metadata = DeviceListPaneMetadata) {
              DeviceScreen(
                onDisconnected = {
                  // Push scanner on top so user can reconnect
                  if (backStack.lastOrNull() !is ScannerRoute) backStack.add(ScannerRoute)
                },
                developerMode = developerMode,
                onNavigateToContact = { contact ->
                  backStack.openConversation(ContactRoute(contact.publicKey.toHex()))
                },
                onNavigateToChannel = { channel ->
                  backStack.openConversation(ChannelRoute(channel.index))
                },
                onNavigateToCommands = { channel -> backStack.add(CommandsRoute(channel.index)) },
                onNavigateToSettings = { channel ->
                  backStack.add(DeviceSettingsRoute(channel.index))
                },
              )
            }
            entry<ScannerRoute> {
              ScannerScreen(
                onConnect = {
                  // Pop back to device screen
                  while (backStack.lastOrNull() is ScannerRoute) backStack.removeLastOrNull()
                },
                developerMode = developerMode,
                onViewCachedDevice = { deviceId -> backStack.add(CachedDeviceRoute(deviceId)) },
                onOpenSettings = { backStack.add(AppSettingsRoute) },
              )
            }
            entry<AppSettingsRoute> {
              AppSettingsScreen(
                onBack = { backStack.removeLastOrNull() },
                onOpenLicenses = { backStack.add(LicensesRoute) },
              )
            }
            entry<ContactRoute>(metadata = ConversationPaneMetadata) { route ->
              ContactChatScreen(
                publicKeyHex = route.publicKeyHex,
                onBack = { backStack.removeLastOrNull() },
              )
            }
            entry<ChannelRoute>(metadata = ConversationPaneMetadata) { route ->
              ChannelChatScreen(
                channelIndex = route.channelIndex,
                onBack = { backStack.removeLastOrNull() },
              )
            }
            entry<CommandsRoute> { route ->
              CommandsScreen(
                channelIndex = route.channelIndex,
                onBack = { backStack.removeLastOrNull() },
              )
            }
            entry<DeviceSettingsRoute> { route ->
              DeviceSettingsScreen(
                channelIndex = route.channelIndex,
                onBack = { backStack.removeLastOrNull() },
              )
            }
            entry<CachedDeviceRoute> { route ->
              CachedDeviceScreen(
                deviceId = route.deviceId,
                onBack = { backStack.removeLastOrNull() },
              )
            }
            entry<LicensesRoute> {
              val libraries by produceLibraries()
              LibrariesContainer(libraries)
            }
          },
      )
    }
  }
}
