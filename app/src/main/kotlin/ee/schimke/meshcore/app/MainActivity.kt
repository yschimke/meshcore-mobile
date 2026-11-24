package ee.schimke.meshcore.app

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.android.ActivityKey
import ee.schimke.meshcore.app.di.AppGraph
import ee.schimke.meshcore.app.di.LocalAppGraph
import ee.schimke.meshcore.app.ui.AppSettingsScreen
import ee.schimke.meshcore.app.ui.CachedDeviceScreen
import ee.schimke.meshcore.app.ui.ChannelChatScreen
import ee.schimke.meshcore.app.ui.CommandsScreen
import ee.schimke.meshcore.app.ui.ContactChatScreen
import ee.schimke.meshcore.app.ui.DeviceScreen
import ee.schimke.meshcore.app.ui.DeviceSettingsScreen
import ee.schimke.meshcore.app.ui.ScannerScreen
import ee.schimke.meshcore.app.ui.theme.MeshcoreTheme
import ee.schimke.meshcore.components.generated.resources.Res
import ee.schimke.meshcore.components.generated.resources.app_settings_licenses
import ee.schimke.meshcore.components.generated.resources.cd_back
import ee.schimke.meshcore.components.ui.icons.MeshIcons
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource

@ContributesIntoMap(AppScope::class, binding<Activity>())
@ActivityKey
@Inject
class MainActivity(
  private val appGraph: AppGraph,
) : ComponentActivity() {
  // Runtime BLE permissions are requested from the BLE tab on demand, not here;
  // that way users can deny without crashing the app on launch.
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { CompositionLocalProvider(LocalAppGraph provides appGraph) { MeshcoreAppUi() } }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MeshcoreAppUi() {
  val app = LocalAppGraph.current
  val developerMode by
    app.themePreferences.developerMode.collectAsStateWithLifecycle(initialValue = false)

  MeshcoreTheme {
    Surface {
      val backStack = rememberNavBackStack(DeviceRoute)
      val motionScheme = MaterialTheme.motionScheme

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
        transitionSpec = {
          slideInHorizontally(motionScheme.defaultSpatialSpec()) { it / 6 } +
            fadeIn(motionScheme.defaultEffectsSpec()) togetherWith
            slideOutHorizontally(motionScheme.defaultSpatialSpec()) { -it / 12 } +
              fadeOut(motionScheme.fastEffectsSpec())
        },
        popTransitionSpec = {
          slideInHorizontally(motionScheme.defaultSpatialSpec()) { -it / 12 } +
            fadeIn(motionScheme.defaultEffectsSpec()) togetherWith
            slideOutHorizontally(motionScheme.defaultSpatialSpec()) { it / 6 } +
              fadeOut(motionScheme.fastEffectsSpec())
        },
        predictivePopTransitionSpec = {
          slideInHorizontally(motionScheme.defaultSpatialSpec()) { -it / 12 } +
            fadeIn(motionScheme.defaultEffectsSpec()) togetherWith
            scaleOut(motionScheme.defaultSpatialSpec(), targetScale = 0.92f) +
              fadeOut(motionScheme.fastEffectsSpec())
        },
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
              Scaffold(
                topBar = {
                  TopAppBar(
                    title = { Text(stringResource(Res.string.app_settings_licenses)) },
                    navigationIcon = {
                      IconButton(onClick = { backStack.removeLastOrNull() }) {
                        Icon(MeshIcons.ArrowBack, stringResource(Res.string.cd_back))
                      }
                    },
                    colors =
                      TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                      ),
                  )
                },
              ) { padding ->
                LibrariesContainer(
                  libraries = libraries,
                  modifier = Modifier.fillMaxSize().padding(padding),
                )
              }
            }
          },
      )
    }
  }
}
