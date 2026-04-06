package ee.schimke.meshcore.core.manager

import ee.schimke.meshcore.core.client.MeshCoreClient
import ee.schimke.meshcore.core.transport.Transport
import ee.schimke.meshcore.core.transport.TransportState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

/**
 * Thin, transport-agnostic orchestrator: given any [Transport] instance
 * (BLE, USB serial, TCP – or a test double), opens it, spins up a
 * [MeshCoreClient], exposes connection state as a [StateFlow], and tears
 * everything down on disconnect.
 *
 * Lives in commonMain because it knows nothing about Android or the JVM.
 * Platform-specific concerns (runtime permissions, discovering USB ports,
 * etc.) belong in platform modules such as `meshcore-mobile`.
 */
class MeshCoreManager(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow<ManagerState>(ManagerState.Idle)
    val state: StateFlow<ManagerState> = _state.asStateFlow()

    @Volatile private var currentTransport: Transport? = null
    @Volatile private var currentClient: MeshCoreClient? = null
    private var transportStateJob: Job? = null

    /**
     * Serialises [connect]/[disconnect] calls so callers that fire a new
     * connect while a previous one is still in-flight can't drive the
     * state machine into a weird intermediate state. The incoming call
     * tears down the previous attempt and takes over.
     */
    private val lifecycleMutex = Mutex()

    /** Job representing the currently in-flight [connect] invocation, if any. */
    @Volatile private var inflightConnectJob: Job? = null

    val client: MeshCoreClient? get() = currentClient

    /**
     * Connect using [transport]. Any existing session (including an
     * in-flight connect attempt) is cancelled first. On success [state]
     * transitions to [ManagerState.Connected].
     */
    suspend fun connect(transport: Transport) {
        // Cancel any in-flight connect first so the new call doesn't queue
        // up behind a 20-second BLE timeout.
        inflightConnectJob?.cancel(CancellationException("superseded by new connect()"))
        val callerJob = coroutineContext[Job]
        inflightConnectJob = callerJob
        try {
            lifecycleMutex.withLock {
                disconnectLocked()
                _state.value = ManagerState.Connecting
                currentTransport = transport
                transportStateJob = scope.launch {
                    transport.state.collect { ts ->
                        when (ts) {
                            is TransportState.Error ->
                                _state.value = ManagerState.Failed(ts.cause)
                            TransportState.Disconnected -> {
                                if (_state.value is ManagerState.Connected) {
                                    _state.value = ManagerState.Idle
                                }
                            }
                            else -> Unit
                        }
                    }
                }
                try {
                    transport.connect()
                } catch (t: Throwable) {
                    _state.value = ManagerState.Failed(t)
                    throw t
                }
                val client = MeshCoreClient(transport, scope)
                try {
                    client.start()
                } catch (t: Throwable) {
                    try { client.stop() } catch (_: Throwable) {}
                    _state.value = ManagerState.Failed(t)
                    throw t
                }
                currentClient = client
                _state.value = ManagerState.Connected(client)
            }
        } finally {
            if (inflightConnectJob === callerJob) inflightConnectJob = null
        }
    }

    suspend fun disconnect() {
        inflightConnectJob?.cancel(CancellationException("disconnect requested"))
        // Force-close the transport outside the mutex. Some BLE stacks
        // (notably Kable on older Android) don't cooperatively cancel
        // a connect() that's mid-GATT-handshake — closing the
        // underlying transport unsticks them. `close()` is expected to
        // be idempotent so `disconnectLocked` below will no-op the
        // second call.
        try { currentTransport?.close() } catch (_: Throwable) {}
        lifecycleMutex.withLock { disconnectLocked() }
    }

    /**
     * Fire-and-forget wrapper around [disconnect] that launches on the
     * manager's own scope. Use this from UI click handlers so the
     * teardown survives the composition being unmounted during
     * navigation — otherwise the click scope is cancelled mid-disconnect
     * and the transport is left stuck.
     */
    fun requestDisconnect() {
        scope.launch { runCatching { disconnect() } }
    }

    private suspend fun disconnectLocked() {
        transportStateJob?.cancel()
        transportStateJob = null
        currentClient?.stop()
        currentClient = null
        val t = currentTransport
        currentTransport = null
        try { t?.close() } catch (_: Throwable) {}
        if (_state.value !is ManagerState.Failed) _state.value = ManagerState.Idle
    }
}

sealed class ManagerState {
    object Idle : ManagerState()
    object Connecting : ManagerState()
    data class Connected(val client: MeshCoreClient) : ManagerState()
    data class Failed(val cause: Throwable) : ManagerState()
}
