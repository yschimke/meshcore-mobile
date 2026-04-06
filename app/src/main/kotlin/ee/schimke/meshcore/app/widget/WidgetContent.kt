@file:SuppressLint("RestrictedApi")

package ee.schimke.meshcore.app.widget

import android.annotation.SuppressLint
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
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

// --- Exact MeshCore dark theme colors from MeshcoreTheme.kt -----------------
// These are the same values used by the Compose DeviceSummaryCard in dark mode.

private val SurfaceContainer = Color(0xFF1A211F).rc          // card background
private val OnSurface = Color(0xFFDDE4E1).rc                 // title, battery text
private val OnSurfaceVariant = Color(0xFFBEC9C5).rc          // pubkey, radio, storage
private val Primary = Color(0xFF53DBC9).rc                   // progress bar
private val Tertiary = Color(0xFFE0C38C).rc                  // low-battery warning
private val SurfaceContainerHighest = Color(0xFF2F3634).rc   // progress track
private val Warning = Color(0xFFFFB74D).rc                   // stale indicator

private fun cardModifier() = RemoteModifier
    .fillMaxSize()
    .background(SurfaceContainer)
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
    // Column with spacedBy(10dp) — matches Compose Arrangement.spacedBy(10.dp)
    RemoteColumn(
        modifier = cardModifier(),
        verticalArrangement = RemoteArrangement.spacedBy(10.rdp),
    ) {
        // Device name — titleLarge ~22sp
        RemoteText(deviceName.rs, color = OnSurface, fontSize = 20.rsp)

        // Pubkey — icon + bodyMedium ~14sp
        if (pubkeyPrefix != null) {
            RemoteRow {
                RemoteText("\uD83D\uDD11 ".rs, fontSize = 14.rsp, color = OnSurfaceVariant)
                RemoteText(pubkeyPrefix.rs, color = OnSurfaceVariant, fontSize = 14.rsp)
            }
        }

        // Radio — icon + bodyMedium ~14sp
        if (radioInfo != null) {
            RemoteRow {
                RemoteText("\uD83D\uDCE1 ".rs, fontSize = 14.rsp, color = OnSurfaceVariant)
                RemoteText(radioInfo.rs, color = OnSurfaceVariant, fontSize = 14.rsp)
            }
        }

        // Battery section — icon + text + progress bar (inner spacedBy 4dp)
        if (batteryLine != null) {
            val batteryColor = if (batteryWarn) Tertiary else OnSurface
            val barColor = if (batteryWarn) Tertiary else Primary

            RemoteColumn(verticalArrangement = RemoteArrangement.spacedBy(4.rdp)) {
                RemoteRow {
                    RemoteText("\uD83D\uDD0B ".rs, fontSize = 14.rsp, color = batteryColor)
                    RemoteText(batteryLine.rs, color = batteryColor, fontSize = 14.rsp)
                }
                // Progress bar
                if (batteryProgress != null) {
                    RemoteBox(
                        modifier = RemoteModifier.fillMaxWidth().height(4.rdp).background(SurfaceContainerHighest),
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
            RemoteRow {
                RemoteText("\uD83D\uDCBE ".rs, fontSize = 12.rsp, color = OnSurfaceVariant)
                RemoteText(storageLine.rs, color = OnSurfaceVariant, fontSize = 12.rsp)
            }
        }

        // Stale indicator
        if (staleLabel != null) {
            RemoteText(staleLabel.rs, color = Warning, fontSize = 12.rsp)
        }
    }
}
