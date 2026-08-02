package ee.schimke.meshcore.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.PreviewWrapperProvider
import ee.schimke.composeai.preview.ThemeCatalog

/**
 * The complete set of visual themes offered by [ThemeSettings].
 *
 * System mode is deliberately not a separate entry: it resolves to either light or dark and would
 * duplicate one of these sheets. Declaring the four concrete palette x mode combinations lets the
 * preview catalog render their resolved Material 3 tokens and lets its theme selector re-render any
 * app preview under the selected provider.
 */
@Composable
private fun MeshcoreCatalogTheme(
  darkTheme: Boolean,
  palette: ThemePalette,
  content: @Composable () -> Unit,
) {
  MeshcoreTheme(darkTheme = darkTheme, palette = palette) {
    CompositionLocalProvider(LocalThemeCatalogOverride provides true, content = content)
  }
}

@ThemeCatalog(name = "MeshCore Light", group = "MeshCore")
class MeshcoreAppLightThemeCatalog : PreviewWrapperProvider {
  @Composable
  override fun Wrap(content: @Composable () -> Unit) =
    MeshcoreCatalogTheme(darkTheme = false, palette = ThemePalette.Meshcore, content = content)
}

@ThemeCatalog(name = "MeshCore Dark", group = "MeshCore")
class MeshcoreAppDarkThemeCatalog : PreviewWrapperProvider {
  @Composable
  override fun Wrap(content: @Composable () -> Unit) =
    MeshcoreCatalogTheme(darkTheme = true, palette = ThemePalette.Meshcore, content = content)
}

@ThemeCatalog(name = "Dynamic Light", group = "MeshCore")
class MeshcoreDynamicLightThemeCatalog : PreviewWrapperProvider {
  @Composable
  override fun Wrap(content: @Composable () -> Unit) =
    MeshcoreCatalogTheme(darkTheme = false, palette = ThemePalette.Dynamic, content = content)
}

@ThemeCatalog(name = "Dynamic Dark", group = "MeshCore")
class MeshcoreDynamicDarkThemeCatalog : PreviewWrapperProvider {
  @Composable
  override fun Wrap(content: @Composable () -> Unit) =
    MeshcoreCatalogTheme(darkTheme = true, palette = ThemePalette.Dynamic, content = content)
}
