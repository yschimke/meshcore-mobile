package ee.schimke.meshcore.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.android.BroadcastReceiverKey
import ee.schimke.meshcore.app.connection.AppConnectionController
import ee.schimke.meshcore.app.widget.goAsync
import ee.schimke.meshcore.data.repository.MeshcoreRepository

/**
 * Single-shot debug actions:
 *
 * DEBUG_DISCONNECT — cancel the active connection DEBUG_FORGET --es id <saved-device-id> — remove a
 * saved device from Room
 */
@ContributesIntoMap(AppScope::class, binding<BroadcastReceiver>())
@BroadcastReceiverKey
@Inject
class DebugActionReceiver(
  private val connectionController: AppConnectionController,
  private val repository: MeshcoreRepository,
) : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    when (intent.action) {
      ACTION_DISCONNECT -> {
        Log.i(TAG, "disconnect")
        connectionController.cancel()
      }
      ACTION_FORGET -> {
        val id =
          intent.getStringExtra("id")
            ?: run {
              Log.w(TAG, "DEBUG_FORGET missing --es id <device-id>")
              return
            }
        Log.i(TAG, "forget $id")
        goAsync { repository.forgetDevice(id) }
      }
      else -> Log.w(TAG, "ignoring action=${intent.action}")
    }
  }

  companion object {
    const val ACTION_DISCONNECT = "ee.schimke.meshcore.DEBUG_DISCONNECT"
    const val ACTION_FORGET = "ee.schimke.meshcore.DEBUG_FORGET"
    private const val TAG = "DebugAction"
  }
}
