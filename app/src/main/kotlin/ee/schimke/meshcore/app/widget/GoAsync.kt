package ee.schimke.meshcore.app.widget

import android.content.BroadcastReceiver
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Execute [block] asynchronously in a scope tied to the broadcast lifetime.
 *
 * Adapted from the androidx Remote Compose demo / Glance CoroutineBroadcastReceiver.
 */
internal fun BroadcastReceiver.goAsync(
    coroutineContext: CoroutineContext = Dispatchers.Default,
    block: suspend CoroutineScope.() -> Unit,
) {
    val parentScope = CoroutineScope(coroutineContext)
    val pendingResult = goAsync()

    parentScope.launch {
        try {
            try {
                coroutineScope { this.block() }
            } catch (e: Throwable) {
                if (e is CancellationException && e.cause == null) {
                    // Regular cancellation, do nothing.
                } else {
                    println("BroadcastReceiver execution failed $e")
                }
            } finally {
                parentScope.cancel()
            }
        } finally {
            try {
                pendingResult.finish()
            } catch (e: IllegalStateException) {
                println("Error thrown when trying to finish broadcast $e")
            }
        }
    }
}
