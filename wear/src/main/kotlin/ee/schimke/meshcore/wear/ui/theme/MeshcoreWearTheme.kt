package ee.schimke.meshcore.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography
import ee.schimke.meshcore.wear.R

// ---------------------------------------------------------------------------
// MeshCore Wear OS theme.
//
// Always dark — Wear OS has no light mode. Colors match the phone's
// MeshcoreDarkColors from MeshcoreTheme.kt. Typography scales down
// ~75% from phone sizes per STYLEGUIDE.md § Wear OS.
//
// Uses androidx.wear.compose.material3.MaterialTheme directly.
// No Horologist Compose wrappers.
// ---------------------------------------------------------------------------

// --- Dark palette (same values as phone MeshcoreDarkColors) ----------------

private val TealPrimary = Color(0xFF53DBC9)
private val TealOnPrimary = Color(0xFF003731)
private val TealPrimaryContainer = Color(0xFF005048)
private val TealOnPrimaryContainer = Color(0xFF74F8E5)

private val SlateSecondary = Color(0xFFB0CCC6)
private val SlateOnSecondary = Color(0xFF1B3531)
private val SlateSecondaryContainer = Color(0xFF324B47)
private val SlateOnSecondaryContainer = Color(0xFFCCE8E2)

private val AmberTertiary = Color(0xFFE0C38C)
private val AmberOnTertiary = Color(0xFF3F2E04)
private val AmberTertiaryContainer = Color(0xFF584419)
private val AmberOnTertiaryContainer = Color(0xFFFDDFA6)

private val ErrorRed = Color(0xFFFFB4AB)
private val OnErrorRed = Color(0xFF690005)
private val ErrorRedContainer = Color(0xFF93000A)
private val OnErrorRedContainer = Color(0xFFFFDAD6)

private val Surface = Color(0xFF0E1513)
private val OnSurface = Color(0xFFDDE4E1)
private val SurfaceVariant = Color(0xFF3F4946)
private val OnSurfaceVariant = Color(0xFFBEC9C5)
private val Outline = Color(0xFF89938F)
private val OutlineVariant = Color(0xFF3F4946)

// Dim variants for Wear M3 (slightly muted versions of the base colors)
private val TealPrimaryDim = Color(0xFF3DB8A8)
private val SlateSecondaryDim = Color(0xFF8FABA6)
private val AmberTertiaryDim = Color(0xFFC0A574)
private val ErrorRedDim = Color(0xFFD9908A)

// Surface containers
private val SurfaceContainerLow = Color(0xFF161D1B)
private val SurfaceContainerMid = Color(0xFF1A211F)
private val SurfaceContainerHigh = Color(0xFF242B29)

val MeshcoreWearColors = ColorScheme(
    primary = TealPrimary,
    primaryDim = TealPrimaryDim,
    primaryContainer = TealPrimaryContainer,
    onPrimary = TealOnPrimary,
    onPrimaryContainer = TealOnPrimaryContainer,
    secondary = SlateSecondary,
    secondaryDim = SlateSecondaryDim,
    secondaryContainer = SlateSecondaryContainer,
    onSecondary = SlateOnSecondary,
    onSecondaryContainer = SlateOnSecondaryContainer,
    tertiary = AmberTertiary,
    tertiaryDim = AmberTertiaryDim,
    tertiaryContainer = AmberTertiaryContainer,
    onTertiary = AmberOnTertiary,
    onTertiaryContainer = AmberOnTertiaryContainer,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainerMid,
    surfaceContainerHigh = SurfaceContainerHigh,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
    background = Surface,
    onBackground = OnSurface,
    error = ErrorRed,
    errorDim = ErrorRedDim,
    errorContainer = ErrorRedContainer,
    onError = OnErrorRed,
    onErrorContainer = OnErrorRedContainer,
)

// --- Fonts (same families as phone, scaled down for wrist) -----------------

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
)

private val SpaceGrotesk = FontFamily(
    gfont("Space Grotesk", FontWeight.Normal),
    gfont("Space Grotesk", FontWeight.Medium),
    gfont("Space Grotesk", FontWeight.Bold),
)

private val JetBrainsMono = FontFamily(
    gfont("JetBrains Mono", FontWeight.Normal),
)

/** Monospace body style for data values (pubkeys, frequencies, RSSI). */
val WearMonoBody: TextStyle = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 15.sp,
    letterSpacing = 0.2.sp,
)

private val WearTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = Orbitron,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.3.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
    ),
)

@Composable
fun MeshcoreWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MeshcoreWearColors,
        typography = WearTypography,
        content = content,
    )
}
