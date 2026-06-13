package ee.schimke.meshcore.app.connection

import android.util.Log
import ee.schimke.meshcore.core.manager.ManagerState
import ee.schimke.meshcore.core.manager.MeshCoreManager
import ee.schimke.meshcore.data.repository.MeshcoreRepository
import ee.schimke.meshcore.data.repository.SavedDevice
import ee.schimke.meshcore.data.repository.SavedTransport
import ee.schimke.meshcore.session.ConnectionRequest
import ee.schimke.meshcore.session.ResolvedConnection
import ee.schimke.meshcore.session.TransportFactory
import ee.schimke.meshcore.session.toConnectionRequest
import java.util.concurrent.TimeoutException
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "MeshConnect"
private const val MAX_RETRY_COUNT = 5
private const val BASE_BACKOFF_MS = 2_000L
private const val MAX_BACKOFF_MS = 60_000L

/**
 * Owns connection state and the connect/retry policy, delegating
 * everything else: transports come from a shared [TransportFactory],
 * canonical device identity from [DeviceIdentityResolver], and OS-facing
 * work (service, persister, refresh scheduling, presence) to
 * [ConnectionSideEffects]. This keeps the class focused on producing a
 * correct [ConnectionUiState] / [connectedDeviceId] and easy to test.
 */
class AppConnectionController(
  private val manager: MeshCoreManager,
  private val repository: MeshcoreRepository,
  private val transportFactory: TransportFactory,
  private val identity: DeviceIdentityResolver = DeviceIdentityResolver(repository),
  private val sideEffects: ConnectionSideEffects = NoopConnectionSideEffects,
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
  @Volatile private var retryCount = 0

  init {
    scope.launch {
      manager.state.collect { ms ->
        when (ms) {
          is ManagerState.Connected -> onConnected(ms)
          is ManagerState.Failed -> {
            _connectedDeviceId.value = null
            sideEffects.onDisconnected()
            if (_state.value !is ConnectionUiState.Failed) {
              _state.value =
                ConnectionUiState.Failed(
                  cause = ms.cause,
                  deviceLabel = currentAttempt?.label ?: currentLabel(),
                )
            }
          }
          ManagerState.Idle -> {
            if (_state.value is ConnectionUiState.Connected) {
              _connectedDeviceId.value = null
              sideEffects.onDisconnected()
              _state.value = ConnectionUiState.Idle
            }
          }
          ManagerState.Connecting -> Unit
        }
      }
    }
  }

  private suspend fun onConnected(ms: ManagerState.Connected) {
    retryCount = 0
    val attempt = currentAttempt
    if (attempt == null) {
      _state.value = ConnectionUiState.Connected(ms.client)
      return
    }
    Log.d(TAG, "Connected to ${attempt.id}")
    val deviceId = identity.resolveCanonicalId(attempt.id, attempt.savedTransport, ms.client)
    _connectedDeviceId.value = deviceId
    _state.value = ConnectionUiState.Connected(ms.client)
    sideEffects.onConnected(deviceId, attempt.label, attempt.savedTransport, ms.client)
  }

  private fun currentLabel(): String? =
    when (val s = _state.value) {
      is ConnectionUiState.Connecting -> s.deviceLabel
      else -> null
    }

  fun requestConnect(request: ConnectionRequest) {
    Log.d(TAG, "requestConnect: ${request.label} (state: ${_state.value::class.simpleName})")
    retryCount = 0
    inFlight?.cancel()
    inFlight =
      scope.launch {
        val resolved =
          runCatching { transportFactory.resolve(request) }
            .getOrElse { cause ->
              _state.value = ConnectionUiState.Failed(cause = cause, deviceLabel = request.label)
              return@launch
            }
        doConnect(Attempt(request.label, resolved))
      }
  }

  fun requestReconnect(saved: SavedDevice) {
    inFlight?.cancel()
    if (saved.transport is SavedTransport.Usb) {
      // USB needs the runtime permission flow from the USB tab; it can't be
      // reconnected unattended from a saved record.
      _state.value =
        ConnectionUiState.Failed(
          cause = IllegalStateException("USB devices must be reconnected from the USB tab."),
          deviceLabel = saved.label,
        )
      return
    }
    inFlight =
      scope.launch {
        val resolved =
          runCatching { transportFactory.resolve(saved.toConnectionRequest()) }
            .getOrElse { cause ->
              _state.value = ConnectionUiState.Failed(cause = cause, deviceLabel = saved.label)
              return@launch
            }
        doConnect(Attempt(saved.label, resolved))
      }
  }

  fun cancel() {
    retryCount = 0
    inFlight?.cancel()
    sideEffects.onDisconnected()
    _connectedDeviceId.value = null
    _warnings.value = emptyList()
    _state.value = ConnectionUiState.Idle
    currentAttempt = null
    scope.launch {
      runCatching { manager.disconnect() }
        .onFailure { Log.d(TAG, "disconnect during cancel failed", it) }
    }
  }

  fun dismissError() {
    if (_state.value is ConnectionUiState.Failed) {
      _state.value = ConnectionUiState.Idle
    }
  }

  fun dismissWarning(warning: String) {
    _warnings.value = _warnings.value - warning
  }

  /** Surface a transient warning to the UI (e.g. from [ConnectionSideEffects]). */
  fun addWarning(warning: String) {
    _warnings.value = _warnings.value + warning
  }

  fun forgetSavedDevice(id: String) {
    scope.launch { repository.forgetDevice(id) }
  }

  fun toggleFavorite(id: String) {
    scope.launch {
      val old = repository.observeFavorite().first()
      repository.toggleFavorite(id)
      val new = repository.observeFavorite().first()
      sideEffects.onFavoriteChanged(old, new)
    }
  }

  /** Wire background refresh + presence for the favorite at app startup. */
  fun scheduleFavorite(favorite: SavedDevice) {
    scope.launch { sideEffects.onFavoriteChanged(null, favorite) }
  }

  private suspend fun doConnect(attempt: Attempt) {
    currentAttempt = attempt
    _state.value =
      ConnectionUiState.Connecting(
        startedAtMs = System.currentTimeMillis(),
        timeoutMs = connectTimeoutMs,
        deviceLabel = attempt.label,
      )
    val completed =
      withTimeoutOrNull(connectTimeoutMs) { runCatching { manager.connect(attempt.transport) } }
    when {
      completed == null -> {
        runCatching { manager.disconnect() }
          .onFailure { Log.d(TAG, "disconnect after timeout failed", it) }
        val cause =
          TimeoutException(
            "No response from ${attempt.label} within ${connectTimeoutMs / 1000}s."
          )
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
      val backoffMs =
        min(BASE_BACKOFF_MS shl (retryCount - 1), MAX_BACKOFF_MS) + Random.nextLong(0, 500)
      Log.d(TAG, "Retrying ${attempt.label} (attempt $retryCount/$MAX_RETRY_COUNT) in ${backoffMs}ms")
      _state.value =
        ConnectionUiState.Retrying(
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

  private fun isRetriable(cause: Throwable): Boolean =
    when {
      cause is CancellationException -> false
      cause is TimeoutException -> true
      cause.message?.contains("GATT", ignoreCase = true) == true -> true
      cause.message?.contains("BLE", ignoreCase = true) == true -> true
      else -> false
    }

  private data class Attempt(val label: String, private val resolved: ResolvedConnection) {
    val id: String
      get() = resolved.deviceId

    val transport
      get() = resolved.transport

    val savedTransport: SavedTransport
      get() = resolved.savedTransport
  }
}
