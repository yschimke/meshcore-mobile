package ee.schimke.meshcore.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import ee.schimke.meshcore.app.MainActivity
import kotlinx.coroutines.flow.first

// --- Battery + SNR --------------------------------------------------------

class BatteryWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetStateBridge.snapshot.first()
        provideContent { BatteryContent(snap) }
    }
}

class BatteryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BatteryWidget()
}

@Composable
private fun BatteryContent(snap: WidgetSnapshot) {
    GlanceTheme {
        Column(
            modifier = GlanceModifier.fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Battery", style = TextStyle(color = GlanceTheme.colors.onSurface))
            Text(
                text = snap.batteryPercent?.let { "$it%" } ?: "—",
                style = TextStyle(color = GlanceTheme.colors.primary),
            )
            snap.batteryMv?.let {
                Text("$it mV", style = TextStyle(color = GlanceTheme.colors.onSurface))
            }
            snap.lastSnr?.let {
                Text("SNR ${it}", style = TextStyle(color = GlanceTheme.colors.secondary))
            }
        }
    }
}

// --- Mesh status (name + contact count + freq) ---------------------------

class MeshStatusWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetStateBridge.snapshot.first()
        provideContent { MeshStatusContent(snap) }
    }
}

class MeshStatusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MeshStatusWidget()
}

@Composable
private fun MeshStatusContent(snap: WidgetSnapshot) {
    GlanceTheme {
        Column(
            modifier = GlanceModifier.fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Text(
                text = snap.deviceName ?: "Not connected",
                style = TextStyle(color = GlanceTheme.colors.primary),
            )
            Text(
                text = "${snap.contactCount} contacts",
                style = TextStyle(color = GlanceTheme.colors.onSurface),
            )
            snap.frequencyMhz?.let {
                Text(
                    text = "${"%.3f".format(it)} MHz",
                    style = TextStyle(color = GlanceTheme.colors.secondary),
                )
            }
        }
    }
}

// --- Last received message -----------------------------------------------

class LastMessageWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetStateBridge.snapshot.first()
        provideContent { LastMessageContent(snap) }
    }
}

class LastMessageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LastMessageWidget()
}

@Composable
private fun LastMessageContent(snap: WidgetSnapshot) {
    GlanceTheme {
        Column(
            modifier = GlanceModifier.fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Text("Last message", style = TextStyle(color = GlanceTheme.colors.secondary))
            Text(
                text = snap.lastMessage ?: "(none yet)",
                style = TextStyle(color = GlanceTheme.colors.onSurface),
            )
        }
    }
}

// --- Quick send -----------------------------------------------------------

class QuickSendWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetStateBridge.snapshot.first()
        provideContent { QuickSendContent(snap) }
    }
}

class QuickSendWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickSendWidget()
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == QUICKSEND_ACTION) {
            QuickSendBroadcast.onTap(context)
        }
        super.onReceive(context, intent)
    }
    companion object {
        const val QUICKSEND_ACTION = "ee.schimke.meshcore.app.QUICKSEND"
    }
}

@Composable
private fun QuickSendContent(snap: WidgetSnapshot) {
    GlanceTheme {
        Box(
            modifier = GlanceModifier.fillMaxSize()
                .background(GlanceTheme.colors.primaryContainer)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Quick send",
                style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer),
            )
        }
    }
}
