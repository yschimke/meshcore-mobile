package ee.schimke.meshcore.app.connection

import ee.schimke.meshcore.app.data.SavedDevice
import ee.schimke.meshcore.app.data.SavedDevicesRepository
import ee.schimke.meshcore.app.data.SavedTransport
import ee.schimke.meshcore.app.data.bleDeviceId
import ee.schimke.meshcore.app.data.tcpDeviceId
import ee.schimke.meshcore.app.data.usbDeviceId
import ee.schimke.meshcore.core.manager.ManagerState
import ee.schimke.meshcore.core.manager.MeshCoreManager
import ee.schimke.meshcore.core.transport.BleTransport
import ee.schimke.meshcore.core.transport.TcpTransport
import ee.schimke.meshcore.core.transport.Transport
import ee.schimke.meshcore.core.transport.UsbSerialTransport
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Single entry point for all connection lifecycle operations.
 *
 * Responsibilities:
 *  - Owns a [CoroutineScope] that is **independent of the UI**, so a
 *    screen being disposed mid-connect doesn't cancel the attempt.
 *  - Looks up saved PINs via [SavedDevicesRepository] and drives a
 *    [ConnectionUiState.NeedsPin] prompt when one is required.
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
                            // Record a successful connect so the Saved
                            // tab can reuse it later.
                            runCatching {
                                savedDevices.upsert(
                                    id = attempt.id,
                                    label = attempt.label,
                                    transport = attempt.savedTransport,
                                    pin = attempt.pin,
                                )
                            }
                        }
                        _state.value = ConnectionUiState.Connected(ms.client)
                    }
                    is ManagerState.Failed -> {
                        if (_state.value !is ConnectionUiState.Failed) {
                            _state.value = ConnectionUiState.Failed(
                                cause = ms.cause,
                                deviceLabel = currentAttempt?.label ?: currentLabel(),
                            )
                        }
                    }
                    ManagerState.Idle -> {
                        if (_state.value is ConnectionUiState.Connected) {
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
        is ConnectionUiState.NeedsPin -> s.deviceLabel
        else -> null
    }

    // --- Public request API --------------------------------------------------

    /**
     * Begin connecting to [request]. For BLE, this first checks the
     * saved-devices store; if no PIN is saved the state transitions
     * to [ConnectionUiState.NeedsPin] and the caller should present a
     * PIN dialog, then call [providePin].
     */
    fun requestConnect(request: ConnectionRequest) {
        inFlight?.cancel()
        inFlight = scope.launch {
            when (request) {
                is ConnectionRequest.Ble -> {
                    val id = bleDeviceId(request.adv.identifier)
                    val saved = savedDevices.get(id)
                    val savedPin = saved?.pin
                    if (savedPin.isNullOrEmpty()) {
                        _state.value = ConnectionUiState.NeedsPin(
                            request = request,
                            deviceLabel = request.label,
                        )
                    } else {
                        doConnect(
                            attempt = Attempt(
                                id = id,
                                label = request.label,
                                pin = savedPin,
                                transport = BleTransport(request.adv),
                                savedTransport = SavedTransport.Ble(
                                    identifier = request.adv.identifier,
                                    advertName = request.adv.name,
                                ),
                            ),
                        )
                    }
                }
                is ConnectionRequest.Tcp -> doConnect(
                    attempt = Attempt(
                        id = tcpDeviceId(request.host, request.port),
                        label = request.label,
                        pin = null,
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
                            pin = null,
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
                    pin = saved.pin,
                    transport = transport,
                    savedTransport = savedTransport,
                ),
            )
        }
    }

    fun providePin(pin: String) {
        val needs = _state.value as? ConnectionUiState.NeedsPin ?: return
        inFlight?.cancel()
        inFlight = scope.launch {
            val request = needs.request
            doConnect(
                attempt = Attempt(
                    id = bleDeviceId(request.adv.identifier),
                    label = request.label,
                    pin = pin,
                    transport = BleTransport(request.adv),
                    savedTransport = SavedTransport.Ble(
                        identifier = request.adv.identifier,
                        advertName = request.adv.name,
                    ),
                ),
            )
        }
    }

    fun cancelPinPrompt() {
        if (_state.value is ConnectionUiState.NeedsPin) {
            _state.value = ConnectionUiState.Idle
        }
    }

    fun cancel() {
        inFlight?.cancel()
        inFlight = scope.launch {
            runCatching { manager.disconnect() }
            if (_state.value !is ConnectionUiState.Failed) {
                _state.value = ConnectionUiState.Idle
            }
        }
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
            runCatching { manager.connect(attempt.transport, pin = attempt.pin) }
        }
        when {
            completed == null -> {
                runCatching { manager.disconnect() }
                _state.value = ConnectionUiState.Failed(
                    cause = TimeoutException(
                        "No response from ${attempt.label} within ${connectTimeoutMs / 1000}s. " +
                            "The device may be out of range, powered off, or waiting " +
                            "for a different PIN.",
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

    private data class Attempt(
        val id: String,
        val label: String,
        val pin: String?,
        val transport: Transport,
        val savedTransport: SavedTransport,
    )
}
