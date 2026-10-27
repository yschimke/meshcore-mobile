@file:SuppressLint("RestrictedApi")

package ee.schimke.meshcore.wear.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.remote.creation.compose.state.rc
import androidx.glance.wear.AssociateWithGlanceWearWidget
import androidx.glance.wear.GlanceWearWidget
import androidx.glance.wear.GlanceWearWidgetService
import androidx.glance.wear.WearWidgetBrush
import androidx.glance.wear.WearWidgetData
import androidx.glance.wear.color
import androidx.glance.wear.core.WearWidgetParams
import ee.schimke.composeai.rcwidget.creation.RcWearBackground
import ee.schimke.composeai.rcwidget.creation.rcWidgetHostAction
import ee.schimke.composeai.rcwidget.wear.RcHostFramedWearWidgetDocument
import ee.schimke.composeai.rcwidget.wear.RcTemplatedWearWidgetData
import ee.schimke.composeai.rcwidget.wear.RcWearWidgetTemplate
import ee.schimke.composeai.rcwidget.wear.withTaps
import ee.schimke.meshcore.grpc.ConnectionState
import ee.schimke.meshcore.wear.WearGraphHolder
import ee.schimke.meshcore.wear.WearMainActivity
import kotlinx.coroutines.withTimeoutOrNull

/** What the status widget shows: the radio as the phone last reported it. */
internal data class StatusWidgetState(
  val deviceName: String,
  val connected: Boolean,
  val batteryPercent: Int?,
  val contactCount: Int,
)

/**
 * The radio's status as a Wear widget, generated on the watch: the content is captured once per
 * render as a host-framed template ([RcWearWidgetTemplate.capture]) and fitted to this watch's
 * widget container ([RcTemplatedWearWidgetData]), so the same capture takes a round host's stadium
 * and a squircle's corners alike. A template that can't play as a widget falls back to a
 * host-framed Glance Wear capture of the same content. A tap opens the app.
 */
class StatusWidget : GlanceWearWidget() {
  override suspend fun provideWidgetData(
    context: Context,
    params: WearWidgetParams,
  ): WearWidgetData {
    val state = readState(context)
    val background = RcWearBackground(base = StatusWidgetSurface)
    val template =
      RcTemplatedWearWidgetData(
        template = { ctx, _ ->
          RcWearWidgetTemplate.capture(ctx, background = background) { Content(state) }
        },
        fallback =
          RcHostFramedWearWidgetDocument(WearWidgetBrush.color(StatusWidgetSurface.rc)) {
            Content(state)
          },
      )
    return template.withTaps(setOf(OPEN_APP)) { ctx, _ -> openApp(ctx) }
  }

  @androidx.compose.runtime.Composable
  private fun Content(state: StatusWidgetState) {
    StatusWidgetContent(
      deviceName = state.deviceName,
      connected = state.connected,
      batteryPercent = state.batteryPercent,
      contactCount = state.contactCount,
      framed = true,
      onTap = rcWidgetHostAction(name = OPEN_APP, payload = OPEN_APP),
    )
  }

  /** The status from the phone, bounded: a widget draws "not connected" rather than waiting. */
  private suspend fun readState(context: Context): StatusWidgetState {
    val graph = (context.applicationContext as? WearGraphHolder)?.wearGraph
    val client = graph?.meshClient ?: return DISCONNECTED
    return withTimeoutOrNull(DATA_LAYER_TIMEOUT_MS) {
      runCatching {
        if (client.getConnectionStatus().state != ConnectionState.CONNECTED) {
          return@runCatching RADIO_DISCONNECTED
        }
        val self = client.getSelfInfo()
        val battery = client.getBatteryInfo()
        val contacts = client.getContacts()
        StatusWidgetState(
          deviceName = self.name.ifEmpty { "MeshCore" },
          connected = true,
          batteryPercent = battery.estimatedPercent.takeIf { it > 0 },
          contactCount = contacts.contactsCount,
        )
      }
        .getOrNull()
    } ?: DISCONNECTED
  }

  private fun openApp(context: Context): PendingIntent =
    PendingIntent.getActivity(
      context,
      0,
      Intent(context, WearMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

  private companion object {
    /** The named tap the widget rewrites to its open-app `PendingIntent`. */
    const val OPEN_APP = "open"

    /** The longest a render waits on the phone: it is local IPC and normally answers at once. */
    const val DATA_LAYER_TIMEOUT_MS = 5_000L

    val DISCONNECTED = StatusWidgetState("Phone not connected", false, null, 0)
    val RADIO_DISCONNECTED = StatusWidgetState("Radio disconnected", false, null, 0)
  }
}

@AssociateWithGlanceWearWidget(StatusWidget::class)
class StatusWidgetService : GlanceWearWidgetService() {
  override val widget: GlanceWearWidget = StatusWidget()
}
