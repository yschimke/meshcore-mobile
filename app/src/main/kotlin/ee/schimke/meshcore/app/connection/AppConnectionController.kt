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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import android.content.Context
import android.util.Log
import ee.schimke.meshcore.app.ble.DevicePresenceManager
import ee.schimke.meshcore.app.widget.PeriodicRefreshWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.concurrent.TimeoutException
import kotlin.math.min
import kotlin.random.Random

private const val TAG = "MeshConnect"
private const val MAX_RETRY_COUNT = 5
private const val BASE_BACKOFF_MS = 2_000L
private const val MAX_BACKOFF_MS = 60_000L

class AppConnectionController(
    private val manager: MeshCoreManager,
    private val repository: MeshcoreRepository,
    private val appContext: Context? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow<ConnectionUiState>(ConnectionUiState.Idle)
    val state: StateFlow<ConnectionUiState> = _state.asStateFlow()

    private val _connectedDeviceId = MutableStateFlow<String?>(null)
    val connectedDeviceId: StateFlow<String?> = _connectedDeviceId.asStateFlow()

    private val _warnings = MutableStateFlow<List<String>>(emptyList())
    val warnings: StateFlow<List<String>> = _warnings.asStateFlow()

    private val connectTimeoutMs: Long = 20_000L

    @Volatile private var inFlight: Job? = null
    @Volatile private var currentAttempt: Attempt? = null
    @Volatile private var persisterJob: Job? = null
    @Volatile private var retryCount = 0

    init {
        scope.launch {
            manager.state.collect { ms ->
                when (ms) {
                    is ManagerState.Connected -> {
                        retryCount = 0
                        val attempt = currentAttempt
                        if (attempt != null) {
                            Log.d(TAG, "Connected to ${attempt.id}")

                            // Resolve canonical device ID by public key.
                            // If this device was previously seen via a different
                            // transport (e.g. BLE vs USB), reuse the existing ID
                            // so contacts/messages/channels are shared.
                            var deviceId = attempt.id
                            val selfInfo = ms.client.selfInfo.value
                            if (selfInfo != null) {
                                val existingId = runCatching {
                                    repository.findDeviceIdByPublicKey(selfInfo.publicKey)
                                }.onFailure { Log.w(TAG, "findDeviceIdByPublicKey failed", it) }
                                    .getOrNull()
                                if (existingId != null && existingId != attempt.id) {
                                    Log.d(TAG, "Merging ${attempt.id} into existing $existingId")
                                    runCatching {
                                        repository.mergeDevice(attempt.id, existingId, attempt.savedTransport)
                                    }.onFailure { Log.w(TAG, "mergeDevice failed for ${attempt.id} -> $existingId", it) }
                                    deviceId = existingId
                                }
                            }

                            _connectedDeviceId.value = deviceId

                            // Seed client with cached data from Room
                            runCatching {
                                val state = repository.getDeviceState(deviceId)
                                val contacts = repository.getContacts(deviceId)
                                val channels = repository.getChannels(deviceId)
                                ms.client.seedFromCache(
                                    selfInfo = state?.toSelfInfo(),
                                    contacts = contacts,
                                    battery = state?.toBattery(),
                                    radio = state?.toRadio(),
                                    deviceInfo = state?.toDeviceInfo(),
                                    channels = channels,
                                )
                            }.onFailure {
                                Log.w(TAG, "seedFromCache failed for $deviceId", it)
                                _warnings.value += "Cached data may be stale"
                            }
                            runCatching {
                                repository.upsertDevice(
                                    id = deviceId,
                                    label = selfInfo?.name ?: attempt.label,
                                    transport = attempt.savedTransport,
                                )
                            }.onFailure { Log.w(TAG, "upsertDevice failed for $deviceId", it) }
                        }
                        _state.value = ConnectionUiState.Connected(ms.client)
                        if (attempt != null) {
                            val deviceId = _connectedDeviceId.value ?: attempt.id
                            // Persist messages in background
                            persisterJob?.cancel()
                            persisterJob = scope.launch {
                                val persister = MessagePersister(
                                    repository = repository,
                                    deviceId = deviceId,
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
                            scope.launch { fetchAndPersist(deviceId, ms.client) }
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
        Log.d(TAG, "requestConnect: ${request.label} (current state: ${_state.value::class.simpleName})")
        retryCount = 0
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
                    val vid = runCatching { request.port.vendorId }
                        .onFailure { Log.d(TAG, "USB vendorId unavailable", it) }
                        .getOrDefault(-1)
                    val pid = runCatching { request.port.productId }
                        .onFailure { Log.d(TAG, "USB productId unavailable", it) }
                        .getOrDefault(-1)
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
        retryCount = 0
        inFlight?.cancel()
        persisterJob?.cancel()
        _connectedDeviceId.value = null
        _warnings.value = emptyList()
        _state.value = ConnectionUiState.Idle
        currentAttempt = null
        scope.launch { runCatching { manager.disconnect() }.onFailure { Log.d(TAG, "disconnect during cancel failed", it) } }
    }

    fun dismissError() {
        if (_state.value is ConnectionUiState.Failed) {
            _state.value = ConnectionUiState.Idle
        }
    }

    fun dismissWarning(warning: String) {
        _warnings.value = _warnings.value - warning
    }

    fun forgetSavedDevice(id: String) {
        scope.launch { repository.forgetDevice(id) }
    }

    fun toggleFavorite(id: String) {
        scope.launch {
            // Stop observing old favorite if any
            val oldFavorite = repository.observeFavorite().first()
            if (oldFavorite != null && appContext != null) {
                DevicePresenceManager.stopObserving(appContext, oldFavorite)
            }

            repository.toggleFavorite(id)

            // Schedule/cancel background refresh + presence observation
            appContext?.let { ctx ->
                val newFavorite = repository.observeFavorite().first()
                if (newFavorite != null) {
                    PeriodicRefreshWorker.scheduleIfFavoriteExists(ctx)
                    DevicePresenceManager.startObserving(ctx, newFavorite)
                } else {
                    PeriodicRefreshWorker.cancel(ctx)
                }
            }
        }
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
                runCatching { manager.disconnect() }.onFailure { Log.d(TAG, "disconnect after timeout failed", it) }
                val cause = TimeoutException("No response from ${attempt.label} within ${connectTimeoutMs / 1000}s.")
                maybeRetry(attempt, cause)
            }
            completed.isFailure -> {
                val cause = completed.exceptionOrNull() ?: IllegalStateException("unknown")
                maybeRetry(attempt, cause)
            }
        }
    }

    private suspend fun maybeRetry(attempt: Attempt, cause: Throwable) {
        if (isRetriable(cause) && retryCount < MAX_RETRY_COUNT) {
            retryCount++
            val backoffMs = min(BASE_BACKOFF_MS shl (retryCount - 1), MAX_BACKOFF_MS) +
                Random.nextLong(0, 500)
            Log.d(TAG, "Retrying ${attempt.label} (attempt $retryCount/$MAX_RETRY_COUNT) in ${backoffMs}ms")
            _state.value = ConnectionUiState.Retrying(
                attempt = retryCount,
                maxAttempts = MAX_RETRY_COUNT,
                nextRetryAtMs = System.currentTimeMillis() + backoffMs,
                deviceLabel = attempt.label,
            )
            delay(backoffMs)
            doConnect(attempt)
        } else {
            _state.value = ConnectionUiState.Failed(cause = cause, deviceLabel = attempt.label)
            currentAttempt = null
        }
    }

    private fun isRetriable(cause: Throwable): Boolean = when {
        cause is CancellationException -> false
        cause is TimeoutException -> true
        cause.message?.contains("GATT", ignoreCase = true) == true -> true
        cause.message?.contains("BLE", ignoreCase = true) == true -> true
        else -> false
    }

    private suspend fun fetchAndPersist(
        deviceId: String,
        client: ee.schimke.meshcore.core.client.MeshCoreClient,
    ) {
        val now = System.currentTimeMillis()
        Log.d(TAG, "fetchAndPersist: starting for $deviceId")

        // 1. Device state first
        client.selfInfo.value?.let {
            Log.d(TAG, "fetchAndPersist: persisting selfInfo name='${it.name}'")
            repository.updateSelfInfo(deviceId, it, now)
        }
        runCatching {
            val bat = client.getBatteryAndStorage()
            Log.d(TAG, "fetchAndPersist: battery ${bat.millivolts}mV")
            repository.updateBattery(deviceId, bat, now)
        }.onFailure { Log.w(TAG, "fetchAndPersist: battery fetch failed", it) }
        runCatching {
            val radio = client.getRadioSettings()
            Log.d(TAG, "fetchAndPersist: radio ${radio.frequencyHz}Hz")
            repository.updateRadio(deviceId, radio, now)
        }.onFailure { Log.w(TAG, "fetchAndPersist: radio fetch failed", it) }
        client.device.value?.let { repository.updateDeviceInfo(deviceId, it, now) }

        // 2. Contacts
        Log.d(TAG, "fetchAndPersist: fetching contacts")
        runCatching { client.getContacts() }.onFailure { Log.w(TAG, "fetchAndPersist: contacts fetch failed", it) }
        repository.replaceContacts(deviceId, client.contacts.value, now)
        Log.d(TAG, "fetchAndPersist: ${client.contacts.value.size} contacts persisted")

        // 3. Channels
        Log.d(TAG, "fetchAndPersist: fetching channels")
        runCatching { client.getChannels() }.onFailure { Log.w(TAG, "fetchAndPersist: channels fetch failed", it) }
        repository.replaceChannels(deviceId, client.channels.value, now)
        Log.d(TAG, "fetchAndPersist: ${client.channels.value.size} channels persisted, done")
    }

    private data class Attempt(
        val id: String,
        val label: String,
        val transport: Transport,
        val savedTransport: SavedTransport,
    )
}
