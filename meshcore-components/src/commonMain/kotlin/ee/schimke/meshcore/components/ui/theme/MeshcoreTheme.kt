package ee.schimke.meshcore.components.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** True while a theme-catalog provider already owns the preview's outer theme. */
internal val LocalThemeCatalogOverride = staticCompositionLocalOf { false }

/**
 * The one MeshCore theme: the brand palette ([MeshcoreLightColors] / [MeshcoreDarkColors])
 * following the system light/dark setting, with the branded typography and shapes, wrapped in
 * Material 3 Expressive (expressive motion scheme). Pure: no platform chrome. The Android app's own
 * `MeshcoreTheme` adds the edge-to-edge system-bar handling on top of this.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MeshcoreTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  if (LocalThemeCatalogOverride.current) {
    content()
    return
  }

  MaterialExpressiveTheme(
    colorScheme = if (darkTheme) MeshcoreDarkColors else MeshcoreLightColors,
    motionScheme = MotionScheme.expressive(),
    typography = MeshcoreBrandedTypography,
    shapes = MeshcoreShapes,
    content = content,
  )
}
