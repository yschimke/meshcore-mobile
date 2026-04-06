@file:SuppressLint("RestrictedApi")

package ee.schimke.meshcore.app.widget

import android.annotation.SuppressLint
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.layout.RemoteSpacer
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

// --- MeshCore dark-theme palette --------------------------------------------

private val WidgetSurfaceContainer = Color(0xFF1A211F).rc
private val WidgetOnSurface = Color(0xFFDDE4E1).rc
private val WidgetPrimary = Color(0xFF53DBC9).rc
private val WidgetOnSurfaceVariant = Color(0xFFBEC9C5).rc
private val WidgetTertiary = Color(0xFFFFB74D).rc
private val WidgetTrack = Color(0xFF2A3230).rc
private val WidgetWarning = Color(0xFFFFB74D).rc

private fun widgetModifier() = RemoteModifier
    .fillMaxSize()
    .background(WidgetSurfaceContainer)
    .padding(16.rdp)

// --- Device Info Widget (mirrors DeviceSummaryCard) -------------------------

@Composable
@RemoteComposable
fun DeviceInfoWidgetContent(
    deviceName: String,
    pubkeyPrefix: String?,
    radioInfo: String?,
    batteryLine: String?,
    batteryProgress: Float? = null,
    storageLine: String?,
    staleLabel: String? = null,
) {
    RemoteColumn(modifier = widgetModifier()) {
        // Device name — bold, primary
        RemoteText(deviceName.rs, color = WidgetOnSurface, fontSize = 18.rsp)

        RemoteSpacer(modifier = RemoteModifier.height(8.rdp))

        // Pubkey prefix — monospace-style, small
        if (pubkeyPrefix != null) {
            RemoteRow {
                RemoteText("\uD83D\uDD11 ".rs, fontSize = 12.rsp, color = WidgetOnSurfaceVariant)
                RemoteText(pubkeyPrefix.rs, color = WidgetOnSurfaceVariant, fontSize = 12.rsp)
            }
            RemoteSpacer(modifier = RemoteModifier.height(4.rdp))
        }

        // Radio info
        if (radioInfo != null) {
            RemoteRow {
                RemoteText("\uD83D\uDCE1 ".rs, fontSize = 12.rsp, color = WidgetOnSurfaceVariant)
                RemoteText(radioInfo.rs, color = WidgetOnSurfaceVariant, fontSize = 12.rsp)
            }
            RemoteSpacer(modifier = RemoteModifier.height(8.rdp))
        }

        // Battery line
        if (batteryLine != null) {
            RemoteRow {
                RemoteText("\uD83D\uDD0B ".rs, fontSize = 13.rsp, color = WidgetOnSurface)
                RemoteText(batteryLine.rs, color = WidgetOnSurface, fontSize = 13.rsp)
            }
            // Progress bar — simulated with nested boxes
            if (batteryProgress != null) {
                RemoteSpacer(modifier = RemoteModifier.height(4.rdp))
                RemoteBox(
                    modifier = RemoteModifier.fillMaxWidth().height(4.rdp).background(WidgetTrack),
                ) {
                    RemoteBox(
                        modifier = RemoteModifier
                            .fillMaxWidth(batteryProgress)
                            .height(4.rdp)
                            .background(WidgetPrimary),
                    ) {}
                }
            }
            RemoteSpacer(modifier = RemoteModifier.height(4.rdp))
        }

        // Storage line
        if (storageLine != null) {
            RemoteRow {
                RemoteText("\uD83D\uDCBE ".rs, fontSize = 11.rsp, color = WidgetOnSurfaceVariant)
                RemoteText(storageLine.rs, color = WidgetOnSurfaceVariant, fontSize = 11.rsp)
            }
        }

        if (staleLabel != null) {
            RemoteSpacer(modifier = RemoteModifier.height(4.rdp))
            RemoteText(staleLabel.rs, color = WidgetWarning, fontSize = 11.rsp)
        }
    }
}
