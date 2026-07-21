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

// Non-Latin fallback. The branded faces (Orbitron / Space Grotesk / JetBrains
// Mono) are Latin-only, but the app is localized into scripts they don't cover
// (Cyrillic, Arabic, Devanagari, Thai, and CJK / Hangul / Kana). Append the Noto
// subsets to every branded family so a glyph the brand face lacks resolves within
// the app's own typography instead of rendering tofu (or, on device, in whatever
// system font happens to be installed). Android does the same via the downloadable
// provider (see MeshcoreFonts.android.kt).
//
// These are static Regular subsets, pre-generated to exactly the characters used
// across values-*/strings.xml (scripts/fonts/generate-fallback-subsets.py) so the
// bundle stays ~1 MB rather than shipping full multi-MB CJK fonts. No
// `variationSettings` — the subsets carry no variable axis.
private fun fallback(path: String) =
  Font(resource = path, weight = FontWeight.Normal, style = FontStyle.Normal)

// CJK is split JP/KR/SC/TC because no single per-language file carries Kana +
// Hangul + both Han variants. A shared Han codepoint resolves to whichever comes
// first here, so its form follows this order (JP → SC → TC → KR); acceptable for
// a fallback, though not locale-perfect for Han unification.
//
// The CJK faces come *first*: the CJK subsets also carry the CJK punctuation that
// appears in the translations (「」、。 …), so if an earlier non-CJK Noto face that
// also covers that punctuation (e.g. Noto Sans) wins the run, the Kana/Han in the
// same run render as tofu from that face. Putting the CJK faces ahead keeps a CJK
// run on a CJK face; Arabic/Cyrillic/etc. still fall through to their own faces.
private val NotoFallback =
  listOf(
    fallback("fonts/noto/NotoSansJP-subset.ttf"), // Kana + Japanese Han
    fallback("fonts/noto/NotoSansSC-subset.ttf"), // Simplified Han
    fallback("fonts/noto/NotoSansTC-subset.ttf"), // Traditional Han
    fallback("fonts/noto/NotoSansKR-subset.ttf"), // Hangul + Korean Han
    fallback("fonts/noto/NotoSansArabic-subset.ttf"),
    fallback("fonts/noto/NotoSansDevanagari-subset.ttf"),
    fallback("fonts/noto/NotoSansThai-subset.ttf"),
    fallback("fonts/noto/NotoSans-subset.ttf"), // Cyrillic / Greek / Latin-ext
  )

internal actual val Orbitron: FontFamily =
  FontFamily(
    listOf(
      ttf("fonts/Orbitron.ttf", FontWeight.Medium),
      ttf("fonts/Orbitron.ttf", FontWeight.SemiBold),
      ttf("fonts/Orbitron.ttf", FontWeight.Bold),
    ) + NotoFallback
  )

internal actual val SpaceGrotesk: FontFamily =
  FontFamily(
    listOf(
      ttf("fonts/SpaceGrotesk.ttf", FontWeight.Normal),
      ttf("fonts/SpaceGrotesk.ttf", FontWeight.Medium),
      ttf("fonts/SpaceGrotesk.ttf", FontWeight.SemiBold),
      ttf("fonts/SpaceGrotesk.ttf", FontWeight.Bold),
    ) + NotoFallback
  )

internal actual val JetBrainsMono: FontFamily =
  FontFamily(
    listOf(
      ttf("fonts/JetBrainsMono.ttf", FontWeight.Normal),
      ttf("fonts/JetBrainsMono.ttf", FontWeight.Medium),
    ) + NotoFallback
  )
