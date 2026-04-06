package ee.schimke.meshcore.app.connection

import ee.schimke.meshcore.core.manager.ManagerState
import ee.schimke.meshcore.core.manager.MeshCoreManager
import ee.schimke.meshcore.core.transport.Transport
import ee.schimke.meshcore.data.repository.MeshcoreRepository
import ee.schimke.meshcore.data.repository.SavedDevice
import ee.schimke.meshcore.data.repository.SavedTransport
import ee.schimke.meshcore.data.repository.bleDeviceId
import ee.schimke.meshcore.data.repository.tcpDeviceId
import ee.schimke.meshcore.data.repository.toSelfInfo
import ee.schimke.meshcore.data.repository.toBattery
import ee.schimke.meshcore.data.repository.toRadio
import ee.schimke.meshcore.data.repository.toDeviceInfo
import ee.schimke.meshcore.data.repository.usbDeviceId
import ee.schimke.meshcore.data.sync.MessagePersister
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

class AppConnectionController(
    private val manager: MeshCoreManager,
    private val repository: MeshcoreRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow<ConnectionUiState>(ConnectionUiState.Idle)
    val state: StateFlow<ConnectionUiState> = _state.asStateFlow()

    private val _connectedDeviceId = MutableStateFlow<String?>(null)
    val connectedDeviceId: StateFlow<String?> = _connectedDeviceId.asStateFlow()

    private val connectTimeoutMs: Long = 20_000L

    @Volatile private var inFlight: Job? = null
    @Volatile private var currentAttempt: Attempt? = null
    @Volatile private var persisterJob: Job? = null

    init {
        scope.launch {
            manager.state.collect { ms ->
                when (ms) {
                    is ManagerState.Connected -> {
                        val attempt = currentAttempt
                        if (attempt != null) {
                            _connectedDeviceId.value = attempt.id
                            // Seed client with cached data from Room
                            runCatching {
                                val state = repository.getDeviceState(attempt.id)
                                val contacts = repository.getContacts(attempt.id)
                                val channels = repository.getChannels(attempt.id)
                                ms.client.seedFromCache(
                                    selfInfo = state?.toSelfInfo(),
                                    contacts = contacts,
                                    battery = state?.toBattery(),
                                    radio = state?.toRadio(),
                                    deviceInfo = state?.toDeviceInfo(),
                                    channels = channels,
                                )
                            }
                            runCatching {
                                repository.upsertDevice(
                                    id = attempt.id,
                                    label = attempt.label,
                                    transport = attempt.savedTransport,
                                )
                            }
                        }
                        _state.value = ConnectionUiState.Connected(ms.client)
                        if (attempt != null) {
                            // Persist messages in background
                            persisterJob?.cancel()
                            persisterJob = scope.launch {
                                val persister = MessagePersister(
                                    repository = repository,
                                    deviceId = attempt.id,
                                    contactResolver = { prefix ->
                                        val hex = prefix.toHex()
                                        ms.client.contacts.value
                                            .firstOrNull { it.publicKey.toHex().startsWith(hex) }
                                            ?.publicKey?.toHex()
                                    },
                                )
                                persister.collect(ms.client.events)
                            }
                            // Fetch fresh data and persist to Room
                            scope.launch { fetchAndPersist(attempt.id, ms.client) }
                        }
                    }
                    is ManagerState.Failed -> {
                        _connectedDeviceId.value = null
                        persisterJob?.cancel()
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
                            persisterJob?.cancel()
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

    fun requestConnect(request: ConnectionRequest) {
        inFlight?.cancel()
        inFlight = scope.launch {
            when (request) {
                is ConnectionRequest.Ble -> {
                    val id = bleDeviceId(request.adv.identifier)
                    doConnect(Attempt(
                        id = id,
                        label = request.label,
                        transport = BleTransport(request.adv),
                        savedTransport = SavedTransport.Ble(request.adv.identifier, request.adv.name),
                    ))
                }
                is ConnectionRequest.Tcp -> doConnect(Attempt(
                    id = tcpDeviceId(request.host, request.port),
                    label = request.label,
                    transport = TcpTransport(request.host, request.port),
                    savedTransport = SavedTransport.Tcp(request.host, request.port),
                ))
                is ConnectionRequest.Usb -> {
                    val className = request.port::class.simpleName ?: "usb"
                    val vid = runCatching { request.port.vendorId }.getOrDefault(-1)
                    val pid = runCatching { request.port.productId }.getOrDefault(-1)
                    doConnect(Attempt(
                        id = usbDeviceId(className, vid, pid),
                        label = request.label,
                        transport = UsbSerialTransport(request.port),
                        savedTransport = SavedTransport.Usb(className, vid, pid),
                    ))
                }
            }
        }
    }

    fun requestReconnect(saved: SavedDevice) {
        inFlight?.cancel()
        inFlight = scope.launch {
            val (transport, savedTransport) = when (val t = saved.transport) {
                is SavedTransport.Ble -> BleTransport.fromIdentifier(t.identifier) to t
                is SavedTransport.Tcp -> TcpTransport(t.host, t.port) to t
                is SavedTransport.Usb -> {
                    _state.value = ConnectionUiState.Failed(
                        cause = IllegalStateException("USB devices must be reconnected from the USB tab."),
                        deviceLabel = saved.label,
                    )
                    return@launch
                }
            }
            doConnect(Attempt(saved.id, saved.label, transport, savedTransport))
        }
    }

    fun cancel() {
        inFlight?.cancel()
        persisterJob?.cancel()
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

    fun forgetSavedDevice(id: String) {
        scope.launch { repository.forgetDevice(id) }
    }

    fun toggleFavorite(id: String) {
        scope.launch { repository.toggleFavorite(id) }
    }

    private suspend fun doConnect(attempt: Attempt) {
        currentAttempt = attempt
        _state.value = ConnectionUiState.Connecting(
            startedAtMs = System.currentTimeMillis(),
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
                    cause = TimeoutException("No response from ${attempt.label} within ${connectTimeoutMs / 1000}s."),
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
        }
    }

    private suspend fun fetchAndPersist(
        deviceId: String,
        client: ee.schimke.meshcore.core.client.MeshCoreClient,
    ) {
        runCatching {
            runCatching { client.getContacts() }
            runCatching { client.getChannels() }
            val now = System.currentTimeMillis()
            client.selfInfo.value?.let { repository.updateSelfInfo(deviceId, it, now) }
            client.battery.value?.let { repository.updateBattery(deviceId, it, now) }
            client.radio.value?.let { repository.updateRadio(deviceId, it, now) }
            client.device.value?.let { repository.updateDeviceInfo(deviceId, it, now) }
            repository.replaceContacts(deviceId, client.contacts.value, now)
            repository.replaceChannels(deviceId, client.channels.value, now)
        }
    }

    private data class Attempt(
        val id: String,
        val label: String,
        val transport: Transport,
        val savedTransport: SavedTransport,
    )
}
