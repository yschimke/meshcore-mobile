@file:SuppressLint("RestrictedApi")

package ee.schimke.meshcore.app.widget

import android.annotation.SuppressLint
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Battery1Bar
import androidx.compose.material.icons.rounded.Battery3Bar
import androidx.compose.material.icons.rounded.Battery6Bar
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteDrawScope
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.drawWithContent
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.painter.RemotePainter
import androidx.compose.remote.creation.compose.shapes.RemoteRoundedCornerShape
import androidx.compose.remote.creation.compose.shapes.RemoteShape
import androidx.compose.remote.creation.compose.state.RemoteBoolean
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.rb
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.remote.material3.RemoteIcon

// --- Exact MeshCore dark theme colors from MeshcoreTheme.kt -----------------
// These are the same values used by the Compose DeviceSummaryCard in dark mode.

val SurfaceContainer = Color(0xFF1A211F).rc          // card background
val OnSurface = Color(0xFFDDE4E1).rc                 // title, battery text
val OnSurfaceVariant = Color(0xFFBEC9C5).rc          // pubkey, radio, storage
val Primary = Color(0xFF53DBC9).rc                   // progress bar
val Tertiary = Color(0xFFE0C38C).rc                  // low-battery warning
val SurfaceContainerHighest = Color(0xFF2F3634).rc   // progress track
val Warning = Color(0xFFFFB74D).rc                   // stale indicator

/** Draws a colored and shaped background with when clipping is not supported. */
internal fun RemoteDrawScope.drawShapedBackground(
    shape: RemoteShape,
    color: RemoteColor,
    enabled: RemoteBoolean,
    containerPainter: RemotePainter?,
    disabledContainerPainter: RemotePainter?,
    borderColor: RemoteColor?,
    borderStrokeWidth: RemoteFloat?,
) {
    val w = width
    val h = height

    if (!enabled.hasConstantValue) {
        TODO("Dynamic clickable enabled value is not supported.")
    }

    val backgroundImagePainter =
        if (enabled.constantValue) containerPainter else disabledContainerPainter

    if (backgroundImagePainter != null) {
        // Draws solid shape as destination
        drawSolidColorShape(shape, w, h)

        // TODO: Fix BlendMode.SRC_IN so it draws an shaped image
        with(backgroundImagePainter) { draw() }
    } else {
        // Draws solid color shape
        drawSolidColorShape(shape, w, h, color)
    }

    // Draw border if specified
    if (borderColor != null && borderStrokeWidth != null) {
        drawBorder(borderColor, borderStrokeWidth, shape, w, h)
    }
}

private fun RemoteDrawScope.drawBorder(
    borderColor: RemoteColor,
    borderStrokeWidth: RemoteFloat,
    shape: RemoteShape,
    w: RemoteFloat,
    h: RemoteFloat,
) {
    with(
        shape.createOutline(
            androidx.compose.remote.creation.compose.layout.RemoteSize(w, h),
            remoteDensity,
            layoutDirection
        )
    ) {
        drawOutline(
            androidx.compose.remote.creation.compose.state.RemotePaint {
                color = borderColor
                strokeWidth = borderStrokeWidth
                style = androidx.compose.ui.graphics.PaintingStyle.Stroke
            }
        )
    }
}

private fun RemoteDrawScope.drawSolidColorShape(
    shape: RemoteShape,
    w: RemoteFloat,
    h: RemoteFloat,
    color: RemoteColor? = null,
) {
    with(
        shape.createOutline(
            androidx.compose.remote.creation.compose.layout.RemoteSize(w, h),
            remoteDensity,
            layoutDirection
        )
    ) {
        drawOutline(
            androidx.compose.remote.creation.compose.state.RemotePaint {
                style = androidx.compose.ui.graphics.PaintingStyle.Fill
                color?.let { this.color = it }
            }
        )
    }
}

fun cardModifier() = RemoteModifier
    .fillMaxSize()
    .drawWithContent {
        drawShapedBackground(
            shape = RemoteRoundedCornerShape(16.rdp),
            color = SurfaceContainer,
            enabled = true.rb,
            containerPainter = null,
            disabledContainerPainter = null,
            borderColor = null,
            borderStrokeWidth = null
        )
        drawContent()
    }
    .padding(16.rdp) // matches Compose card: Modifier.padding(16.dp)

// --- Device Info Widget (mirrors DeviceSummaryCard) -------------------------

@Composable
@RemoteComposable
fun DeviceInfoWidgetContent(
    deviceName: String,
    pubkeyPrefix: String?,
    radioInfo: String?,
    batteryLine: String?,
    batteryProgress: Float? = null,
    batteryWarn: Boolean = false,
    storageLine: String?,
    staleLabel: String? = null,
) {
    RemoteColumn(
        modifier = cardModifier(),
        verticalArrangement = RemoteArrangement.spacedBy(10.rdp),
    ) {
        RemoteText(deviceName.rs, color = OnSurface, fontSize = 20.rsp)

        if (pubkeyPrefix != null) {
            RemoteRow(horizontalArrangement = RemoteArrangement.spacedBy(4.rdp)) {
                RemoteIcon(
                    imageVector = Icons.Rounded.Fingerprint,
                    contentDescription = null,
                    tint = OnSurfaceVariant,
                    modifier = RemoteModifier.size(12.rdp)
                )
                RemoteText(pubkeyPrefix.rs, color = OnSurfaceVariant, fontSize = 14.rsp)
            }
        }

        // Radio — icon + bodyMedium ~14sp
        if (radioInfo != null) {
            RemoteRow(horizontalArrangement = RemoteArrangement.spacedBy(4.rdp)) {
                RemoteIcon(
                    imageVector = Icons.Rounded.Wifi,
                    contentDescription = null,
                    tint = OnSurfaceVariant,
                    modifier = RemoteModifier.size(12.rdp)
                )
                RemoteText(radioInfo.rs, color = OnSurfaceVariant, fontSize = 14.rsp, maxLines = 1)
            }
        }

        // Battery section — icon + text + progress bar (inner spacedBy 4dp)
        if (batteryLine != null) {
            val batteryColor = if (batteryWarn) Tertiary else OnSurface
            val barColor = if (batteryWarn) Tertiary else Primary

            RemoteColumn(verticalArrangement = RemoteArrangement.spacedBy(4.rdp)) {
                RemoteRow(horizontalArrangement = RemoteArrangement.spacedBy(4.rdp)) {
                    val percent = ((batteryProgress ?: 0f) * 100).toInt()
                    val icon = when {
                        percent >= 80 -> Icons.Rounded.BatteryFull
                        percent >= 50 -> Icons.Rounded.Battery6Bar
                        percent >= 25 -> Icons.Rounded.Battery3Bar
                        else -> Icons.Rounded.Battery1Bar
                    }
                    RemoteIcon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = batteryColor,
                        modifier = RemoteModifier.size(12.rdp)
                    )
                    RemoteText(batteryLine.rs, color = batteryColor, fontSize = 14.rsp)
                }
                // Progress bar
                if (batteryProgress != null) {
                    RemoteBox(
                        modifier = RemoteModifier.fillMaxWidth().height(4.rdp)
                            .background(SurfaceContainerHighest),
                    ) {
                        RemoteBox(
                            modifier = RemoteModifier
                                .fillMaxWidth(batteryProgress)
                                .height(4.rdp)
                                .background(barColor),
                        ) {}
                    }
                }
            }
        }

        // Storage — icon + bodySmall ~12sp
        if (storageLine != null) {
            RemoteRow(horizontalArrangement = RemoteArrangement.spacedBy(4.rdp)) {
                RemoteIcon(
                    imageVector = Icons.Rounded.Storage,
                    contentDescription = null,
                    tint = OnSurfaceVariant,
                    modifier = RemoteModifier.size(12.rdp)
                )
                RemoteText(storageLine.rs, color = OnSurfaceVariant, fontSize = 12.rsp)
            }
        }

        // Stale indicator
        if (staleLabel != null) {
            RemoteText(staleLabel.rs, color = Warning, fontSize = 12.rsp)
        }
    }
}
