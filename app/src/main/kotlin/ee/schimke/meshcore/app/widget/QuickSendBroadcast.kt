package ee.schimke.meshcore.app.widget

import android.content.Context
import ee.schimke.meshcore.app.MeshcoreApp
import ee.schimke.meshcore.core.manager.ManagerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Placeholder "tap to send" action for the QuickSend widget. In a real
 * app this would be configured per-instance (target pubkey + preset
 * text). Here we just fire a hello message at the first contact.
 */
object QuickSendBroadcast {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun onTap(context: Context) {
        val manager = MeshcoreApp.get().manager
        val state = manager.state.value as? ManagerState.Connected ?: return
        val client = state.client
        val target = client.contacts.value.firstOrNull() ?: return
        scope.launch {
            runCatching {
                client.sendText(
                    recipient = target.publicKey,
                    text = "Hello from widget",
                    timestamp = kotlin.time.Clock.System.now(),
                )
            }
        }
    }
}
