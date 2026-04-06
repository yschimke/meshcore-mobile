package ee.schimke.meshcore.transport.ble

import com.juul.kable.Advertisement
import com.juul.kable.Identifier
import com.juul.kable.Peripheral
import com.juul.kable.PeripheralBuilder
import com.juul.kable.Scanner
import com.juul.kable.State
import com.juul.kable.WriteType
import com.juul.kable.characteristicOf
import ee.schimke.meshcore.core.protocol.MeshCoreConstants
import ee.schimke.meshcore.core.transport.Transport
import ee.schimke.meshcore.core.transport.TransportState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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

internal expect fun PeripheralBuilder.applyMeshCoreDefaults(autoConnect: Boolean)

internal expect suspend fun requestLargerMtu(peripheral: Peripheral)

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

/**
 * BLE transport over the MeshCore Nordic UART Service, backed by Kable.
 * Each GATT write / characteristic notification delivers one complete
 * MeshCore frame, so no stream codec is required.
 */
class BleTransport private constructor(
    private val target: BleTarget,
) : Transport {

    constructor(advertisement: BleAdvertisement) : this(BleTarget.Advert(advertisement.raw))

    private val _state = MutableStateFlow<TransportState>(TransportState.Disconnected)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<ByteString>(extraBufferCapacity = 64)
    override val incoming: SharedFlow<ByteString> = _incoming.asSharedFlow()

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
            p.connect()
            // Negotiate a larger MTU so MeshCore frames (up to 172 bytes)
            // fit in a single BLE notification. Without this, the default
            // ATT MTU of 23 (20-byte payload) splits frames across
            // multiple notifications and the parser sees garbage.
            runCatching { requestLargerMtu(p) }
            observerJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
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
        sessionScope?.cancel()
        sessionScope = null
    }

    companion object {
        fun fromIdentifier(identifier: Identifier): BleTransport =
            BleTransport(BleTarget.ById(identifier))
    }
}
