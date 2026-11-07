package ee.schimke.meshcore.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import ee.schimke.meshcore.app.di.LocalAppGraph
import ee.schimke.meshcore.components.ui.AppSettingsBody
import kotlinx.coroutines.launch

/** Stateful app Settings / About screen: the installed version, licenses and developer options. */
@Composable
fun AppSettingsScreen(onBack: () -> Unit, onOpenLicenses: () -> Unit) {
  val prefs = LocalAppGraph.current.themePreferences
  val developerMode by prefs.developerMode.collectAsState(initial = false)
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val versionName = remember {
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
      .getOrNull()
      .orEmpty()
  }
  AppSettingsBody(
    versionName = versionName,
    developerMode = developerMode,
    onDeveloperModeChange = { enabled -> scope.launch { prefs.setDeveloperMode(enabled) } },
    onOpenLicenses = onOpenLicenses,
    onBack = onBack,
  )
}
