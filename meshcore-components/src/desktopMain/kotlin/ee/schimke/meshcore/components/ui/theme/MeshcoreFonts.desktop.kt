package ee.schimke.meshcore.components.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

// Desktop: load the branded faces from bundled .ttf (variable fonts) on the
// classpath (desktopMain/resources/fonts). Matches the Android families so the
// parity render is faithful.
//
// These are *variable* fonts: every weight below points at the same .ttf. Skiko
// matches a face within a family by its declared `weight`, but it renders at the
// file's default instance unless the variable `wght` axis is set explicitly — so
// without `variationSettings` all of Medium/SemiBold/Bold rasterize identically.
// Applying `FontVariation.Settings(weight, …)` sets the `wght` axis to the face's
// weight, so each entry renders at its actual weight and matches the Android
// downloadable-font parity.
private fun ttf(path: String, weight: FontWeight) =
  Font(
    resource = path,
    weight = weight,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(weight, FontStyle.Normal),
  )

internal actual val Orbitron: FontFamily =
  FontFamily(
    ttf("fonts/Orbitron.ttf", FontWeight.Medium),
    ttf("fonts/Orbitron.ttf", FontWeight.SemiBold),
    ttf("fonts/Orbitron.ttf", FontWeight.Bold),
  )

internal actual val SpaceGrotesk: FontFamily =
  FontFamily(
    ttf("fonts/SpaceGrotesk.ttf", FontWeight.Normal),
    ttf("fonts/SpaceGrotesk.ttf", FontWeight.Medium),
    ttf("fonts/SpaceGrotesk.ttf", FontWeight.SemiBold),
    ttf("fonts/SpaceGrotesk.ttf", FontWeight.Bold),
  )

internal actual val JetBrainsMono: FontFamily =
  FontFamily(
    ttf("fonts/JetBrainsMono.ttf", FontWeight.Normal),
    ttf("fonts/JetBrainsMono.ttf", FontWeight.Medium),
  )
