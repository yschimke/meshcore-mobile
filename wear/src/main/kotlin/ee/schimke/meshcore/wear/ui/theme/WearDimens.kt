package ee.schimke.meshcore.wear.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Wear OS spacing scale — tighter than the phone's [Dimens] (~75%).
 * See STYLEGUIDE.md § Wear OS for the mapping rationale.
 */
object WearDimens {
    val XXS = 2.dp
    val XS = 3.dp
    val S = 4.dp
    val M = 8.dp
    val L = 12.dp
    val XL = 16.dp

    val ScreenPadding = L
    val CardGap = M
    val RowGap = S
    val IconSize = 20.dp
    val TouchTarget = 48.dp
}
