package ee.schimke.meshcore.components.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// MeshCore branded typography (multiplatform).
//
//   Headers / logo   → Orbitron        — wide geometric display face
//   UI / body        → Space Grotesk   — confident neo-grotesque
//   Telemetry / data → JetBrains Mono  — pubkeys, MACs, MHz
//
// The font *families* are provided per-platform (expect/actual): Android pulls
// them from the Google Fonts downloadable provider (unchanged); desktop loads
// bundled .ttf. The Typography spec below is shared so both render identically.
// ---------------------------------------------------------------------------

internal expect val Orbitron: FontFamily
internal expect val SpaceGrotesk: FontFamily
internal expect val JetBrainsMono: FontFamily

/** Script/display face used for the device-name title (design-led — see design-briefs/). */
internal expect val LobsterTwo: FontFamily

/** Monospace body style exposed for callers that need to render data. */
val MeshcoreMonoBody: TextStyle =
  TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.2.sp,
  )

/** Device-name title style — Lobster Two, per design-briefs/device-summary-card-lobster-two. */
val MeshcoreDeviceTitle: TextStyle =
  TextStyle(
    fontFamily = LobsterTwo,
    fontWeight = FontWeight.Normal,
    fontSize = 24.sp,
    lineHeight = 30.sp,
  )

/** Typography for the MeshCore branded palette. */
val MeshcoreBrandedTypography: Typography =
  Typography(
    displayLarge =
      TextStyle(
        fontFamily = Orbitron,
        fontWeight = FontWeight.Medium,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
      ),
    displayMedium =
      TextStyle(
        fontFamily = Orbitron,
        fontWeight = FontWeight.Medium,
        fontSize = 45.sp,
        lineHeight = 52.sp,
      ),
    displaySmall =
      TextStyle(
        fontFamily = Orbitron,
        fontWeight = FontWeight.Medium,
        fontSize = 36.sp,
        lineHeight = 44.sp,
      ),
    headlineLarge =
      TextStyle(
        fontFamily = Orbitron,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = 40.sp,
      ),
    headlineMedium =
      TextStyle(
        fontFamily = Orbitron,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 36.sp,
      ),
    headlineSmall =
      TextStyle(
        fontFamily = Orbitron,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.5.sp,
      ),
    titleLarge =
      TextStyle(
        fontFamily = Orbitron,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.2.sp,
      ),
    titleMedium =
      TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
      ),
    titleSmall =
      TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
      ),
    bodyLarge =
      TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
      ),
    bodyMedium =
      TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
      ),
    bodySmall =
      TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
      ),
    labelLarge =
      TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.4.sp,
      ),
    labelMedium =
      TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
      ),
    labelSmall =
      TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
      ),
  )
