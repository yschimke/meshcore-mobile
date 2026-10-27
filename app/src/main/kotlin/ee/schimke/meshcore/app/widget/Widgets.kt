@file:SuppressLint("RestrictedApi")

package ee.schimke.meshcore.app.widget

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import ee.schimke.composeai.rcwidget.creation.RcWidgetCaptureSize
import ee.schimke.composeai.rcwidget.creation.rcWidgetHostAction
import ee.schimke.composeai.rcwidget.launcher.RcLauncherWidgets
import ee.schimke.composeai.rcwidget.launcher.RcWidgetSizing
import ee.schimke.composeai.rcwidget.launcher.captureLauncherWidget
import ee.schimke.meshcore.app.di.appGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val STALE_THRESHOLD_MS = 60 * 60 * 1000L // 1 hour

private fun staleLabel(snap: WidgetSnapshot): String? {
  if (snap.connected) return null
  val updated = snap.lastUpdatedMs ?: return null
  val elapsed = System.currentTimeMillis() - updated
  if (elapsed < STALE_THRESHOLD_MS) return null
  val hours = elapsed / (60 * 60 * 1000)
  return if (hours < 24) "Updated ${hours}h ago" else "Updated ${hours / 24}d ago"
}

/**
 * Get the current widget snapshot, seeding from Room if the in-memory bridge hasn't been populated
 * yet.
 */
private suspend fun currentSnapshot(context: Context): WidgetSnapshot {
  val snap = WidgetStateBridge.snapshot.value
  if (snap.batteryMv != null || snap.deviceName != null) return snap

  val app = runCatching { context.appGraph() }.getOrNull() ?: return snap
  val fav = app.repository.observeFavorite().first() ?: return snap
  val state = app.repository.getDeviceState(fav.id) ?: return snap

  return snap.copy(
    deviceName = state.selfName,
    pubkeyPrefix =
      state.selfPublicKey?.let { bytes -> bytes.joinToString("") { "%02x".format(it) }.take(16) },
    batteryMv = state.batteryMillivolts,
    batteryPercent =
      state.batteryMillivolts?.let {
        ee.schimke.meshcore.core.model
          .BatteryInfo(
            it,
            state.storageUsedKb ?: 0,
            state.storageTotalKb ?: 0,
          )
          .estimatePercent()
      },
    storageUsedKb = state.storageUsedKb,
    storageTotalKb = state.storageTotalKb,
    frequencyMhz = state.radioFrequencyHz?.let { it / 1_000_000.0 },
    bandwidthKhz = state.radioBandwidthHz?.let { it / 1000 },
    spreadingFactor = state.radioSpreadingFactor,
    codingRate = state.radioCodingRate,
    lastUpdatedMs =
      maxOf(
          state.selfInfoFetchedAtMs,
          state.batteryFetchedAtMs,
          state.radioFetchedAtMs,
        )
        .takeIf { it > 0 },
  )
}

// --- Device Info Widget -----------------------------------------------------

/** The payload of a tap on the card: open the app. */
private const val OPEN_APP = "open"

/**
 * The device info widget for [snap] at [size], published as a launcher widget: captured at the
 * widget's own slot for this launcher ([RcLauncherWidgets.profileFor]), a tap on the card opening
 * the app.
 */
@RequiresApi(RcLauncherWidgets.MIN_HOST_API)
internal suspend fun deviceInfoRemoteViews(
  context: Context,
  widgetId: Int,
  size: RcWidgetCaptureSize,
  snap: WidgetSnapshot,
): RemoteViews {
  val capture =
    withContext(Dispatchers.Main) {
      captureLauncherWidget(context, size) {
        val pct = snap.batteryPercent
        DeviceInfoWidgetContent(
          deviceName = snap.deviceName ?: "No device",
          pubkeyPrefix = snap.pubkeyPrefix,
          radioInfo = snap.radioLine,
          batteryLine =
            pct?.let {
              val mv = snap.batteryMv?.let { " · $it mV" } ?: ""
              "$it%$mv"
            },
          batteryProgress = pct?.let { it / 100f },
          batteryWarn = pct != null && pct < 30,
          storageLine = snap.storageLine,
          staleLabel = staleLabel(snap),
          onTap = rcWidgetHostAction(name = OPEN_APP, payload = OPEN_APP),
        )
      }
    }
  val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
  val taps =
    if (launch == null) emptyMap()
    else
      capture.actions.mapValues { (actionId, _) ->
        RcLauncherWidgets.actionActivity(context, Intent(launch), widgetId, actionId)
      }
  return RcLauncherWidgets.remoteViews(capture.bytes, taps)
}

class DeviceInfoWidgetReceiver : AppWidgetProvider() {
  override fun onUpdate(context: Context, wm: AppWidgetManager, widgetIds: IntArray) {
    update(context, wm, widgetIds)
  }

  /** Resized: its breakpoints and fill follow the new slot, so capture it again. */
  override fun onAppWidgetOptionsChanged(
    context: Context,
    wm: AppWidgetManager,
    widgetId: Int,
    newOptions: Bundle,
  ) {
    update(context, wm, intArrayOf(widgetId))
  }

  private fun update(context: Context, wm: AppWidgetManager, widgetIds: IntArray) {
    // The receiver is only enabled where the launcher plays Remote Compose (values-v37).
    if (Build.VERSION.SDK_INT < RcLauncherWidgets.MIN_HOST_API) return
    goAsync {
      val snap = currentSnapshot(context)
      coroutineScope {
        widgetIds.forEach { id ->
          launch {
            val size = RcWidgetSizing.slotOf(wm, id)
            wm.updateAppWidget(id, deviceInfoRemoteViews(context, id, size, snap))
          }
        }
      }
    }
  }

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == REFRESH_ACTION) {
      WidgetRefreshWorker.enqueue(context)
    }
    super.onReceive(context, intent)
  }

  companion object {
    const val REFRESH_ACTION = "ee.schimke.meshcore.app.WIDGET_REFRESH"
  }
}
