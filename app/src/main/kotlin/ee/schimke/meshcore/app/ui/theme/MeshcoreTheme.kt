package ee.schimke.meshcore.app.ui.theme

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import ee.schimke.meshcore.components.ui.theme.MeshcoreTheme as SharedMeshcoreTheme

/** True while a theme-catalog provider already owns the preview's outer theme. */
internal val LocalThemeCatalogOverride = staticCompositionLocalOf { false }

/**
 * Top-level theme wrapper. All previews and the MainActivity content should be wrapped in this —
 * never in a bare MaterialTheme {} — so the design is applied consistently.
 *
 * This is the shared MeshCore Expressive theme from `:meshcore-components` (brand palette following
 * the system light/dark setting) plus the Android edge-to-edge system-bar styling.
 */
@Composable
fun MeshcoreTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  // Theme catalog providers wrap the preview function. Most MeshCore previews also install their
  // normal default theme in their body; without this guard that inner theme would shadow the
  // selected provider and every theme override would render identical pixels.
  if (LocalThemeCatalogOverride.current) {
    content()
    return
  }

  // Keep system bar icons legible: enableEdgeToEdge in MainActivity only samples the system
  // setting once, so re-apply it here against the effective darkTheme.
  val activity = LocalActivity.current as? ComponentActivity
  if (activity != null) {
    DisposableEffect(activity, darkTheme) {
      activity.enableEdgeToEdge(
        statusBarStyle =
          SystemBarStyle.auto(
            AndroidColor.TRANSPARENT,
            AndroidColor.TRANSPARENT,
          ) {
            darkTheme
          },
        navigationBarStyle =
          SystemBarStyle.auto(
            AndroidColor.WHITE,
            AndroidColor.BLACK,
          ) {
            darkTheme
          },
      )
      onDispose {}
    }
  }
  SharedMeshcoreTheme(darkTheme = darkTheme, content = content)
}
