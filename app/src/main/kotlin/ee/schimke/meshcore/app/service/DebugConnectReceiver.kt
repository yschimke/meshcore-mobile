package ee.schimke.meshcore.app.service

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
 * Targets allowed via [DebugConnectReceiver]. Defaults to empty so the
 * receiver is a no-op until a developer opts something in. Format
 * mirrors the device-id helpers:
 *
 *   "ble:<identifier>"   "tcp:<host>:<port>"   "saved:<device-id>"
 *
 * Keep this list short and intentional — the receiver is exported and,
 * while signature-permission-protected, anyone holding the signing key
 * (i.e. you) can fire it from a debug shell.
 */
private val ALLOWED_DEBUG_CONNECTS: Set<String> = setOf(
    "ble:C7:8D:8C:45:5F:78",
)

/**
 * `adb shell am broadcast` entry point that initiates a connection
 * without any UI interaction. Three forms:
 *
 *   am broadcast -p ee.schimke.meshcore \
 *     -a ee.schimke.meshcore.DEBUG_CONNECT \
 *     --es ble <identifier> [--es label "<name>"]
 *
 *   am broadcast -p ee.schimke.meshcore \
 *     -a ee.schimke.meshcore.DEBUG_CONNECT \
 *     --es tcp <host> --ei port <port>
 *
 *   am broadcast -p ee.schimke.meshcore \
 *     -a ee.schimke.meshcore.DEBUG_CONNECT \
 *     --es saved <device-id>
 *
 * The intent is rejected unless the resolved target key is in
 * [ALLOWED_DEBUG_CONNECTS]. Result lands in logcat under tag
 * [TAG] — `adb logcat -s DebugConnect` to follow.
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

        val target = resolveTarget(intent) ?: run {
            Log.w(TAG, "missing/invalid extras; expected ble|tcp|saved")
            return
        }
        if (target.key !in ALLOWED_DEBUG_CONNECTS) {
            Log.w(TAG, "refused: '${target.key}' not in ALLOWED_DEBUG_CONNECTS " +
                "(edit DebugConnectReceiver.kt and rebuild)")
            return
        }

        Log.i(TAG, "connect → ${target.key} (label='${target.label}')")
        goAsync {
            // requestReconnect is a fire-and-forget call; the connection
            // controller updates state asynchronously. Watch progress with:
            //   adb shell dumpsys -t 30 activity service MeshcoreConnectionService
            app.connectionController.requestReconnect(target.toSavedDevice())
        }
    }

    private fun resolveTarget(intent: Intent): Target? {
        intent.getStringExtra("ble")?.let { id ->
            val label = intent.getStringExtra("label") ?: id
            return Target(
                key = bleDeviceId(id),
                label = label,
                saved = SavedTransport.Ble(id, label),
                deviceId = bleDeviceId(id),
            )
        }
        intent.getStringExtra("tcp")?.let { host ->
            val port = intent.getIntExtra("port", -1)
            if (port <= 0) return null
            return Target(
                key = tcpDeviceId(host, port),
                label = "$host:$port",
                saved = SavedTransport.Tcp(host, port),
                deviceId = tcpDeviceId(host, port),
            )
        }
        intent.getStringExtra("saved")?.let { id ->
            // Saved-device path expects the controller to look it up;
            // this branch is mostly useful once a device is in the DB.
            return Target(
                key = id,
                label = id,
                saved = null,
                deviceId = id,
            )
        }
        return null
    }

    private data class Target(
        val key: String,
        val label: String,
        val saved: SavedTransport?,
        val deviceId: String,
    ) {
        fun toSavedDevice(): SavedDevice = SavedDevice(
            id = deviceId,
            label = label,
            transport = saved
                ?: error("saved-device lookup not yet implemented; pass --es ble|tcp instead"),
            favorite = false,
            lastConnectedAtMs = 0L,
        )
    }

    companion object {
        const val ACTION = "ee.schimke.meshcore.DEBUG_CONNECT"
        private const val TAG = "DebugConnect"
    }
}
