package ee.schimke.meshcore.core.client

import ee.schimke.meshcore.core.model.BatteryInfo
import ee.schimke.meshcore.core.model.ChannelInfo
import ee.schimke.meshcore.core.model.Contact
import ee.schimke.meshcore.core.model.DeviceInfo
import ee.schimke.meshcore.core.model.MeshEvent
import ee.schimke.meshcore.core.model.PublicKey
import ee.schimke.meshcore.core.model.RadioSettings
import ee.schimke.meshcore.core.model.ReceivedChannelMessage
import ee.schimke.meshcore.core.model.ReceivedDirectMessage
import ee.schimke.meshcore.core.model.SelfInfo
import ee.schimke.meshcore.core.model.SendAck
import ee.schimke.meshcore.core.protocol.Frames
import ee.schimke.meshcore.core.protocol.MeshCoreConstants
import ee.schimke.meshcore.core.protocol.Parsers
import ee.schimke.meshcore.core.transport.Transport
import ee.schimke.meshcore.core.transport.TransportState
import kotlin.time.Instant
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

/**
 * The command surface of a [MeshCoreClient] that is only meaningful once the handshake has
 * completed. [MeshCoreClient.start] returns this handle so new code can thread the *started*
 * capability through its types: a function that issues device commands can accept a
 * [StartedMeshCoreClient] and the compiler then guarantees the caller went through `start()`.
 *
 * [MeshCoreClient] itself implements this interface (so existing `client.getContacts()` call sites
 * keep working), but those calls are guarded at runtime — see [MeshCoreClient.enforceLifecycle].
 */
interface StartedMeshCoreClient {
  suspend fun login(recipient: PublicKey, password: String, timeoutMs: Long = 15_000)

  suspend fun getContacts(delta: Boolean = false, timeoutMs: Long = 5_000): List<Contact>

  suspend fun getChannels(perChannelTimeoutMs: Long = 1_000): List<ChannelInfo>

  suspend fun setChannel(index: Int, name: String, psk: ByteString, timeoutMs: Long = 2_000)

  suspend fun syncMessages(perMessageTimeoutMs: Long = 5_000)

  suspend fun getBatteryAndStorage(timeoutMs: Long = 3_000): BatteryInfo

  suspend fun getRadioSettings(timeoutMs: Long = 3_000): RadioSettings

  suspend fun getDeviceTime(timeoutMs: Long = 3_000): Instant

  suspend fun setDeviceTime(time: Instant)

  suspend fun setAdvertName(name: String)

  suspend fun setAdvertLatLon(lat: Double, lon: Double)

  suspend fun setRadioParams(freqHz: Int, bwHz: Int, sf: Int, cr: Int)

  suspend fun setRadioTxPower(dbm: Int)

  suspend fun sendSelfAdvert(floodMode: Boolean = false)

  suspend fun reboot()

  suspend fun syncNextMessage()

  suspend fun sendText(
    recipient: PublicKey,
    text: String,
    timestamp: Instant,
    attempt: Int = 0,
    timeoutMs: Long = 5_000,
  ): SendAck

  suspend fun sendChannelText(
    channelIdx: Int,
    text: String,
    timestamp: Instant,
    timeoutMs: Long = 5_000,
  ): SendAck
}

/**
 * High-level coroutine API over a [Transport]. Construct one per active device connection; call
 * [start] after the transport is connected.
 *
 * Lifecycle is enforced: command methods (`getContacts`, `sendText`, …) throw
 * [IllegalStateException] until [start] has completed the handshake. Prefer the typed
 * [StartedMeshCoreClient] handle that [start] returns for new code. The explicit escape hatch for
 * callers that must issue commands without a handshake (frame replay, tests against a canned
 * transport) is to construct with `enforceLifecycle = false`.
 *
 * @param logger sink for diagnostic output. Defaults to [Logger.None]; pass [Logger.Println] (or a
 *   platform logger) to see the protocol trace.
 * @param enforceLifecycle when true (the default), command methods require a prior [start].
 */
class MeshCoreClient(
  private val transport: Transport,
  private val scope: CoroutineScope,
  private val logger: Logger = Logger.None,
  private val enforceLifecycle: Boolean = true,
) : StartedMeshCoreClient {
  private fun log(msg: String) = logger.debug("[MeshCoreClient] $msg")

  @Volatile private var started = false

  private fun ensureStarted() {
    check(!enforceLifecycle || started) {
      "MeshCoreClient.start() must complete before issuing device commands. To bypass this " +
        "(e.g. replaying captured frames or testing), construct with enforceLifecycle = false."
    }
  }

  private val _events = MutableSharedFlow<MeshEvent>(replay = 0, extraBufferCapacity = 64)
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
  private val _directMessages =
    MutableStateFlow<Map<String, List<ReceivedDirectMessage>>>(emptyMap())
  val directMessages: StateFlow<Map<String, List<ReceivedDirectMessage>>> =
    _directMessages.asStateFlow()

  /** Accumulated channel messages keyed by channel index. */
  private val _channelMessages =
    MutableStateFlow<Map<Int, List<ReceivedChannelMessage>>>(emptyMap())
  val channelMessages: StateFlow<Map<Int, List<ReceivedChannelMessage>>> =
    _channelMessages.asStateFlow()

  val connection: StateFlow<TransportState>
    get() = transport.state

  /**
   * Pre-populate StateFlows with cached data from a previous session so the UI has something to
   * show immediately while fresh data loads. Only sets values that are still null/empty — live data
   * from the device takes priority.
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

  /** Timestamp of the last successful contacts fetch, for delta queries. */
  @Volatile
  var lastContactsFetchedAt: Instant? = null
    private set

  /**
   * Start the protocol session. Sends AppStart + device queries and waits for the SelfInfo response
   * (up to [timeoutMs]) so the caller knows the handshake completed. Battery, radio, and
   * device-info responses may still arrive after this returns — they populate their StateFlows
   * asynchronously.
   */
  suspend fun start(
    appName: String = "meshcore-kmp",
    appVersion: Int = MeshCoreConstants.APP_PROTOCOL_VERSION,
    timeoutMs: Long = 5_000,
  ): StartedMeshCoreClient {
    if (pumpJob == null) {
      pumpJob =
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
          transport.incoming.collect { frame -> handleEvent(Parsers.parse(frame)) }
        }
    }
    log("start: sending AppStart + queries")
    // Subscribe for SelfInfo before sending, then send all queries
    coroutineScope {
      val selfInfoDeferred =
        async(start = CoroutineStart.UNDISPATCHED) {
          withTimeout(timeoutMs) { events.filter { it is MeshEvent.SelfInfoEvent }.first() }
        }
      transport.send(Frames.appStart(appName, appVersion))
      log("start: AppStart sent")
      runCatching { transport.send(Frames.deviceQuery()) }
      runCatching { transport.send(Frames.getBatteryAndStorage()) }
      runCatching { transport.send(Frames.getRadioSettings()) }
      log("start: all queries sent, waiting for SelfInfo (${timeoutMs}ms timeout)")
      try {
        selfInfoDeferred.await()
        log("start: SelfInfo received — name='${_selfInfo.value?.name}'")
      } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        log("start: SelfInfo timeout — device may not be running MeshCore")
        throw IllegalStateException(
          "No response from device. It may not be running MeshCore firmware, " +
            "or the connection is not working.",
          e,
        )
      }
    }
    started = true
    return this
  }

  /**
   * Authenticate to [recipient] (a repeater or room contact) using [password]. Awaits a
   * `LoginSuccess` / `LoginFail` push from the device; throws on failure or after [timeoutMs]. This
   * is a runtime operation on an established connection — it has nothing to do with the device
   * handshake.
   *
   * Wire format verified against the MeshCore Flutter client's `buildSendLoginFrame` in
   * `connector/meshcore_protocol.dart`.
   */
  override suspend fun login(recipient: PublicKey, password: String, timeoutMs: Long) {
    ensureStarted()
    val recipientPrefixHex = recipient.toHex().take(MeshCoreConstants.PUB_KEY_PREFIX_SIZE * 2)
    val ev =
      requestOne(Frames.sendLogin(recipient, password), timeoutMs) { event ->
        val evKey =
          when (event) {
            is MeshEvent.LoginSuccess -> event.publicKey
            is MeshEvent.LoginFail -> event.publicKey
            else -> return@requestOne false
          }
        evKey.toHex().startsWith(recipientPrefixHex)
      }
    if (ev is MeshEvent.LoginFail) error("device rejected login for ${recipient.toHex().take(12)}")
  }

  fun stop() {
    pumpJob?.cancel()
    pumpJob = null
    started = false
  }

  private suspend fun handleEvent(event: MeshEvent) {
    when (event) {
      is MeshEvent.SelfInfoEvent -> {
        log("event: SelfInfo name='${event.info.name}'")
        _selfInfo.value = event.info
        _radio.value = event.info.radio
      }
      is MeshEvent.Radio -> {
        log("event: Radio ${event.settings.frequencyHz}Hz")
        _radio.value = event.settings
      }
      is MeshEvent.Battery -> {
        log(
          "event: Battery ${event.info.millivolts}mV storage=${event.info.storageUsedKb}/${event.info.storageTotalKb}kB"
        )
        _battery.value = event.info
      }
      is MeshEvent.Device -> {
        log(
          "event: DeviceInfo proto=${event.info.protocolVersion} contacts=${event.info.maxContacts} channels=${event.info.maxChannels}"
        )
        _device.value = event.info
      }
      MeshEvent.ContactsStart -> contactsAccumulator.clear()
      is MeshEvent.ContactEvent -> contactsAccumulator += event.contact
      MeshEvent.EndOfContacts -> {
        log("event: EndOfContacts count=${contactsAccumulator.size}")
        _contacts.value = contactsAccumulator.toList()
        contactsAccumulator.clear()
      }
      is MeshEvent.ChannelInfoEvent -> {
        // Don't accumulate individual channel responses here —
        // getChannels() sets _channels atomically after filtering
        // empty entries. Accumulating here causes transient ghost
        // channels (e.g. "Channel 4", "Channel 5") in the UI.
      }
      is MeshEvent.DirectMessage -> {
        val msg = event.message
        val prefix = msg.senderPrefix.toHex()
        val contactKey =
          _contacts.value
            .firstOrNull { it.publicKey.toHex().startsWith(prefix) }
            ?.publicKey
            ?.toHex() ?: prefix
        log("event: DirectMessage from=${prefix.take(12)} text='${msg.text.take(30)}'")
        val current = _directMessages.value
        _directMessages.value = current + (contactKey to (current[contactKey].orEmpty() + msg))
      }
      is MeshEvent.ChannelMessage -> {
        val msg = event.message
        log("event: ChannelMessage ch=${msg.channelIndex} body='${msg.body.take(30)}'")
        val idx = msg.channelIndex
        val current = _channelMessages.value
        _channelMessages.value = current + (idx to (current[idx].orEmpty() + msg))
      }
      MeshEvent.MessagesWaiting -> log("event: MessagesWaiting")
      MeshEvent.NoMoreMessages -> log("event: NoMoreMessages")
      is MeshEvent.Raw ->
        // The parser hands back Raw for any frame it doesn't recognise or fails to decode. With no
        // correlation IDs in the wire protocol there's nothing to match it against, so the best we
        // can do is warn: an unexpected frame type usually means a firmware/protocol-version skew
        // or a framing bug, and a silent drop hides both.
        logger.warn(
          "[MeshCoreClient] unexpected frame: code=0x${event.code.toString(16)} " +
            "size=${event.body.size} (no parser matched or decode failed)"
        )
      else -> Unit
    }
    _events.emit(event)
  }

  /**
   * Fetch contacts from the device. If [delta] is true and a previous fetch timestamp exists, only
   * requests contacts modified since then.
   */
  override suspend fun getContacts(delta: Boolean, timeoutMs: Long): List<Contact> {
    ensureStarted()
    return sendMutex.withLock {
      val since = if (delta) lastContactsFetchedAt else null
      log("getContacts: requesting (delta=$delta, since=$since)")
      coroutineScope {
        val deferred =
          async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(timeoutMs) { events.filter { it is MeshEvent.EndOfContacts }.first() }
          }
        transport.send(Frames.getContacts(since))
        deferred.await()
      }
      lastContactsFetchedAt = kotlin.time.Clock.System.now()
      log("getContacts: done, ${_contacts.value.size} contacts")
      _contacts.value
    }
  }

  /**
   * Enumerate all configured channels by requesting each index from 0 until
   * [DeviceInfo.maxChannels]. Empty channels are filtered out.
   */
  override suspend fun getChannels(perChannelTimeoutMs: Long): List<ChannelInfo> {
    ensureStarted()
    val maxCh = _device.value?.maxChannels ?: 8
    log("getChannels: requesting 0..$maxCh")
    val result = mutableListOf<ChannelInfo>()
    for (i in 0 until maxCh) {
      val ev =
        runCatching {
            requestOne(Frames.getChannel(i), perChannelTimeoutMs) {
              it is MeshEvent.ChannelInfoEvent
            }
          }
          .getOrNull() as? MeshEvent.ChannelInfoEvent ?: continue
      val ch = ev.info
      val isEmpty = ch.name.isBlank() && ch.psk.toByteArray().all { it == 0.toByte() }
      if (!isEmpty) result += ch
    }
    _channels.value = result
    log("getChannels: done, ${result.size} non-empty channels")
    return result
  }

  /**
   * Create or update a channel on the device. After a successful call the local channel list is
   * refreshed.
   */
  override suspend fun setChannel(index: Int, name: String, psk: ByteString, timeoutMs: Long) {
    ensureStarted()
    val frame = Frames.setChannel(index, name, psk)
    val ev = requestOne(frame, timeoutMs) { it is MeshEvent.Ok || it is MeshEvent.Err }
    if (ev is MeshEvent.Err) error("setChannel failed: error code ${ev.code}")
    log("setChannel($index, '$name') ok")
    getChannels() // refresh local cache
  }

  /**
   * Drain the device's pending message queue by repeatedly sending `SyncNextMessage` until the
   * device replies with `NoMoreMessages`. Each iteration yields a `DirectMessage` or
   * `ChannelMessage` event through the [events] SharedFlow as usual.
   */
  override suspend fun syncMessages(perMessageTimeoutMs: Long) {
    ensureStarted()
    log("syncMessages: draining pending queue")
    var count = 0
    while (true) {
      val ev =
        runCatching {
            requestOne(Frames.syncNextMessage(), perMessageTimeoutMs) {
              it is MeshEvent.DirectMessage ||
                it is MeshEvent.ChannelMessage ||
                it is MeshEvent.NoMoreMessages
            }
          }
          .getOrNull() ?: break
      if (ev is MeshEvent.NoMoreMessages) break
      count++
    }
    log("syncMessages: done, $count messages received")
  }

  override suspend fun getBatteryAndStorage(timeoutMs: Long): BatteryInfo {
    ensureStarted()
    return (requestOne(Frames.getBatteryAndStorage(), timeoutMs) { it is MeshEvent.Battery }
        as MeshEvent.Battery)
      .info
  }

  override suspend fun getRadioSettings(timeoutMs: Long): RadioSettings {
    ensureStarted()
    return (requestOne(Frames.getRadioSettings(), timeoutMs) { it is MeshEvent.Radio }
        as MeshEvent.Radio)
      .settings
  }

  override suspend fun getDeviceTime(timeoutMs: Long): Instant {
    ensureStarted()
    return (requestOne(Frames.getDeviceTime(), timeoutMs) { it is MeshEvent.CurrentTime }
        as MeshEvent.CurrentTime)
      .time
  }

  override suspend fun setDeviceTime(time: Instant) {
    ensureStarted()
    sendMutex.withLock { transport.send(Frames.setDeviceTime(time)) }
  }

  override suspend fun setAdvertName(name: String) {
    ensureStarted()
    sendMutex.withLock { transport.send(Frames.setAdvertName(name)) }
  }

  override suspend fun setAdvertLatLon(lat: Double, lon: Double) {
    ensureStarted()
    sendMutex.withLock { transport.send(Frames.setAdvertLatLon(lat, lon)) }
  }

  override suspend fun setRadioParams(freqHz: Int, bwHz: Int, sf: Int, cr: Int) {
    ensureStarted()
    sendMutex.withLock { transport.send(Frames.setRadioParams(freqHz, bwHz, sf, cr)) }
  }

  override suspend fun setRadioTxPower(dbm: Int) {
    ensureStarted()
    sendMutex.withLock { transport.send(Frames.setRadioTxPower(dbm)) }
  }

  override suspend fun sendSelfAdvert(floodMode: Boolean) {
    ensureStarted()
    sendMutex.withLock { transport.send(Frames.sendSelfAdvert(floodMode)) }
  }

  override suspend fun reboot() {
    ensureStarted()
    sendMutex.withLock { transport.send(Frames.reboot()) }
  }

  override suspend fun syncNextMessage() {
    ensureStarted()
    sendMutex.withLock { transport.send(Frames.syncNextMessage()) }
  }

  override suspend fun sendText(
    recipient: PublicKey,
    text: String,
    timestamp: Instant,
    attempt: Int,
    timeoutMs: Long,
  ): SendAck {
    ensureStarted()
    val frame = Frames.sendTextMessage(recipient, text, timestamp, attempt)
    val ev = requestOne(frame, timeoutMs) { it is MeshEvent.Sent || it is MeshEvent.Err }
    if (ev is MeshEvent.Err) error("device returned error code ${ev.code}")
    return (ev as MeshEvent.Sent).ack
  }

  override suspend fun sendChannelText(
    channelIdx: Int,
    text: String,
    timestamp: Instant,
    timeoutMs: Long,
  ): SendAck {
    ensureStarted()
    val frame = Frames.sendChannelTextMessage(channelIdx, text, timestamp)
    val ev = requestOne(frame, timeoutMs) { it is MeshEvent.Sent || it is MeshEvent.Err }
    if (ev is MeshEvent.Err) error("device returned error code ${ev.code}")
    return (ev as MeshEvent.Sent).ack
  }

  /**
   * Send a frame and await the first matching reply.
   *
   * The collector is attached **before** `transport.send` so a very fast reply (BLE round-trips can
   * be <10 ms) can't slip past an unattached `SharedFlow` — prior versions of this method had a
   * race where the pump emitted the reply and dropped it because `events` is replay=0 and the
   * collector wasn't subscribed yet.
   */
  private suspend fun requestOne(
    frame: ByteString,
    timeoutMs: Long,
    predicate: (MeshEvent) -> Boolean,
  ): MeshEvent = sendMutex.withLock {
    coroutineScope {
      val deferred =
        async(start = CoroutineStart.UNDISPATCHED) {
          withTimeout(timeoutMs) { events.filter(predicate).first() }
        }
      transport.send(frame)
      deferred.await()
    }
  }
}
