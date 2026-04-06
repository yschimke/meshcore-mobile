@file:SuppressLint("RestrictedApi")

package ee.schimke.meshcore.app.widget

import android.annotation.SuppressLint
import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteSpacer
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// --- MeshCore dark-theme palette (teal seed #00695C) ------------------------
// These mirror the dark scheme in MeshcoreTheme.kt, which is the typical
// context for home-screen widgets.

private val WidgetSurface = Color(0xFF0E1513).rc           // SurfaceDark
private val WidgetSurfaceContainer = Color(0xFF1A211F).rc  // SurfaceContainerDark
private val WidgetOnSurface = Color(0xFFDDE4E1).rc         // OnSurfaceDark
private val WidgetPrimary = Color(0xFF53DBC9).rc           // TealPrimaryDark
private val WidgetSecondary = Color(0xFFB0CCC6).rc         // SlateSecondaryDark
private val WidgetOnSurfaceVariant = Color(0xFFBEC9C5).rc  // OnSurfaceVariantDark
private val WidgetPrimaryContainer = Color(0xFF005048).rc  // TealPrimaryContainerDark
private val WidgetOnPrimaryContainer = Color(0xFF74F8E5).rc // TealOnPrimaryContainerDark
private val WidgetWarning = Color(0xFFFFB74D).rc            // Amber warning

private fun widgetModifier() = RemoteModifier
    .fillMaxSize()
    .background(WidgetSurfaceContainer)
    .padding(12.rdp)

// --- Battery + SNR ----------------------------------------------------------

@Composable
@RemoteComposable
fun BatteryWidgetContent(
    batteryPercent: String,
    batteryMv: String?,
    snr: String?,
    staleLabel: String? = null,
) {
    RemoteColumn(
        modifier = widgetModifier(),
        verticalArrangement = RemoteArrangement.Center,
    ) {
        RemoteText("Battery".rs, color = WidgetOnSurfaceVariant)
        RemoteText(batteryPercent.rs, color = WidgetPrimary)
        if (batteryMv != null) {
            RemoteText(batteryMv.rs, color = WidgetOnSurface)
        }
        if (snr != null) {
            RemoteText(snr.rs, color = WidgetSecondary)
        }
        if (staleLabel != null) {
            RemoteText(staleLabel.rs, color = WidgetWarning)
        }
    }
}

// --- Mesh status (name + contact count + freq) ------------------------------

@Composable
@RemoteComposable
fun MeshStatusWidgetContent(
    deviceName: String,
    contactCount: String,
    frequencyMhz: String?,
    staleLabel: String? = null,
) {
    RemoteColumn(modifier = widgetModifier()) {
        RemoteText(deviceName.rs, color = WidgetPrimary)
        RemoteText(contactCount.rs, color = WidgetOnSurface)
        if (frequencyMhz != null) {
            RemoteText(frequencyMhz.rs, color = WidgetSecondary)
        }
        if (staleLabel != null) {
            RemoteText(staleLabel.rs, color = WidgetWarning)
        }
    }
}

// --- Last received message --------------------------------------------------

@Composable
@RemoteComposable
fun LastMessageWidgetContent(
    message: String,
) {
    RemoteColumn(modifier = widgetModifier()) {
        RemoteText("Last message".rs, color = WidgetOnSurfaceVariant)
        RemoteSpacer(modifier = RemoteModifier.height(4.rdp))
        RemoteText(message.rs, color = WidgetOnSurface)
    }
}

// --- Connection status ------------------------------------------------------

@Composable
@RemoteComposable
fun ConnectionStatusWidgetContent(
    status: String,
    deviceName: String?,
    lastSeen: String?,
    staleLabel: String? = null,
) {
    RemoteColumn(modifier = widgetModifier()) {
        RemoteText("Connection".rs, color = WidgetOnSurfaceVariant)
        RemoteText(status.rs, color = WidgetPrimary)
        if (deviceName != null) {
            RemoteText(deviceName.rs, color = WidgetOnSurface)
        }
        if (lastSeen != null) {
            RemoteText(lastSeen.rs, color = WidgetSecondary)
        }
        if (staleLabel != null) {
            RemoteSpacer(modifier = RemoteModifier.height(4.rdp))
            RemoteText(staleLabel.rs, color = WidgetWarning)
        }
    }
}
