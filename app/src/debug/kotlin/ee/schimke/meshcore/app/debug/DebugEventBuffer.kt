package ee.schimke.meshcore.app.debug

import ee.schimke.meshcore.app.MeshcoreApp
import ee.schimke.meshcore.app.connection.ConnectionUiState
import ee.schimke.meshcore.core.client.MeshCoreClient
import ee.schimke.meshcore.core.model.MeshEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Keeps the last [capacity] `MeshEvent`s observed from the currently
 * connected [MeshCoreClient]. Subscribes/unsubscribes automatically as
 * connections come and go. Purely for `--events` debug dumps; never
 * drives app behavior.
 */
internal class DebugEventBuffer(private val capacity: Int = 100) {

    data class Entry(val timestampMs: Long, val event: MeshEvent)

    private val ring = ArrayDeque<Entry>(capacity)
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var currentCollectJob: Job? = null
    private var currentClient: MeshCoreClient? = null

    fun attach(app: MeshcoreApp) {
        scope.launch {
            app.connectionController.state.collect { state ->
                val client = (state as? ConnectionUiState.Connected)?.client
                if (client === currentClient) return@collect
                currentCollectJob?.cancel()
                currentClient = client
                if (client != null) {
                    currentCollectJob = scope.launch {
                        client.events.collect { ev ->
                            synchronized(lock) {
                                while (ring.size >= capacity) ring.removeFirst()
                                ring.addLast(Entry(System.currentTimeMillis(), ev))
                            }
                        }
                    }
                }
            }
        }
    }

    fun snapshot(n: Int = capacity): List<Entry> = synchronized(lock) {
        if (n >= ring.size) ring.toList() else ring.takeLast(n)
    }
}
