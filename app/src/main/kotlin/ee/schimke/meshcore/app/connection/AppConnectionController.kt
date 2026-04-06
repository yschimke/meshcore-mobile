package ee.schimke.meshcore.app.connection

import ee.schimke.meshcore.app.data.DeviceSnapshot
import ee.schimke.meshcore.app.data.SavedDevice
import ee.schimke.meshcore.app.data.SavedDevicesRepository
import ee.schimke.meshcore.app.data.SavedTransport
import ee.schimke.meshcore.app.data.Timestamped
import ee.schimke.meshcore.app.data.bleDeviceId
import ee.schimke.meshcore.app.data.tcpDeviceId
import ee.schimke.meshcore.app.data.usbDeviceId
import ee.schimke.meshcore.core.manager.ManagerState
import ee.schimke.meshcore.core.manager.MeshCoreManager
import ee.schimke.meshcore.core.transport.Transport
import ee.schimke.meshcore.transport.ble.BleTransport
import ee.schimke.meshcore.transport.tcp.TcpTransport
import ee.schimke.meshcore.transport.usb.UsbSerialTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeoutException

/**
 * Single entry point for all connection lifecycle operations.
 *
 * Responsibilities:
 *  - Owns a [CoroutineScope] that is **independent of the UI**, so a
 *    screen being disposed mid-connect doesn't cancel the attempt.
 *  - Records a device entry in the repository on every successful
 *    connect so the Saved tab can offer quick reconnects.
 *  - Enforces a client-side connect timeout so a stuck BLE pairing
 *    surfaces a visible failure instead of a frozen spinner.
 *  - Publishes a single [ConnectionUiState] stream that the UI
 *    observes. The UI never imports [MeshCoreManager] or
 *    [ManagerState] directly.
 *
 * All public methods are **non-suspend**: they submit work to the
 * controller's scope and return immediately. UI click handlers can
 * call them without creating their own `rememberCoroutineScope`.
 */
class AppConnectionController(
    private val manager: MeshCoreManager,
    private val savedDevices: SavedDevicesRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow<ConnectionUiState>(ConnectionUiState.Idle)
    val state: StateFlow<ConnectionUiState> = _state.asStateFlow()

    /** The saved-device ID of the currently connected (or connecting) device, if any. */
    private val _connectedDeviceId = MutableStateFlow<String?>(null)
    val connectedDeviceId: StateFlow<String?> = _connectedDeviceId.asStateFlow()

    private val connectTimeoutMs: Long = 20_000L

    @Volatile
    private var inFlight: Job? = null

    /** The (id, transport, label) we're currently attempting, for recording on success. */
    @Volatile
    private var currentAttempt: Attempt? = null

    init {
        scope.launch {
            manager.state.collect { ms ->
                when (ms) {
                    is ManagerState.Connected -> {
                        val attempt = currentAttempt
                        if (attempt != null) {
                            _connectedDeviceId.value = attempt.id
                            // Seed the client with cached snapshot data so
                            // the UI shows previous session's contacts/channels
                            // immediately while fresh data loads.
                            runCatching {
                                val saved = savedDevices.get(attempt.id)
                                saved?.snapshot?.let { snap ->
                                    ms.client.seedFromCache(
                                        selfInfo = snap.selfInfo?.value,
                                        contacts = snap.contacts?.value,
                                        battery = snap.battery?.value,
                                        radio = snap.radio?.value,
                                        deviceInfo = snap.deviceInfo?.value,
                                        channels = snap.channels?.value,
                                    )
                                }
                            }
                            runCatching {
                                savedDevices.upsert(
                                    id = attempt.id,
                                    label = attempt.label,
                                    transport = attempt.savedTransport,
                                )
                            }
                        }
                        _state.value = ConnectionUiState.Connected(ms.client)
                        // Fetch contacts and persist a full snapshot in
                        // the background so the Saved tab has cached
                        // device info for next time.
                        if (attempt != null) {
                            scope.launch { fetchAndPersistSnapshot(attempt.id, ms.client) }
                        }
                    }
                    is ManagerState.Failed -> {
                        _connectedDeviceId.value = null
                        if (_state.value !is ConnectionUiState.Failed) {
                            _state.value = ConnectionUiState.Failed(
                                cause = ms.cause,
                                deviceLabel = currentAttempt?.label ?: currentLabel(),
                            )
                        }
                    }
                    ManagerState.Idle -> {
                        if (_state.value is ConnectionUiState.Connected) {
                            _connectedDeviceId.value = null
                            _state.value = ConnectionUiState.Idle
                        }
                    }
                    ManagerState.Connecting -> Unit
                }
            }
        }
    }

    private fun currentLabel(): String? = when (val s = _state.value) {
        is ConnectionUiState.Connecting -> s.deviceLabel
        else -> null
    }

    // --- Public request API --------------------------------------------------

    /**
     * Begin connecting to [request]. Dispatches immediately for all
     * transport types.
     */
    fun requestConnect(request: ConnectionRequest) {
        inFlight?.cancel()
        inFlight = scope.launch {
            when (request) {
                is ConnectionRequest.Ble -> {
                    val id = bleDeviceId(request.adv.identifier)
                    doConnect(
                        attempt = Attempt(
                            id = id,
                            label = request.label,
                            transport = BleTransport(request.adv),
                            savedTransport = SavedTransport.Ble(
                                identifier = request.adv.identifier,
                                advertName = request.adv.name,
                            ),
                        ),
                    )
                }
                is ConnectionRequest.Tcp -> doConnect(
                    attempt = Attempt(
                        id = tcpDeviceId(request.host, request.port),
                        label = request.label,
                        transport = TcpTransport(request.host, request.port),
                        savedTransport = SavedTransport.Tcp(request.host, request.port),
                    ),
                )
                is ConnectionRequest.Usb -> {
                    val className = request.port::class.simpleName ?: "usb"
                    val vid = runCatching { request.port.vendorId }.getOrDefault(-1)
                    val pid = runCatching { request.port.productId }.getOrDefault(-1)
                    doConnect(
                        attempt = Attempt(
                            id = usbDeviceId(className, vid, pid),
                            label = request.label,
                            transport = UsbSerialTransport(request.port),
                            savedTransport = SavedTransport.Usb(className, vid, pid),
                        ),
                    )
                }
            }
        }
    }

    /**
     * Reconnect to a device already in the saved-devices store. USB is
     * not supported by this path because USB ports aren't stably
     * identifiable across replugs — users must pick the live port
     * from the USB tab each time.
     */
    fun requestReconnect(saved: SavedDevice) {
        inFlight?.cancel()
        inFlight = scope.launch {
            val (transport, savedTransport) = when (val t = saved.transport) {
                is SavedTransport.Ble -> {
                    BleTransport.fromIdentifier(t.identifier) to t
                }
                is SavedTransport.Tcp -> TcpTransport(t.host, t.port) to t
                is SavedTransport.Usb -> {
                    _state.value = ConnectionUiState.Failed(
                        cause = IllegalStateException(
                            "USB devices must be reconnected from the USB tab — " +
                                "the port isn't stable across replugs.",
                        ),
                        deviceLabel = saved.label,
                    )
                    return@launch
                }
            }
            doConnect(
                attempt = Attempt(
                    id = saved.id,
                    label = saved.label,
                    transport = transport,
                    savedTransport = savedTransport,
                ),
            )
        }
    }

    fun cancel() {
        inFlight?.cancel()
        // Set Idle immediately so the UI navigates back without waiting
        // for the async transport teardown to complete.
        _connectedDeviceId.value = null
        _state.value = ConnectionUiState.Idle
        currentAttempt = null
        scope.launch { runCatching { manager.disconnect() } }
    }

    fun dismissError() {
        if (_state.value is ConnectionUiState.Failed) {
            _state.value = ConnectionUiState.Idle
        }
    }

    // --- Saved device management (thin passthrough) --------------------------

    fun forgetSavedDevice(id: String) {
        scope.launch { savedDevices.forget(id) }
    }

    fun toggleFavorite(id: String) {
        scope.launch {
            val current = savedDevices.snapshot().firstOrNull { it.favorite }
            savedDevices.setFavorite(if (current?.id == id) null else id)
        }
    }

    // --- Internals -----------------------------------------------------------

    private suspend fun doConnect(attempt: Attempt) {
        currentAttempt = attempt
        val startedAt = System.currentTimeMillis()
        _state.value = ConnectionUiState.Connecting(
            startedAtMs = startedAt,
            timeoutMs = connectTimeoutMs,
            deviceLabel = attempt.label,
        )
        val completed = withTimeoutOrNull(connectTimeoutMs) {
            runCatching { manager.connect(attempt.transport) }
        }
        when {
            completed == null -> {
                runCatching { manager.disconnect() }
                _state.value = ConnectionUiState.Failed(
                    cause = TimeoutException(
                        "No response from ${attempt.label} within ${connectTimeoutMs / 1000}s. " +
                            "The device may be out of range or powered off.",
                    ),
                    deviceLabel = attempt.label,
                )
                currentAttempt = null
            }
            completed.isFailure -> {
                _state.value = ConnectionUiState.Failed(
                    cause = completed.exceptionOrNull() ?: IllegalStateException("unknown"),
                    deviceLabel = attempt.label,
                )
                currentAttempt = null
            }
            else -> {
                // Success path: manager.state collector will publish
                // Connected and persist the saved-device record.
            }
        }
    }

    private suspend fun fetchAndPersistSnapshot(
        deviceId: String,
        client: ee.schimke.meshcore.core.client.MeshCoreClient,
    ) {
        runCatching {
            // Fetch contacts + channels (selfInfo, battery, radio already fetched by start())
            runCatching { client.getContacts() }
            runCatching { client.getChannels() }
            val now = System.currentTimeMillis()
            val snapshot = DeviceSnapshot(
                selfInfo = client.selfInfo.value?.let { Timestamped(it, now) },
                contacts = Timestamped(client.contacts.value, now),
                battery = client.battery.value?.let { Timestamped(it, now) },
                radio = client.radio.value?.let { Timestamped(it, now) },
                deviceInfo = client.device.value?.let { Timestamped(it, now) },
                channels = Timestamped(client.channels.value, now),
            )
            savedDevices.updateSnapshot(deviceId, snapshot)
        }
    }

    private data class Attempt(
        val id: String,
        val label: String,
        val transport: Transport,
        val savedTransport: SavedTransport,
    )
}
