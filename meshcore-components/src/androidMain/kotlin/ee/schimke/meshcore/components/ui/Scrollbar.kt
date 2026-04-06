package ee.schimke.meshcore.components.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// Lightweight scrollbar overlay for scrollable composables.
//
// Android Compose's Modifier.verticalScroll and LazyColumn do not paint a
// visible scroll thumb. For long lists (10+ contacts, 10 BLE devices) that
// leaves no affordance that more content exists below the fold. These two
// modifiers paint a thin rounded thumb along the trailing edge of the
// composable, tinted with the current color scheme. Lives in
// meshcore-mobile so shared panels (BleScannerPanel, etc.) can use it
// without having to depend on app-only code.
// ---------------------------------------------------------------------------

private val ThumbWidth = 3.dp
private val ThumbInset = 2.dp
private const val MinThumbFraction = 0.08f

@Composable
fun Modifier.verticalScrollbar(
    state: ScrollState,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
): Modifier {
    val density = LocalDensity.current
    val widthPx = with(density) { ThumbWidth.toPx() }
    val insetPx = with(density) { ThumbInset.toPx() }
    return this.drawWithContent {
        drawContent()
        val max = state.maxValue
        if (max == 0) return@drawWithContent
        val viewport = size.height
        val total = viewport + max
        val visibleFraction = (viewport / total).coerceIn(MinThumbFraction, 1f)
        val thumbHeight = viewport * visibleFraction
        val track = viewport - thumbHeight
        val progress = state.value.toFloat() / max
        val top = track * progress
        drawRoundedThumb(
            color = color,
            topLeft = Offset(x = size.width - widthPx - insetPx, y = top),
            size = Size(width = widthPx, height = thumbHeight),
            radius = widthPx,
        )
    }
}

@Composable
fun Modifier.verticalScrollbar(
    state: LazyListState,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
): Modifier = composed {
    val density = LocalDensity.current
    val widthPx = with(density) { ThumbWidth.toPx() }
    val insetPx = with(density) { ThumbInset.toPx() }
    drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val totalItems = info.totalItemsCount
        val visibleItems = info.visibleItemsInfo
        if (totalItems == 0 || visibleItems.isEmpty()) return@drawWithContent
        // Everything fits — no thumb.
        if (visibleItems.size >= totalItems &&
            info.viewportEndOffset >= (visibleItems.last().offset + visibleItems.last().size)
        ) {
            return@drawWithContent
        }
        val viewport = size.height
        val firstVisible = visibleItems.first()
        // Approximate content extents assuming items are roughly uniform
        // (which is the case for our card-based lists).
        val itemHeight = visibleItems
            .map { it.size.toFloat() }
            .average()
            .toFloat()
            .coerceAtLeast(1f)
        val total = itemHeight * totalItems
        val visibleFraction = (viewport / total).coerceIn(MinThumbFraction, 1f)
        val thumbHeight = viewport * visibleFraction
        val track = viewport - thumbHeight
        val offset = firstVisible.index * itemHeight - firstVisible.offset
        val denom = (total - viewport).coerceAtLeast(1f)
        val progress = (offset / denom).coerceIn(0f, 1f)
        val top = track * progress
        drawRoundedThumb(
            color = color,
            topLeft = Offset(x = size.width - widthPx - insetPx, y = top),
            size = Size(width = widthPx, height = thumbHeight),
            radius = widthPx,
        )
    }
}

private fun DrawScope.drawRoundedThumb(
    color: Color,
    topLeft: Offset,
    size: Size,
    radius: Float,
) {
    drawRoundRect(
        color = color,
        topLeft = topLeft,
        size = size,
        cornerRadius = CornerRadius(radius, radius),
    )
}
