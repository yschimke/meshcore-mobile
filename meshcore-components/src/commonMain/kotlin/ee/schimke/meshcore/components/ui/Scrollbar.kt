package ee.schimke.meshcore.components.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
): Modifier =
  this.drawWithContent {
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
    drawRoundedThumb(color = color, top = top, thumbHeight = thumbHeight)
  }

@Composable
fun Modifier.verticalScrollbar(
  state: LazyListState,
  color: Color = MaterialTheme.colorScheme.outlineVariant,
): Modifier =
  // Every read happens in the draw lambda, so scrolling only invalidates draw. No `composed {}`
  // wrapper: this factory is already @Composable (for the theme default), and density comes from
  // the DrawScope.
  this.drawWithContent {
    drawContent()
    val info = state.layoutInfo
    val totalItems = info.totalItemsCount
    val visibleItems = info.visibleItemsInfo
    if (totalItems == 0 || visibleItems.isEmpty()) return@drawWithContent
    // Everything fits — no thumb.
    if (
      visibleItems.size >= totalItems &&
        info.viewportEndOffset >= (visibleItems.last().offset + visibleItems.last().size)
    ) {
      return@drawWithContent
    }
    val viewport = size.height
    val firstVisible = visibleItems.first()
    // Approximate content extents assuming items are roughly uniform
    // (which is the case for our card-based lists).
    // Summed in place rather than `map {}.average()`, which allocated a list on every draw frame.
    var sizeSum = 0.0
    for (i in visibleItems.indices) sizeSum += visibleItems[i].size
    val itemHeight = (sizeSum / visibleItems.size).toFloat().coerceAtLeast(1f)
    val total = itemHeight * totalItems
    val visibleFraction = (viewport / total).coerceIn(MinThumbFraction, 1f)
    val thumbHeight = viewport * visibleFraction
    val track = viewport - thumbHeight
    val offset = firstVisible.index * itemHeight - firstVisible.offset
    val denom = (total - viewport).coerceAtLeast(1f)
    val progress = (offset / denom).coerceIn(0f, 1f)
    val top = track * progress
    drawRoundedThumb(color = color, top = top, thumbHeight = thumbHeight)
  }

private fun DrawScope.drawRoundedThumb(color: Color, top: Float, thumbHeight: Float) {
  val widthPx = ThumbWidth.toPx()
  val insetPx = ThumbInset.toPx()
  drawRoundRect(
    color = color,
    topLeft = Offset(x = size.width - widthPx - insetPx, y = top),
    size = Size(width = widthPx, height = thumbHeight),
    cornerRadius = CornerRadius(widthPx, widthPx),
  )
}
