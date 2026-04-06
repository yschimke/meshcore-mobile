package ee.schimke.meshcore.core.client

import ee.schimke.meshcore.core.model.BatteryInfo
import ee.schimke.meshcore.core.model.Contact
import ee.schimke.meshcore.core.model.DeviceInfo
import ee.schimke.meshcore.core.model.MeshEvent
import ee.schimke.meshcore.core.model.PublicKey
import ee.schimke.meshcore.core.model.RadioSettings
import ee.schimke.meshcore.core.model.SelfInfo
import ee.schimke.meshcore.core.model.SendAck
import ee.schimke.meshcore.core.protocol.Frames
import ee.schimke.meshcore.core.protocol.MeshCoreConstants
import ee.schimke.meshcore.core.protocol.Parsers
import ee.schimke.meshcore.core.transport.Transport
import ee.schimke.meshcore.core.transport.TransportState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.io.bytestring.ByteString
import kotlin.time.Instant

/**
 * High-level coroutine API over a [Transport]. Construct one per active
 * device connection; call [start] after the transport is connected.
 */
class MeshCoreClient(
    private val transport: Transport,
    private val scope: CoroutineScope,
) {
    private val _events = MutableSharedFlow<MeshEvent>(
        replay = 0, extraBufferCapacity = 64,
    )
    val events: SharedFlow<MeshEvent> = _events.asSharedFlow()

    private val _selfInfo = MutableStateFlow<SelfInfo?>(null)
    val selfInfo: StateFlow<SelfInfo?> = _selfInfo.asStateFlow()

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _battery = MutableStateFlow<BatteryInfo?>(null)
    val battery: StateFlow<BatteryInfo?> = _battery.asStateFlow()

    private val _radio = MutableStateFlow<RadioSettings?>(null)
    val radio: StateFlow<RadioSettings?> = _radio.asStateFlow()

    private val _device = MutableStateFlow<DeviceInfo?>(null)
    val device: StateFlow<DeviceInfo?> = _device.asStateFlow()

    val connection: StateFlow<TransportState> get() = transport.state

    private val sendMutex = Mutex()
    private var pumpJob: Job? = null
    private val contactsAccumulator = mutableListOf<Contact>()

    suspend fun start(
        appName: String = "meshcore-kmp",
        appVersion: Int = MeshCoreConstants.APP_PROTOCOL_VERSION,
    ) {
        if (pumpJob == null) {
            // UNDISPATCHED so the pump subscribes to transport.incoming
            // *before* we send any frames — a dispatched launch could
            // miss the AppStart response on a fast BLE round-trip
            // because the SharedFlow has replay=0.
            pumpJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                transport.incoming.collect { frame ->
                    handleEvent(Parsers.parse(frame))
                }
            }
        }
        transport.send(Frames.appStart(appName, appVersion))
        runCatching { transport.send(Frames.deviceQuery()) }
        runCatching { transport.send(Frames.getBatteryAndStorage()) }
        runCatching { transport.send(Frames.getRadioSettings()) }
    }

    /**
     * Authenticate to [recipient] (a repeater or room contact) using
     * [password]. Awaits a `LoginSuccess` / `LoginFail` push from the
     * device; throws on failure or after [timeoutMs]. This is a
     * runtime operation on an established connection — it has nothing
     * to do with the device handshake.
     *
     * Wire format verified against the MeshCore Flutter client's
     * `buildSendLoginFrame` in `connector/meshcore_protocol.dart`.
     */
    suspend fun login(
        recipient: PublicKey,
        password: String,
        timeoutMs: Long = 5_000,
    ) {
        val ev = requestOne(Frames.sendLogin(recipient, password), timeoutMs) {
            it is MeshEvent.LoginSuccess || it is MeshEvent.LoginFail
        }
        if (ev is MeshEvent.LoginFail) error("device rejected login for ${recipient.toHex().take(12)}")
    }

    fun stop() {
        pumpJob?.cancel()
        pumpJob = null
    }

    private suspend fun handleEvent(event: MeshEvent) {
        when (event) {
            is MeshEvent.SelfInfoEvent -> {
                _selfInfo.value = event.info
                _radio.value = event.info.radio
            }
            is MeshEvent.Radio -> _radio.value = event.settings
            is MeshEvent.Battery -> _battery.value = event.info
            is MeshEvent.Device -> _device.value = event.info
            MeshEvent.ContactsStart -> contactsAccumulator.clear()
            is MeshEvent.ContactEvent -> contactsAccumulator += event.contact
            MeshEvent.EndOfContacts -> {
                _contacts.value = contactsAccumulator.toList()
                contactsAccumulator.clear()
            }
            else -> Unit
        }
        _events.emit(event)
    }

    suspend fun getContacts(timeoutMs: Long = 5_000): List<Contact> = sendMutex.withLock {
        coroutineScope {
            val deferred = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(timeoutMs) { events.filter { it is MeshEvent.EndOfContacts }.first() }
            }
            transport.send(Frames.getContacts())
            deferred.await()
        }
        _contacts.value
    }

    suspend fun getBatteryAndStorage(timeoutMs: Long = 3_000): BatteryInfo =
        (requestOne(Frames.getBatteryAndStorage(), timeoutMs) { it is MeshEvent.Battery } as MeshEvent.Battery).info

    suspend fun getRadioSettings(timeoutMs: Long = 3_000): RadioSettings =
        (requestOne(Frames.getRadioSettings(), timeoutMs) { it is MeshEvent.Radio } as MeshEvent.Radio).settings

    suspend fun getDeviceTime(timeoutMs: Long = 3_000): Instant =
        (requestOne(Frames.getDeviceTime(), timeoutMs) { it is MeshEvent.CurrentTime } as MeshEvent.CurrentTime).time

    suspend fun setDeviceTime(time: Instant) =
        sendMutex.withLock { transport.send(Frames.setDeviceTime(time)) }

    suspend fun setAdvertName(name: String) =
        sendMutex.withLock { transport.send(Frames.setAdvertName(name)) }

    suspend fun setAdvertLatLon(lat: Double, lon: Double) =
        sendMutex.withLock { transport.send(Frames.setAdvertLatLon(lat, lon)) }

    suspend fun setRadioParams(freqHz: Int, bwHz: Int, sf: Int, cr: Int) =
        sendMutex.withLock { transport.send(Frames.setRadioParams(freqHz, bwHz, sf, cr)) }

    suspend fun setRadioTxPower(dbm: Int) =
        sendMutex.withLock { transport.send(Frames.setRadioTxPower(dbm)) }

    suspend fun sendSelfAdvert(floodMode: Boolean = false) =
        sendMutex.withLock { transport.send(Frames.sendSelfAdvert(floodMode)) }

    suspend fun reboot() = sendMutex.withLock { transport.send(Frames.reboot()) }

    suspend fun syncNextMessage() = sendMutex.withLock { transport.send(Frames.syncNextMessage()) }

    suspend fun sendText(
        recipient: PublicKey,
        text: String,
        timestamp: Instant,
        attempt: Int = 0,
        timeoutMs: Long = 5_000,
    ): SendAck {
        val frame = Frames.sendTextMessage(recipient, text, timestamp, attempt)
        val ev = requestOne(frame, timeoutMs) { it is MeshEvent.Sent || it is MeshEvent.Err }
        if (ev is MeshEvent.Err) error("device returned error code ${ev.code}")
        return (ev as MeshEvent.Sent).ack
    }

    suspend fun sendChannelText(
        channelIdx: Int,
        text: String,
        timestamp: Instant,
        timeoutMs: Long = 5_000,
    ): SendAck {
        val frame = Frames.sendChannelTextMessage(channelIdx, text, timestamp)
        val ev = requestOne(frame, timeoutMs) { it is MeshEvent.Sent || it is MeshEvent.Err }
        if (ev is MeshEvent.Err) error("device returned error code ${ev.code}")
        return (ev as MeshEvent.Sent).ack
    }

    /**
     * Send a frame and await the first matching reply.
     *
     * The collector is attached **before** `transport.send` so a very
     * fast reply (BLE round-trips can be <10 ms) can't slip past an
     * unattached `SharedFlow` — prior versions of this method had a
     * race where the pump emitted the reply and dropped it because
     * `events` is replay=0 and the collector wasn't subscribed yet.
     */
    private suspend fun requestOne(
        frame: ByteString,
        timeoutMs: Long,
        predicate: (MeshEvent) -> Boolean,
    ): MeshEvent = sendMutex.withLock {
        coroutineScope {
            val deferred = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(timeoutMs) { events.filter(predicate).first() }
            }
            transport.send(frame)
            deferred.await()
        }
    }
}
