package ee.schimke.meshcore.components.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// MeshCore Material 3 palette + shapes (seed teal #00695C). Single source of
// truth, shared by the app theme and the CMP desktop parity render.

private val TealPrimary = Color(0xFF006A60)
private val TealOnPrimary = Color(0xFFFFFFFF)
private val TealPrimaryContainer = Color(0xFF74F8E5)
private val TealOnPrimaryContainer = Color(0xFF00201C)

private val SlateSecondary = Color(0xFF4A635F)
private val SlateOnSecondary = Color(0xFFFFFFFF)
private val SlateSecondaryContainer = Color(0xFFCCE8E2)
private val SlateOnSecondaryContainer = Color(0xFF05201C)

private val AmberTertiary = Color(0xFF715B2E)
private val AmberOnTertiary = Color(0xFFFFFFFF)
private val AmberTertiaryContainer = Color(0xFFFDDFA6)
private val AmberOnTertiaryContainer = Color(0xFF261A00)

private val ErrorRed = Color(0xFFBA1A1A)
private val OnErrorRed = Color(0xFFFFFFFF)
private val ErrorRedContainer = Color(0xFFFFDAD6)
private val OnErrorRedContainer = Color(0xFF410002)

private val SurfaceLight = Color(0xFFF4FBF8)
private val OnSurfaceLight = Color(0xFF161D1B)
private val SurfaceVariantLight = Color(0xFFDAE5E1)
private val OnSurfaceVariantLight = Color(0xFF3F4946)
private val OutlineLight = Color(0xFF6F7976)
private val OutlineVariantLight = Color(0xFFBEC9C5)
private val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
private val SurfaceContainerLowLight = Color(0xFFEEF5F2)
private val SurfaceContainerLight = Color(0xFFE8EFEC)
private val SurfaceContainerHighLight = Color(0xFFE2E9E6)
private val SurfaceContainerHighestLight = Color(0xFFDCE3E0)

private val TealPrimaryDark = Color(0xFF53DBC9)
private val TealOnPrimaryDark = Color(0xFF003731)
private val TealPrimaryContainerDark = Color(0xFF005048)
private val TealOnPrimaryContainerDark = Color(0xFF74F8E5)

private val SlateSecondaryDark = Color(0xFFB0CCC6)
private val SlateOnSecondaryDark = Color(0xFF1B3531)
private val SlateSecondaryContainerDark = Color(0xFF324B47)
private val SlateOnSecondaryContainerDark = Color(0xFFCCE8E2)

private val AmberTertiaryDark = Color(0xFFE0C38C)
private val AmberOnTertiaryDark = Color(0xFF3F2E04)
private val AmberTertiaryContainerDark = Color(0xFF584419)
private val AmberOnTertiaryContainerDark = Color(0xFFFDDFA6)

private val ErrorRedDark = Color(0xFFFFB4AB)
private val OnErrorRedDark = Color(0xFF690005)
private val ErrorRedContainerDark = Color(0xFF93000A)
private val OnErrorRedContainerDark = Color(0xFFFFDAD6)

private val SurfaceDark = Color(0xFF0E1513)
private val OnSurfaceDark = Color(0xFFDDE4E1)
private val SurfaceVariantDark = Color(0xFF3F4946)
private val OnSurfaceVariantDark = Color(0xFFBEC9C5)
private val OutlineDark = Color(0xFF89938F)
private val OutlineVariantDark = Color(0xFF3F4946)
private val SurfaceContainerLowestDark = Color(0xFF090F0E)
private val SurfaceContainerLowDark = Color(0xFF161D1B)
private val SurfaceContainerDark = Color(0xFF1A211F)
private val SurfaceContainerHighDark = Color(0xFF242B29)
private val SurfaceContainerHighestDark = Color(0xFF2F3634)

val MeshcoreLightColors =
  lightColorScheme(
    primary = TealPrimary,
    onPrimary = TealOnPrimary,
    primaryContainer = TealPrimaryContainer,
    onPrimaryContainer = TealOnPrimaryContainer,
    secondary = SlateSecondary,
    onSecondary = SlateOnSecondary,
    secondaryContainer = SlateSecondaryContainer,
    onSecondaryContainer = SlateOnSecondaryContainer,
    tertiary = AmberTertiary,
    onTertiary = AmberOnTertiary,
    tertiaryContainer = AmberTertiaryContainer,
    onTertiaryContainer = AmberOnTertiaryContainer,
    error = ErrorRed,
    onError = OnErrorRed,
    errorContainer = ErrorRedContainer,
    onErrorContainer = OnErrorRedContainer,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
  )

val MeshcoreDarkColors =
  darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = TealOnPrimaryDark,
    primaryContainer = TealPrimaryContainerDark,
    onPrimaryContainer = TealOnPrimaryContainerDark,
    secondary = SlateSecondaryDark,
    onSecondary = SlateOnSecondaryDark,
    secondaryContainer = SlateSecondaryContainerDark,
    onSecondaryContainer = SlateOnSecondaryContainerDark,
    tertiary = AmberTertiaryDark,
    onTertiary = AmberOnTertiaryDark,
    tertiaryContainer = AmberTertiaryContainerDark,
    onTertiaryContainer = AmberOnTertiaryContainerDark,
    error = ErrorRedDark,
    onError = OnErrorRedDark,
    errorContainer = ErrorRedContainerDark,
    onErrorContainer = OnErrorRedContainerDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
  )

val MeshcoreShapes =
  Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )
