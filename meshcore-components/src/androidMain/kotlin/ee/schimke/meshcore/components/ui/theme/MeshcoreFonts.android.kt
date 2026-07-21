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

// Non-Latin fallback. The branded faces are Latin-only, but the app is localized
// into scripts they don't cover (Cyrillic, Arabic, Devanagari, Thai, and CJK /
// Hangul / Kana). Append the matching Noto families — pulled from the same
// downloadable provider — to every branded family so a glyph the brand face lacks
// resolves within the app's own typography rather than the ambient OEM system
// font. Desktop bundles the same families as subset .ttf (MeshcoreFonts.desktop.kt).
//
// CJK is split JP/KR/SC/TC because no single per-language family carries Kana +
// Hangul + both Han variants; a shared Han codepoint follows this order
// (JP → SC → TC → KR). Downloadable, so nothing is added to the APK.
private val NotoFallback =
    listOf(
        gfont("Noto Sans", FontWeight.Normal), // Cyrillic / Greek / Latin-ext
        gfont("Noto Sans Arabic", FontWeight.Normal),
        gfont("Noto Sans Devanagari", FontWeight.Normal),
        gfont("Noto Sans Thai", FontWeight.Normal),
        gfont("Noto Sans JP", FontWeight.Normal), // Kana + Japanese Han
        gfont("Noto Sans SC", FontWeight.Normal), // Simplified Han
        gfont("Noto Sans TC", FontWeight.Normal), // Traditional Han
        gfont("Noto Sans KR", FontWeight.Normal), // Hangul + Korean Han
    )

internal actual val Orbitron: FontFamily =
    FontFamily(
        listOf(
            gfont("Orbitron", FontWeight.Medium),
            gfont("Orbitron", FontWeight.SemiBold),
            gfont("Orbitron", FontWeight.Bold),
        ) + NotoFallback,
    )

internal actual val SpaceGrotesk: FontFamily =
    FontFamily(
        listOf(
            gfont("Space Grotesk", FontWeight.Normal),
            gfont("Space Grotesk", FontWeight.Medium),
            gfont("Space Grotesk", FontWeight.SemiBold),
            gfont("Space Grotesk", FontWeight.Bold),
        ) + NotoFallback,
    )

internal actual val JetBrainsMono: FontFamily =
    FontFamily(
        listOf(
            gfont("JetBrains Mono", FontWeight.Normal),
            gfont("JetBrains Mono", FontWeight.Medium),
        ) + NotoFallback,
    )
