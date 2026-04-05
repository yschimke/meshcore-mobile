package ee.schimke.meshcore.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import ee.schimke.meshcore.app.R

// ---------------------------------------------------------------------------
// MeshCore branded typography.
//
// When the user picks the "MeshCore" palette we layer custom display
// and mono typefaces on top of the base M3 scale:
//
//   Headers / logo  → Orbitron (Medium 500)        — wide geometric display face
//   UI / buttons    → Space Grotesk (Bold 700)     — confident neo-grotesque
//   Telemetry / data → JetBrains Mono (Regular 400) — for pubkeys, MACs, MHz
//
// The fonts are pulled from the Google Fonts downloadable provider so
// they ship as deltas against any pre-existing Play Services cache.
// When the user picks the Dynamic palette we intentionally fall back
// to M3 defaults — dynamic colors are a system-look feature and
// pairing them with custom display fonts would clash.
// ---------------------------------------------------------------------------

private val FontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private fun gfont(name: String, weight: FontWeight) = Font(
    googleFont = GoogleFont(name),
    fontProvider = FontProvider,
    weight = weight,
    style = FontStyle.Normal,
)

private val Orbitron = FontFamily(
    gfont("Orbitron", FontWeight.Medium),
    gfont("Orbitron", FontWeight.SemiBold),
    gfont("Orbitron", FontWeight.Bold),
)

private val SpaceGrotesk = FontFamily(
    gfont("Space Grotesk", FontWeight.Normal),
    gfont("Space Grotesk", FontWeight.Medium),
    gfont("Space Grotesk", FontWeight.SemiBold),
    gfont("Space Grotesk", FontWeight.Bold),
)

private val JetBrainsMono = FontFamily(
    gfont("JetBrains Mono", FontWeight.Normal),
    gfont("JetBrains Mono", FontWeight.Medium),
)

/** Monospace body style exposed for callers that need to render data. */
val MeshcoreMonoBody: TextStyle = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.2.sp,
)

/**
 * Typography for the MeshCore branded palette. Headlines & titles use
 * Orbitron, body/label copy uses Space Grotesk, and the base monospace
 * fallback is JetBrains Mono so raw `FontFamily.Monospace` references
 * throughout the app inherit the branded look.
 */
val MeshcoreBrandedTypography: Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = Orbitron,
        fontWeight = FontWeight.Medium,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Orbitron,
        fontWeight = FontWeight.Medium,
        fontSize = 45.sp,
        lineHeight = 52.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Orbitron,
        fontWeight = FontWeight.Medium,
        fontSize = 36.sp,
        lineHeight = 44.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Orbitron,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Orbitron,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Orbitron,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.5.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Orbitron,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.2.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.4.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)
