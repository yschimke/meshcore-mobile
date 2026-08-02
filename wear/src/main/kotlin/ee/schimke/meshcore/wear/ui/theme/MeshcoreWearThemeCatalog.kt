package ee.schimke.meshcore.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.PreviewWrapperProvider
import ee.schimke.composeai.preview.WearThemeCatalog

/**
 * The watch has one always-dark theme. [WearThemeCatalog] selects the Wear Material 3 specimen, and
 * also makes this provider available as a live override for every preview in the Wear module.
 */
@WearThemeCatalog(name = "MeshCore", group = "Wear")
class MeshcoreWearThemeCatalog : PreviewWrapperProvider {
  @Composable
  override fun Wrap(content: @Composable () -> Unit) =
    MeshcoreWearTheme {
      CompositionLocalProvider(LocalWearThemeCatalogOverride provides true, content = content)
    }
}
