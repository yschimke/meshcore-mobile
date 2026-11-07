package ee.schimke.meshcore.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.PreviewWrapperProvider
import ee.schimke.composeai.preview.ThemeCatalog

/**
 * The app's visual themes: the single MeshCore brand palette in light and dark. Declaring both lets
 * the preview catalog render their resolved Material 3 tokens and lets its theme selector re-render
 * any app preview under the selected provider.
 */
@Composable
private fun MeshcoreCatalogTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
  MeshcoreTheme(darkTheme = darkTheme) {
    CompositionLocalProvider(LocalThemeCatalogOverride provides true, content = content)
  }
}

@ThemeCatalog(name = "MeshCore Light", group = "MeshCore")
class MeshcoreAppLightThemeCatalog : PreviewWrapperProvider {
  @Composable
  override fun Wrap(content: @Composable () -> Unit) =
    MeshcoreCatalogTheme(darkTheme = false, content = content)
}

@ThemeCatalog(name = "MeshCore Dark", group = "MeshCore")
class MeshcoreAppDarkThemeCatalog : PreviewWrapperProvider {
  @Composable
  override fun Wrap(content: @Composable () -> Unit) =
    MeshcoreCatalogTheme(darkTheme = true, content = content)
}
