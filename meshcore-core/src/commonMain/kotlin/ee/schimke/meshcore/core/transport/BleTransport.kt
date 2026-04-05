package ee.schimke.meshcore.core.transport

import com.juul.kable.Advertisement
import com.juul.kable.Identifier
import com.juul.kable.Peripheral
import com.juul.kable.PeripheralBuilder
import com.juul.kable.Scanner
import com.juul.kable.State
import com.juul.kable.WriteType
import com.juul.kable.characteristicOf
import ee.schimke.meshcore.core.protocol.MeshCoreConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString

@OptIn(ExperimentalUuidApi::class)
private val serviceUuid = Uuid.parse(MeshCoreConstants.BLE_SERVICE_UUID)
@OptIn(ExperimentalUuidApi::class)
private val txCharacteristic = characteristicOf(
    service = serviceUuid,
    characteristic = Uuid.parse(MeshCoreConstants.BLE_TX_CHAR_UUID),
)
@OptIn(ExperimentalUuidApi::class)
private val rxCharacteristic = characteristicOf(
    service = serviceUuid,
    characteristic = Uuid.parse(MeshCoreConstants.BLE_RX_CHAR_UUID),
)

/** Cross-platform summary of a MeshCore device seen during a BLE scan. */
data class BleAdvertisement(
    val identifier: String,
    val name: String?,
    val rssi: Int,
    internal val raw: Advertisement,
)

/** Platform-specific tuning of the Kable [Scanner] builder. */
internal expect fun com.juul.kable.ScannerBuilder.applyMeshCoreScannerDefaults()

class BleScanner {
    private val scanner = Scanner { applyMeshCoreScannerDefaults() }

    val advertisements: Flow<BleAdvertisement> = scanner.advertisements
        .map { adv ->
            BleAdvertisement(
                identifier = adv.identifier.toString(),
                name = adv.name,
                rssi = adv.rssi,
                raw = adv,
            )
        }
}

/**
 * BLE transport over the MeshCore Nordic UART Service, backed by
 * [Kable][com.juul.kable]. Each GATT write / characteristic notification
 * delivers one complete MeshCore frame, so no stream codec is required.
 *
 * Constructing from a [BleAdvertisement] uses the active scan result;
 * [fromIdentifier] creates one from a raw MAC / UUID so callers that
 * already know the device (e.g. a saved bookmark) can skip scanning.
 */
/**
 * Platform-specific MeshCore Peripheral configuration. Only Android's
 * Kable build exposes options like `autoConnectIf` and logging
 * identifiers; commonMain delegates to the actual to stay portable.
 */
internal expect fun PeripheralBuilder.applyMeshCoreDefaults(autoConnect: Boolean)

/**
 * Spec describing how to build the Peripheral on each [BleTransport.connect]
 * call. A fresh Peripheral per attempt avoids carrying a cancelled
 * `SharedRepeatableAction` state forward from a previous failure.
 */
private sealed interface BleTarget {
    fun create(): Peripheral
    class Advert(private val adv: Advertisement) : BleTarget {
        override fun create(): Peripheral =
            Peripheral(adv) { applyMeshCoreDefaults(autoConnect = false) }
    }
    class ById(private val id: Identifier) : BleTarget {
        override fun create(): Peripheral =
            Peripheral(id) { applyMeshCoreDefaults(autoConnect = true) }
    }
}

class BleTransport private constructor(
    private val target: BleTarget,
) : Transport {

    constructor(advertisement: BleAdvertisement) : this(BleTarget.Advert(advertisement.raw))

    private val _state = MutableStateFlow<TransportState>(TransportState.Disconnected)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<ByteString>(extraBufferCapacity = 64)
    override val incoming: SharedFlow<ByteString> = _incoming.asSharedFlow()

    // One scope per connect() invocation so a cancelled Peripheral is
    // garbage-collected before the next attempt constructs a new one.
    private var sessionScope: CoroutineScope? = null
    private var peripheral: Peripheral? = null
    private var observerJob: Job? = null
    private var stateJob: Job? = null

    override suspend fun connect() {
        if (_state.value is TransportState.Connected) return
        _state.value = TransportState.Connecting
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        sessionScope = scope
        try {
            val p = target.create()
            peripheral = p
            stateJob = scope.launch {
                p.state.collect { s ->
                    when (s) {
                        is State.Connected -> _state.value = TransportState.Connected
                        is State.Disconnected -> _state.value = TransportState.Disconnected
                        else -> Unit
                    }
                }
            }
            // No withTimeout: cancelling p.connect() puts Kable's
            // SharedRepeatableAction into a permanently-cancelled state,
            // which poisons subsequent reconnect attempts. With
            // autoConnect = true the GATT request just stays pending
            // until the device is in range. Users cancel via
            // MeshCoreManager.disconnect() which closes us, which
            // cancels the session scope, which propagates into p.connect().
            p.connect()
            observerJob = scope.launch {
                p.observe(txCharacteristic).collect { bytes ->
                    _incoming.emit(ByteString(bytes))
                }
            }
            _state.value = TransportState.Connected
        } catch (t: CancellationException) {
            closeQuietly()
            _state.value = TransportState.Disconnected
            throw t
        } catch (t: Throwable) {
            closeQuietly()
            _state.value = TransportState.Error(t)
            throw t
        }
    }

    override suspend fun send(frame: ByteString) {
        val p = peripheral ?: error("BLE transport not connected")
        p.write(rxCharacteristic, frame.toByteArray(), WriteType.WithoutResponse)
    }

    override suspend fun close() {
        closeQuietly()
        _state.value = TransportState.Disconnected
    }

    private fun closeQuietly() {
        observerJob?.cancel(); observerJob = null
        stateJob?.cancel(); stateJob = null
        peripheral = null
        // Cancelling the session scope tears down the Peripheral bound
        // to it. Doing a graceful p.disconnect() is unnecessary – the
        // scope cancellation reaches the GATT client via Kable.
        sessionScope?.cancel()
        sessionScope = null
    }

    companion object {
        /**
         * Create a transport straight from a device identifier (MAC
         * address on Android, UUID on Apple platforms). Bypasses the
         * scanner entirely – useful when you already know the device.
         *
         * Uses Android's `autoConnect = true` semantics, meaning the OS
         * will keep the GATT request pending until the peer becomes
         * visible. This avoids the `NotConnectedException: Disconnect
         * detected` you get with `autoConnect = false` when the device
         * isn't advertising at the exact moment of the call.
         */
        fun fromIdentifier(identifier: Identifier): BleTransport =
            BleTransport(BleTarget.ById(identifier))
    }
}
