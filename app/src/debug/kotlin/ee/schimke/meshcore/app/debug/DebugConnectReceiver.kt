package ee.schimke.meshcore.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import ee.schimke.meshcore.app.MeshcoreApp
import ee.schimke.meshcore.app.widget.goAsync
import ee.schimke.meshcore.data.repository.SavedDevice
import ee.schimke.meshcore.data.repository.SavedTransport
import ee.schimke.meshcore.data.repository.bleDeviceId
import ee.schimke.meshcore.data.repository.tcpDeviceId

/**
 * `adb shell am broadcast -p ee.schimke.meshcore
 *    -a ee.schimke.meshcore.DEBUG_CONNECT [extras]`
 *
 * Extras (pick one form):
 *   --es ble <identifier> [--es label "<name>"]
 *   --es tcp <host> --ei port <port>
 *
 * Target must be in [DebugAllowlists.bleIdentifiers] or
 * [DebugAllowlists.tcpTargets] (format `host:port`).
 */
class DebugConnectReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) {
            Log.w(TAG, "ignoring action=${intent.action}")
            return
        }
        val app = context.applicationContext as? MeshcoreApp ?: run {
            Log.w(TAG, "no MeshcoreApp"); return
        }

        val resolved = resolve(intent) ?: run {
            Log.w(TAG, "missing/invalid extras — expected --es ble <id> or --es tcp <host> --ei port <p>")
            return
        }
        if (!resolved.allowed) {
            Log.w(TAG, "refused: '${resolved.allowlistKey}' not in the DebugAllowlists entry; " +
                "edit app/src/debug/.../DebugAllowlists.kt and rebuild")
            return
        }

        Log.i(TAG, "connect → ${resolved.device.id} (label='${resolved.device.label}')")
        goAsync {
            app.connectionController.requestReconnect(resolved.device)
        }
    }

    private data class Resolved(
        val device: SavedDevice,
        val allowlistKey: String,
        val allowed: Boolean,
    )

    private fun resolve(intent: Intent): Resolved? {
        intent.getStringExtra("ble")?.let { id ->
            val label = intent.getStringExtra("label") ?: id
            val device = SavedDevice(
                id = bleDeviceId(id),
                label = label,
                transport = SavedTransport.Ble(id, label),
                favorite = false,
                lastConnectedAtMs = 0L,
            )
            return Resolved(device, id, id in DebugAllowlists.bleIdentifiers)
        }
        intent.getStringExtra("tcp")?.let { host ->
            val port = intent.getIntExtra("port", -1)
            if (port <= 0) return null
            val key = "$host:$port"
            val device = SavedDevice(
                id = tcpDeviceId(host, port),
                label = key,
                transport = SavedTransport.Tcp(host, port),
                favorite = false,
                lastConnectedAtMs = 0L,
            )
            return Resolved(device, key, key in DebugAllowlists.tcpTargets)
        }
        return null
    }

    companion object {
        const val ACTION = "ee.schimke.meshcore.DEBUG_CONNECT"
        private const val TAG = "DebugConnect"
    }
}
