package ee.schimke.meshcore.components.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewWrapperProvider
import ee.schimke.composeai.preview.ThemeCatalog

/**
 * `@ThemeCatalog` providers — MeshCore's **alternative themes**, declared so the compose-preview
 * plugin renders a specimen sheet per theme (the theme-scoped sibling of `@ColorCatalog`). No
 * `@Preview` is written: discovery finds each annotated [PreviewWrapperProvider] and composes its
 * `Wrap` around a canned Material 3 role + type-scale grid, so each sheet shows that theme's live
 * resolved `colorScheme` / `typography` / shapes.
 *
 * MeshCore has a single brand palette across two modes, so the matrix is `MeshCore × {Light, Dark}`
 * — the N-ary generalization of a single `uiMode` light/dark toggle. Both wrap the shared
 * [MeshcoreTheme], so the sheets reflect exactly the palette the app and the design-parity desktop
 * render use. They live in `commonMain` (the tokens do too), which is why `preview-annotations` had
 * to ship as a Kotlin Multiplatform artifact.
 */
@ThemeCatalog(name = "MeshCore Light", group = "MeshCore")
class MeshcoreLightThemeCatalog : PreviewWrapperProvider {
  @Composable
  override fun Wrap(content: @Composable () -> Unit) =
    MeshcoreTheme(darkTheme = false) { content() }
}

@ThemeCatalog(name = "MeshCore Dark", group = "MeshCore")
class MeshcoreDarkThemeCatalog : PreviewWrapperProvider {
  @Composable
  override fun Wrap(content: @Composable () -> Unit) = MeshcoreTheme(darkTheme = true) { content() }
}
