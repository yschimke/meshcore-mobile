package ee.schimke.meshcore.app.ui

// =============================================================================
// PLAY STORE BRAND ASSETS (icon + feature graphic)
// -----------------------------------------------------------------------------
// Each `@Preview` here renders one of the listing brand assets:
//
//   * PlayStoreIcon       -> graphics/icon/icon.png            (512x512)
//   * PlayStoreFeature    -> graphics/feature-graphic/feature.png (1024x500)
//
// Refresh by running:
//
//     ./scripts/refresh-play-screenshots.sh
//
// The artwork is intentionally drawn with Compose `Canvas` so it's
// reproducible from source — no Photoshop, no SVG export step. The
// hexagonal mesh-node motif and palette mirror the launcher icon
// (`res/drawable/ic_launcher_foreground.xml`).
// =============================================================================

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ee.schimke.meshcore.app.ui.theme.MeshcoreTheme
import kotlin.math.cos
import kotlin.math.sin

// --- Palette (matches res/drawable/ic_launcher_*.xml) ------------------------

private val BrandNavy = Color(0xFF0D1B2A)
private val BrandMidBlue = Color(0xFF0F3460)
private val BrandTealDeep = Color(0xFF16697A)
private val BrandTeal = Color(0xFF489FB5)
private val BrandCyan = Color(0xFF82DBD8)

/**
 * Draws the MeshCore mesh-node motif: a central hexagon with six spokes
 * radiating to outer nodes. Drawn into [size]; caller supplies its own
 * background.
 */
@Composable
private fun MeshNode(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val unit = size.minDimension / 108f // match the 108-viewport vector

        // Six outer nodes at r=29 from center, hex angles offset by 30°.
        val outerRadius = 29f * unit
        val outerNodeRadius = 3.5f * unit
        val angles = (0..5).map { i -> Math.toRadians((i * 60 - 90).toDouble()) }
        val outerCenters =
            angles.map { a ->
                Offset(
                    center.x + (outerRadius * cos(a)).toFloat(),
                    center.y + (outerRadius * sin(a)).toFloat(),
                )
            }

        // Spokes
        outerCenters.forEach { p ->
            drawLine(
                color = BrandTeal.copy(alpha = 0.9f),
                start = center,
                end = p,
                strokeWidth = 2.8f * unit,
                cap = StrokeCap.Round,
            )
        }
        // Outer nodes
        outerCenters.forEach { p -> drawCircle(BrandTeal, outerNodeRadius, p) }

        // Central hexagon (vertices from the original vector path)
        val hex =
            Path().apply {
                moveTo(center.x, center.y - 9.2f * unit)
                lineTo(center.x + 8.6f * unit, center.y - 4.6f * unit)
                lineTo(center.x + 8.6f * unit, center.y + 4.6f * unit)
                lineTo(center.x, center.y + 9.2f * unit)
                lineTo(center.x - 8.6f * unit, center.y + 4.6f * unit)
                lineTo(center.x - 8.6f * unit, center.y - 4.6f * unit)
                close()
            }
        drawPath(hex, BrandCyan)

        // White core highlight (r=4 in the 108-viewport)
        drawCircle(Color.White, 4f * unit, center)
    }
}

@Composable
private fun BrandBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier =
            modifier.background(
                Brush.radialGradient(
                    0f to BrandTealDeep,
                    0.45f to BrandMidBlue,
                    1f to BrandNavy,
                )
            )
    ) {
        content()
    }
}

// --- 512x512 hi-res icon -----------------------------------------------------

@Preview(
    name = "Play Store — icon (512x512)",
    showBackground = false,
    device = "spec:width=512dp,height=512dp,dpi=160",
)
@Composable
fun PlayStoreIcon() {
    MeshcoreTheme {
        BrandBackground(modifier = Modifier.fillMaxSize()) {
            // Inset a little so the mesh doesn't crowd the corners; the
            // launcher mask isn't applied in the Play listing, but a
            // square asset still benefits from breathing room.
            MeshNode(modifier = Modifier.fillMaxSize().padding(56.dp))
        }
    }
}

// --- 1024x500 feature graphic ------------------------------------------------

@Preview(
    name = "Play Store — feature graphic (1024x500)",
    showBackground = false,
    device = "spec:width=1024dp,height=500dp,dpi=160",
)
@Composable
fun PlayStoreFeature() {
    MeshcoreTheme {
        BrandBackground(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MeshNode(modifier = Modifier.size(300.dp))
                Spacer(Modifier.width(40.dp))
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "MeshCore",
                        color = Color.White,
                        fontSize = 76.sp,
                        maxLines = 1,
                        style = MaterialTheme.typography.displayLarge,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Talk over the mesh.\nNo internet. No towers.",
                        color = BrandCyan,
                        fontSize = 26.sp,
                        textAlign = TextAlign.Start,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}
