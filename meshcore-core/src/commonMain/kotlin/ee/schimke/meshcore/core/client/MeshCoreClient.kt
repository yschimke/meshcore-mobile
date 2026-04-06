package ee.schimke.meshcore.core.client

import ee.schimke.meshcore.core.model.BatteryInfo
import ee.schimke.meshcore.core.model.ChannelInfo
import ee.schimke.meshcore.core.model.Contact
import ee.schimke.meshcore.core.model.DeviceInfo
import ee.schimke.meshcore.core.model.MeshEvent
import ee.schimke.meshcore.core.model.ReceivedChannelMessage
import ee.schimke.meshcore.core.model.ReceivedDirectMessage
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

    private val _channels = MutableStateFlow<List<ChannelInfo>>(emptyList())
    val channels: StateFlow<List<ChannelInfo>> = _channels.asStateFlow()

    /** Accumulated DMs keyed by the contact's full public key hex. */
    private val _directMessages = MutableStateFlow<Map<String, List<ReceivedDirectMessage>>>(emptyMap())
    val directMessages: StateFlow<Map<String, List<ReceivedDirectMessage>>> = _directMessages.asStateFlow()

    /** Accumulated channel messages keyed by channel index. */
    private val _channelMessages = MutableStateFlow<Map<Int, List<ReceivedChannelMessage>>>(emptyMap())
    val channelMessages: StateFlow<Map<Int, List<ReceivedChannelMessage>>> = _channelMessages.asStateFlow()

    val connection: StateFlow<TransportState> get() = transport.state

    /**
     * Pre-populate StateFlows with cached data from a previous session
     * so the UI has something to show immediately while fresh data loads.
     * Only sets values that are still null/empty — live data from the
     * device takes priority.
     */
    fun seedFromCache(
        selfInfo: SelfInfo? = null,
        contacts: List<Contact>? = null,
        battery: BatteryInfo? = null,
        radio: RadioSettings? = null,
        deviceInfo: DeviceInfo? = null,
        channels: List<ChannelInfo>? = null,
    ) {
        if (_selfInfo.value == null && selfInfo != null) _selfInfo.value = selfInfo
        if (_contacts.value.isEmpty() && contacts != null) _contacts.value = contacts
        if (_battery.value == null && battery != null) _battery.value = battery
        if (_radio.value == null && radio != null) _radio.value = radio
        if (_device.value == null && deviceInfo != null) _device.value = deviceInfo
        if (_channels.value.isEmpty() && channels != null) _channels.value = channels
    }

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
            is MeshEvent.ChannelInfoEvent -> {
                val ch = event.info
                _channels.value = _channels.value.filter { it.index != ch.index } + ch
            }
            is MeshEvent.DirectMessage -> {
                val msg = event.message
                // Resolve the full pubkey hex from contacts by matching the 6-byte prefix
                val prefix = msg.senderPrefix.toHex()
                val contactKey = _contacts.value
                    .firstOrNull { it.publicKey.toHex().startsWith(prefix) }
                    ?.publicKey?.toHex() ?: prefix
                val current = _directMessages.value
                _directMessages.value = current + (contactKey to (current[contactKey].orEmpty() + msg))
            }
            is MeshEvent.ChannelMessage -> {
                val msg = event.message
                val idx = msg.channelIndex
                val current = _channelMessages.value
                _channelMessages.value = current + (idx to (current[idx].orEmpty() + msg))
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

    /**
     * Enumerate all configured channels by requesting each index
     * from 0 until [DeviceInfo.maxChannels]. Empty channels (name
     * blank + PSK all zeros) are filtered out.
     */
    /**
     * Enumerate all configured channels by requesting each index
     * from 0 until [DeviceInfo.maxChannels]. Empty channels (name
     * blank + PSK all zeros) are filtered out. Uses a short per-channel
     * timeout since responses are local to the companion device.
     */
    suspend fun getChannels(perChannelTimeoutMs: Long = 1_000): List<ChannelInfo> {
        val maxCh = _device.value?.maxChannels ?: 8
        val result = mutableListOf<ChannelInfo>()
        for (i in 0 until maxCh) {
            val ev = runCatching {
                requestOne(Frames.getChannel(i), perChannelTimeoutMs) {
                    it is MeshEvent.ChannelInfoEvent
                }
            }.getOrNull() as? MeshEvent.ChannelInfoEvent ?: continue
            val ch = ev.info
            val isEmpty = ch.name.isBlank() && ch.psk.toByteArray().all { it == 0.toByte() }
            if (!isEmpty) result += ch
        }
        _channels.value = result
        return result
    }

    /**
     * Drain the device's pending message queue by repeatedly sending
     * `SyncNextMessage` until the device replies with `NoMoreMessages`.
     * Each iteration yields a `DirectMessage` or `ChannelMessage` event
     * through the [events] SharedFlow as usual.
     */
    suspend fun syncMessages(perMessageTimeoutMs: Long = 5_000) {
        while (true) {
            val ev = runCatching {
                requestOne(Frames.syncNextMessage(), perMessageTimeoutMs) {
                    it is MeshEvent.DirectMessage ||
                        it is MeshEvent.ChannelMessage ||
                        it is MeshEvent.NoMoreMessages
                }
            }.getOrNull() ?: break
            if (ev is MeshEvent.NoMoreMessages) break
        }
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
