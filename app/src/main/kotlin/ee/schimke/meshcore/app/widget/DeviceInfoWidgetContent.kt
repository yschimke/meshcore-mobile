@file:SuppressLint("RestrictedApi")

package ee.schimke.meshcore.app.widget

import android.annotation.SuppressLint
import android.view.View
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.remote.core.RemoteContext
import androidx.compose.remote.creation.compose.capture.LocalRemoteComposeCreationState
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.modifier.visibility
import androidx.compose.remote.creation.compose.state.RemoteBoolean
import androidx.compose.remote.creation.compose.state.RemoteDp
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemoteInt
import androidx.compose.remote.creation.compose.state.RemoteString
import androidx.compose.remote.creation.compose.state.rb
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.ri
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.remote.material3.RemoteIcon

@Composable
@RemoteComposable
fun DeviceInfoWidgetContentDynamic(
    deviceName: RemoteString,
    pubkeyPrefix: RemoteString,
    radioInfo: RemoteString,
    batteryLine: RemoteString,
    batteryProgress: RemoteFloat = (-1).rf,
    batteryWarn: RemoteBoolean = false.rb,
    staleLabel: RemoteString,
    storageLine: RemoteString,
) {
    RemoteColumn(
        modifier = cardModifier(),
        verticalArrangement = RemoteArrangement.spacedBy(10.rdp),
    ) {
        RemoteText(deviceName, color = OnSurface, fontSize = 20.rsp)

        RemoteRow(
            horizontalArrangement = RemoteArrangement.spacedBy(4.rdp),
            modifier = RemoteModifier.visibility(pubkeyPrefix.isNotEmpty.toVisibility())
        ) {
            RemoteIcon(
                imageVector = Icons.Rounded.Fingerprint,
                contentDescription = null,
                tint = OnSurfaceVariant,
                modifier = RemoteModifier.size(12.rdp)
            )
            RemoteText(pubkeyPrefix, color = OnSurfaceVariant, fontSize = 14.rsp)
        }

        RemoteRow(
            horizontalArrangement = RemoteArrangement.spacedBy(4.rdp),
            modifier = RemoteModifier.visibility(radioInfo.isNotEmpty.toVisibility())
        ) {
            RemoteIcon(
                imageVector = Icons.Rounded.Wifi,
                contentDescription = null,
                tint = OnSurfaceVariant,
                modifier = RemoteModifier.size(12.rdp)
            )
            RemoteText(radioInfo, color = OnSurfaceVariant, fontSize = 14.rsp, maxLines = 1)
        }

        val batteryColor = batteryWarn.select(Tertiary, OnSurface)
        val barColor = batteryWarn.select(Tertiary, Primary)

        RemoteRow(
            horizontalArrangement = RemoteArrangement.spacedBy(4.rdp),
            modifier = RemoteModifier.visibility(batteryLine.isNotEmpty.toVisibility())
        ) {
            val percent = (batteryProgress * 100f)
            val icon = Icons.Rounded.BatteryFull
            // TODO
//            when {
//                    percent >= 80 -> Icons.Rounded.BatteryFull
//                    percent >= 50 -> Icons.Rounded.Battery6Bar
//                    percent >= 25 -> Icons.Rounded.Battery3Bar
//                    else -> Icons.Rounded.Battery1Bar
//                }
            RemoteIcon(
                imageVector = icon,
                contentDescription = null,
                tint = batteryColor,
                modifier = RemoteModifier.size(12.rdp)
            )
            RemoteText(batteryLine, color = batteryColor, fontSize = 14.rsp)
        }
        // Progress bar
        RemoteBox(
            modifier = RemoteModifier.fillMaxWidth().height(4.rdp)
                .background(SurfaceContainerHighest)
                .visibility((batteryProgress ge (0.rf)).toVisibility()),
        ) {
            RemoteBox(
                modifier = RemoteModifier
                    .fillMaxWidth(batteryProgress)
                    .height(4.rdp)
                    .background(barColor),
            ) {}
        }

        RemoteRow(
            horizontalArrangement = RemoteArrangement.spacedBy(4.rdp),
            modifier = RemoteModifier.visibility(radioInfo.isNotEmpty.toVisibility())
        ) {
            RemoteIcon(
                imageVector = Icons.Rounded.Storage,
                contentDescription = null,
                tint = OnSurfaceVariant,
                modifier = RemoteModifier.size(12.rdp)
            )
            RemoteText(storageLine, color = OnSurfaceVariant, fontSize = 12.rsp)
        }

        // Stale indicator
        RemoteText(
            staleLabel,
            color = Warning,
            fontSize = 12.rsp,
            modifier = RemoteModifier.visibility(
                staleLabel.isEmpty.toVisibility()
            )
        )
    }
}

fun RemoteBoolean.toVisibility(): RemoteInt {
    return this.select(
        View.VISIBLE.ri, View.GONE.ri
    )
}

@Composable
@RemoteComposable
fun FunWithExpressions() {
    // Compose functions take RemoteDp
    val padding: RemoteDp = 4.rdp

    // Canvas operations need RemoteFloat Px
    val paddingPx: RemoteFloat = padding.toPx()

    // expanding it out
    val paddingPx2: RemoteFloat = 4.rf * LocalRemoteComposeCreationState.current.remoteDensity.density
    val paddingPx3: RemoteFloat = 4.rf * RemoteFloat(RemoteContext.FLOAT_DENSITY)
}

@Composable
@RemoteComposable
fun FunWithExpressions2(batteryLine: RemoteString, batteryPct: RemoteFloat) {
    val hasBatteryLine: RemoteBoolean = batteryLine.isNotEmpty

    fun RemoteBoolean.toVisibility(): RemoteInt {
        return this.select(
            View.VISIBLE.ri, View.GONE.ri
        )
    }

    val batteryColor = (batteryPct lt 20.rf).select(Color.Red.rc, Color.Black.rc)

    RemoteText(
        batteryLine,
        modifier = RemoteModifier.visibility(hasBatteryLine.toVisibility()),
        color = batteryColor
    )
}