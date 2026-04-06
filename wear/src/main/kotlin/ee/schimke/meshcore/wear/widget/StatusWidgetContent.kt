@file:SuppressLint("RestrictedApi")

package ee.schimke.meshcore.wear.widget

import android.annotation.SuppressLint
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark theme colors (same as phone widget and MeshcoreWearTheme)
private val SurfaceContainer = Color(0xFF1A211F).rc
private val OnSurface = Color(0xFFDDE4E1).rc
private val OnSurfaceVariant = Color(0xFFBEC9C5).rc
private val Primary = Color(0xFF53DBC9).rc
private val Tertiary = Color(0xFFE0C38C).rc
private val SurfaceContainerHighest = Color(0xFF2F3634).rc
private val ErrorRed = Color(0xFFFFB4AB).rc

private fun widgetModifier() = RemoteModifier
    .fillMaxSize()
    .background(SurfaceContainer)
    .padding(12.rdp)

/**
 * Remote Compose content for the Wear OS status widget.
 * Non-scrollable, single page. Mirrors the phone's DeviceInfoWidgetContent
 * but simplified for a round watch face.
 */
@Composable
@RemoteComposable
fun StatusWidgetContent(
    deviceName: String,
    connected: Boolean,
    batteryPercent: Int?,
    contactCount: Int,
) {
    RemoteColumn(
        modifier = widgetModifier(),
        verticalArrangement = RemoteArrangement.spacedBy(6.rdp),
    ) {
        // Connection indicator + device name
        RemoteRow {
            val statusColor = if (connected) Primary else ErrorRed
            RemoteText(
                text = if (connected) "\u2B24 " else "\u2B24 ",
                color = statusColor,
                fontSize = 10.rsp,
            )
            RemoteText(
                text = deviceName.rs,
                color = OnSurface,
                fontSize = 16.rsp,
            )
        }

        // Battery
        if (batteryPercent != null) {
            val batteryColor = if (batteryPercent < 30) Tertiary else OnSurface
            val barColor = if (batteryPercent < 30) Tertiary else Primary

            RemoteRow {
                RemoteText("\uD83D\uDD0B ".rs, fontSize = 12.rsp, color = batteryColor)
                RemoteText("${batteryPercent}%".rs, fontSize = 12.rsp, color = batteryColor)
            }
            RemoteBox(
                modifier = RemoteModifier.fillMaxWidth().height(3.rdp).background(SurfaceContainerHighest),
            ) {
                RemoteBox(
                    modifier = RemoteModifier
                        .fillMaxWidth(batteryPercent / 100f)
                        .height(3.rdp)
                        .background(barColor),
                ) {}
            }
        }

        // Contacts count
        RemoteRow {
            RemoteText("\uD83D\uDC65 ".rs, fontSize = 12.rsp, color = OnSurfaceVariant)
            RemoteText("$contactCount contacts".rs, fontSize = 12.rsp, color = OnSurfaceVariant)
        }
    }
}
