package ee.schimke.meshcore.components.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** True while a theme-catalog provider already owns the preview's outer theme. */
internal val LocalThemeCatalogOverride = staticCompositionLocalOf { false }

/**
 * Multiplatform MeshCore theme for the shared composables (and the design-parity desktop render).
 * Pure: the MeshCore branded palette, typography and shapes, with no platform chrome. The app's own
 * `MeshcoreTheme` wraps this concern with Android-only dynamic color + edge-to-edge handling.
 */
@Composable
fun MeshcoreTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  if (LocalThemeCatalogOverride.current) {
    content()
    return
  }

  MaterialTheme(
    colorScheme = if (darkTheme) MeshcoreDarkColors else MeshcoreLightColors,
    typography = MeshcoreBrandedTypography,
    shapes = MeshcoreShapes,
    content = content,
  )
}
