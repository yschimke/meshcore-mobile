package ee.schimke.meshcore.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import ee.schimke.meshcore.app.MeshcoreApp
import ee.schimke.meshcore.app.widget.goAsync

/**
 * Single-shot debug actions:
 *
 *   DEBUG_DISCONNECT                            — cancel the active connection
 *   DEBUG_FORGET --es id <saved-device-id>      — remove a saved device from Room
 */
class DebugActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? MeshcoreApp ?: return
        when (intent.action) {
            ACTION_DISCONNECT -> {
                Log.i(TAG, "disconnect")
                app.connectionController.cancel()
            }
            ACTION_FORGET -> {
                val id = intent.getStringExtra("id") ?: run {
                    Log.w(TAG, "DEBUG_FORGET missing --es id <device-id>")
                    return
                }
                Log.i(TAG, "forget $id")
                goAsync { app.repository.forgetDevice(id) }
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
