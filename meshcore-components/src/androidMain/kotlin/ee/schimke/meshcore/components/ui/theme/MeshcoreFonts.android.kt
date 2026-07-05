package ee.schimke.meshcore.components.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import ee.schimke.meshcore.components.R

// Android: pull the branded faces from the Google Fonts downloadable provider
// (unchanged behaviour — no bundled font binaries on Android).
private val FontProvider =
    GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs,
    )

private fun gfont(name: String, weight: FontWeight) =
    Font(googleFont = GoogleFont(name), fontProvider = FontProvider, weight = weight, style = FontStyle.Normal)

internal actual val Orbitron: FontFamily =
    FontFamily(
        gfont("Orbitron", FontWeight.Medium),
        gfont("Orbitron", FontWeight.SemiBold),
        gfont("Orbitron", FontWeight.Bold),
    )

internal actual val SpaceGrotesk: FontFamily =
    FontFamily(
        gfont("Space Grotesk", FontWeight.Normal),
        gfont("Space Grotesk", FontWeight.Medium),
        gfont("Space Grotesk", FontWeight.SemiBold),
        gfont("Space Grotesk", FontWeight.Bold),
    )

internal actual val JetBrainsMono: FontFamily =
    FontFamily(
        gfont("JetBrains Mono", FontWeight.Normal),
        gfont("JetBrains Mono", FontWeight.Medium),
    )

internal actual val LobsterTwo: FontFamily =
    FontFamily(
        gfont("Lobster Two", FontWeight.Normal),
        gfont("Lobster Two", FontWeight.Bold),
    )
