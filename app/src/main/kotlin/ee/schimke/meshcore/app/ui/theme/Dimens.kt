package ee.schimke.meshcore.app.ui.theme

import androidx.compose.ui.unit.dp
import ee.schimke.meshcore.app.ui.theme.Dimens.L

/**
 * MeshCore spacing scale. All paddings and gaps in the app should use
 * one of these — do not hand-pick dp values at call sites. The scale is
 * 4dp-based and capped at 48dp; larger gaps are a design smell and
 * probably mean a section break belongs there instead of whitespace.
 */
object Dimens {
    val XXS = 2.dp
    val XS = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val XL = 24.dp
    val XXL = 32.dp
    val XXXL = 48.dp

    /** Canonical screen edge inset — matches the [L] step above. */
    val ScreenPadding = L

    /** Vertical gap between cards in a list. */
    val CardGap = M

    /** Vertical gap between rows inside a card. */
    val RowGap = S

    /** Minimum touch target per Material guidance. */
    val TouchTarget = 48.dp
}
