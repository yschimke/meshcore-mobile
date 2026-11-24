package ee.schimke.meshcore.wear

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.ViewModelProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.android.ActivityKey
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import ee.schimke.meshcore.wear.ui.WearNavigation
import ee.schimke.meshcore.wear.ui.theme.MeshcoreWearTheme

@ContributesIntoMap(AppScope::class, binding<Activity>())
@ActivityKey
@Inject
class WearMainActivity(
  private val metroViewModelFactory: MetroViewModelFactory,
) : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      CompositionLocalProvider(LocalMetroViewModelFactory provides metroViewModelFactory) {
        MeshcoreWearTheme { WearNavigation() }
      }
    }
  }

  override val defaultViewModelProviderFactory: ViewModelProvider.Factory
    get() = metroViewModelFactory
}
