package ee.schimke.meshcore.data.repository

import ee.schimke.meshcore.core.model.BatteryInfo
import ee.schimke.meshcore.core.model.ChannelInfo
import ee.schimke.meshcore.core.model.Contact
import ee.schimke.meshcore.core.model.ContactType
import ee.schimke.meshcore.core.model.DeviceInfo
import ee.schimke.meshcore.core.model.PublicKey
import ee.schimke.meshcore.core.model.RadioSettings
import ee.schimke.meshcore.core.model.ReceivedChannelMessage
import ee.schimke.meshcore.core.model.ReceivedDirectMessage
import ee.schimke.meshcore.core.model.SelfInfo
import ee.schimke.meshcore.data.MeshcoreDatabase
import ee.schimke.meshcore.data.entity.ChannelEntity
import ee.schimke.meshcore.data.entity.ContactEntity
import ee.schimke.meshcore.data.entity.DeviceEntity
import ee.schimke.meshcore.data.entity.DeviceStateEntity
import ee.schimke.meshcore.data.entity.MessageDirection
import ee.schimke.meshcore.data.entity.MessageEntity
import ee.schimke.meshcore.data.entity.MessageKind
import ee.schimke.meshcore.data.entity.MessageStatus
import ee.schimke.meshcore.data.entity.TransportType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.io.bytestring.ByteString
import kotlin.time.Instant

/** Reusable transport descriptor (same as the old SavedTransport). */
sealed class SavedTransport {
    data class Ble(val identifier: String, val advertName: String?) : SavedTransport()
    data class Tcp(val host: String, val port: Int) : SavedTransport()
    data class Usb(val className: String, val vendorId: Int, val productId: Int) : SavedTransport()
}

/** Domain-level saved device. */
data class SavedDevice(
    val id: String,
    val label: String,
    val transport: SavedTransport,
    val favorite: Boolean,
    val lastConnectedAtMs: Long,
)

/** Saved device enriched with cached state from Room. */
data class SavedDeviceWithState(
    val device: SavedDevice,
    val batteryMillivolts: Int? = null,
    val contactsCount: Int = 0,
)

fun bleDeviceId(identifier: String): String = "ble:$identifier"
fun tcpDeviceId(host: String, port: Int): String = "tcp:$host:$port"
fun usbDeviceId(className: String, vid: Int, pid: Int): String = "usb:$className:$vid:$pid"

class MeshcoreRepository(private val db: MeshcoreDatabase) {

    // --- Devices ---

    fun observeDevices(): Flow<List<SavedDevice>> =
        db.deviceDao().observeAll().map { list -> list.map { it.toDomain() } }

    /** Saved devices enriched with cached battery + contact count from Room. */
    @Suppress("OPT_IN_USAGE")
    fun observeDevicesWithState(): Flow<List<SavedDeviceWithState>> =
        db.deviceDao().observeAll().flatMapLatest { devices ->
            if (devices.isEmpty()) return@flatMapLatest flowOf(emptyList())
            val flows = devices.map { entity ->
                combine(
                    db.deviceStateDao().observeByDeviceId(entity.id),
                    db.contactDao().countByDevice(entity.id),
                ) { state, count ->
                    SavedDeviceWithState(
                        device = entity.toDomain(),
                        batteryMillivolts = state?.batteryMillivolts,
                        contactsCount = count,
                    )
                }
            }
            combine(flows) { it.toList() }
        }

    fun observeFavorite(): Flow<SavedDevice?> =
        db.deviceDao().observeFavorite().map { it?.toDomain() }

    suspend fun getDevice(id: String): SavedDevice? =
        db.deviceDao().getById(id)?.toDomain()

    /**
     * Find an existing device by its public key (from SelfInfo).
     * Returns the device ID if found, null otherwise.
     * Used to merge devices connected via different transports (BLE vs USB).
     */
    suspend fun findDeviceIdByPublicKey(publicKey: ee.schimke.meshcore.core.model.PublicKey): String? =
        db.deviceStateDao().findDeviceIdByPublicKey(publicKey.bytes.toByteArray())

    /**
     * Merge a transport-specific device entry into an existing canonical entry.
     * Moves the transport details and deletes the old entry.
     */
    suspend fun mergeDevice(fromId: String, intoId: String, transport: SavedTransport) {
        val into = db.deviceDao().getById(intoId) ?: return
        // Update the canonical entry with the new transport details
        db.deviceDao().upsert(
            into.copy(
                transportType = transport.toType(),
                bleIdentifier = (transport as? SavedTransport.Ble)?.identifier ?: into.bleIdentifier,
                bleAdvertName = (transport as? SavedTransport.Ble)?.advertName ?: into.bleAdvertName,
                tcpHost = (transport as? SavedTransport.Tcp)?.host ?: into.tcpHost,
                tcpPort = (transport as? SavedTransport.Tcp)?.port ?: into.tcpPort,
                usbClassName = (transport as? SavedTransport.Usb)?.className ?: into.usbClassName,
                usbVendorId = (transport as? SavedTransport.Usb)?.vendorId ?: into.usbVendorId,
                usbProductId = (transport as? SavedTransport.Usb)?.productId ?: into.usbProductId,
                lastConnectedAtMs = System.currentTimeMillis(),
            ),
        )
        // Delete the transport-specific entry (CASCADE removes its state/contacts/etc)
        if (fromId != intoId) {
            db.deviceDao().delete(fromId)
        }
    }

    suspend fun upsertDevice(
        id: String,
        label: String,
        transport: SavedTransport,
        markConnectedNow: Boolean = true,
    ) {
        val existing = db.deviceDao().getById(id)
        db.deviceDao().upsert(
            DeviceEntity(
                id = id,
                label = label,
                isFavorite = existing?.isFavorite ?: false,
                lastConnectedAtMs = if (markConnectedNow) System.currentTimeMillis() else (existing?.lastConnectedAtMs ?: 0L),
                transportType = transport.toType(),
                bleIdentifier = (transport as? SavedTransport.Ble)?.identifier,
                bleAdvertName = (transport as? SavedTransport.Ble)?.advertName,
                tcpHost = (transport as? SavedTransport.Tcp)?.host,
                tcpPort = (transport as? SavedTransport.Tcp)?.port,
                usbClassName = (transport as? SavedTransport.Usb)?.className,
                usbVendorId = (transport as? SavedTransport.Usb)?.vendorId,
                usbProductId = (transport as? SavedTransport.Usb)?.productId,
            ),
        )
    }

    suspend fun forgetDevice(id: String) {
        db.deviceDao().delete(id) // CASCADE deletes contacts, channels, messages, state
    }

    suspend fun toggleFavorite(id: String) {
        db.deviceDao().toggleFavorite(id)
    }

    // --- Device State ---

    suspend fun getDeviceState(deviceId: String): DeviceStateEntity? =
        db.deviceStateDao().getByDeviceId(deviceId)

    fun observeDeviceState(deviceId: String): Flow<DeviceStateEntity?> =
        db.deviceStateDao().observeByDeviceId(deviceId)

    suspend fun updateSelfInfo(deviceId: String, selfInfo: SelfInfo, fetchedAtMs: Long) {
        val existing = db.deviceStateDao().getByDeviceId(deviceId) ?: DeviceStateEntity(deviceId = deviceId)
        db.deviceStateDao().upsert(
            existing.copy(
                selfName = selfInfo.name,
                selfAdvertType = selfInfo.advertType,
                selfTxPowerDbm = selfInfo.txPowerDbm,
                selfMaxPowerDbm = selfInfo.maxPowerDbm,
                selfPublicKey = selfInfo.publicKey.bytes.toByteArray(),
                selfLatitude = selfInfo.latitude,
                selfLongitude = selfInfo.longitude,
                selfInfoFetchedAtMs = fetchedAtMs,
            ),
        )
        // Also update the device label with the actual name
        db.deviceDao().getById(deviceId)?.let { device ->
            db.deviceDao().upsert(device.copy(label = selfInfo.name))
        }
    }

    suspend fun updateBattery(deviceId: String, battery: BatteryInfo, fetchedAtMs: Long) {
        val existing = db.deviceStateDao().getByDeviceId(deviceId) ?: DeviceStateEntity(deviceId = deviceId)
        db.deviceStateDao().upsert(
            existing.copy(
                batteryMillivolts = battery.millivolts,
                storageUsedKb = battery.storageUsedKb,
                storageTotalKb = battery.storageTotalKb,
                batteryFetchedAtMs = fetchedAtMs,
            ),
        )
    }

    suspend fun updateRadio(deviceId: String, radio: RadioSettings, fetchedAtMs: Long) {
        val existing = db.deviceStateDao().getByDeviceId(deviceId) ?: DeviceStateEntity(deviceId = deviceId)
        db.deviceStateDao().upsert(
            existing.copy(
                radioFrequencyHz = radio.frequencyHz,
                radioBandwidthHz = radio.bandwidthHz,
                radioSpreadingFactor = radio.spreadingFactor,
                radioCodingRate = radio.codingRate,
                radioFetchedAtMs = fetchedAtMs,
            ),
        )
    }

    suspend fun updateDeviceInfo(deviceId: String, deviceInfo: DeviceInfo, fetchedAtMs: Long) {
        val existing = db.deviceStateDao().getByDeviceId(deviceId) ?: DeviceStateEntity(deviceId = deviceId)
        db.deviceStateDao().upsert(
            existing.copy(
                protocolVersion = deviceInfo.protocolVersion,
                maxContacts = deviceInfo.maxContacts,
                maxChannels = deviceInfo.maxChannels,
                deviceInfoFetchedAtMs = fetchedAtMs,
            ),
        )
    }

    // --- Contacts ---

    fun observeContacts(deviceId: String): Flow<List<Contact>> =
        db.contactDao().observeByDevice(deviceId).map { list -> list.map { it.toDomain() } }

    suspend fun getContacts(deviceId: String): List<Contact> =
        db.contactDao().getByDevice(deviceId).map { it.toDomain() }

    suspend fun replaceContacts(deviceId: String, contacts: List<Contact>, fetchedAtMs: Long) {
        db.contactDao().replaceAll(
            deviceId,
            contacts.map { it.toEntity(deviceId, fetchedAtMs) },
        )
    }

    // --- Channels ---

    fun observeChannels(deviceId: String): Flow<List<ChannelInfo>> =
        db.channelDao().observeByDevice(deviceId).map { list -> list.map { it.toDomain() } }

    suspend fun getChannels(deviceId: String): List<ChannelInfo> =
        db.channelDao().getByDevice(deviceId).map { it.toDomain() }

    suspend fun replaceChannels(deviceId: String, channels: List<ChannelInfo>, fetchedAtMs: Long) {
        db.channelDao().replaceAll(
            deviceId,
            channels.map { it.toEntity(deviceId, fetchedAtMs) },
        )
    }

    // --- Messages ---

    suspend fun getRecentMessages(deviceId: String, limit: Int = 20): List<MessageEntity> =
        db.messageDao().getRecentMessages(deviceId, limit)

    fun observeDms(deviceId: String, contactKeyHex: String): Flow<List<MessageEntity>> =
        db.messageDao().observeDms(deviceId, contactKeyHex)

    fun observeChannelMessages(deviceId: String, channelIndex: Int): Flow<List<MessageEntity>> =
        db.messageDao().observeChannelMessages(deviceId, channelIndex)

    fun observeLatestMessage(deviceId: String): Flow<MessageEntity?> =
        db.messageDao().observeLatestMessage(deviceId)

    fun observeContactedKeys(deviceId: String): Flow<List<String>> =
        db.messageDao().observeContactedKeys(deviceId)

    suspend fun insertReceivedDm(
        deviceId: String,
        msg: ReceivedDirectMessage,
        resolvedContactKeyHex: String,
    ) {
        val timestampMs = msg.timestamp.toEpochMilliseconds()
        if (db.messageDao().countDuplicates(deviceId, MessageKind.DM, timestampMs, msg.text) > 0) return
        db.messageDao().insert(
            MessageEntity(
                deviceId = deviceId,
                kind = MessageKind.DM,
                direction = MessageDirection.RECEIVED,
                contactPublicKeyHex = resolvedContactKeyHex,
                text = msg.text,
                timestampEpochMs = timestampMs,
                textType = msg.textType.raw.toInt(),
                snr = msg.snr,
                pathLength = msg.pathLength,
            ),
        )
    }

    suspend fun insertReceivedChannelMessage(deviceId: String, msg: ReceivedChannelMessage) {
        val timestampMs = msg.timestamp.toEpochMilliseconds()
        if (db.messageDao().countDuplicates(deviceId, MessageKind.CHANNEL, timestampMs, msg.text) > 0) return
        db.messageDao().insert(
            MessageEntity(
                deviceId = deviceId,
                kind = MessageKind.CHANNEL,
                direction = MessageDirection.RECEIVED,
                channelIndex = msg.channelIndex,
                senderName = msg.sender,
                text = msg.text,
                timestampEpochMs = timestampMs,
                textType = msg.textType.raw.toInt(),
                snr = msg.snr,
                pathLength = msg.pathLength,
            ),
        )
    }

    suspend fun insertSentDm(
        deviceId: String,
        contactKeyHex: String,
        text: String,
        timestamp: Instant,
        ackHash: Int?,
        status: MessageStatus = MessageStatus.SENT,
    ) {
        db.messageDao().insert(
            MessageEntity(
                deviceId = deviceId,
                kind = MessageKind.DM,
                direction = MessageDirection.SENT,
                contactPublicKeyHex = contactKeyHex,
                text = text,
                timestampEpochMs = timestamp.toEpochMilliseconds(),
                ackHash = ackHash,
                status = status,
            ),
        )
    }

    suspend fun markConfirmed(ackHash: Int) {
        db.messageDao().updateStatusByAckHash(ackHash, MessageStatus.CONFIRMED)
    }

    suspend fun markFailed(ackHash: Int) {
        db.messageDao().updateStatusByAckHash(ackHash, MessageStatus.FAILED)
    }
}

// --- Entity ↔ Domain mapping ------------------------------------------------

private fun DeviceEntity.toDomain(): SavedDevice = SavedDevice(
    id = id,
    label = label,
    transport = when (transportType) {
        TransportType.BLE -> SavedTransport.Ble(bleIdentifier ?: id, bleAdvertName)
        TransportType.TCP -> SavedTransport.Tcp(tcpHost ?: "", tcpPort ?: 0)
        TransportType.USB -> SavedTransport.Usb(usbClassName ?: "", usbVendorId ?: -1, usbProductId ?: -1)
    },
    favorite = isFavorite,
    lastConnectedAtMs = lastConnectedAtMs,
)

private fun SavedTransport.toType(): TransportType = when (this) {
    is SavedTransport.Ble -> TransportType.BLE
    is SavedTransport.Tcp -> TransportType.TCP
    is SavedTransport.Usb -> TransportType.USB
}

private fun ContactEntity.toDomain(): Contact = Contact(
    publicKey = PublicKey.fromBytes(ByteString(publicKeyBytes)),
    type = ContactType.fromRaw(type),
    flags = flags,
    pathLength = pathLength,
    path = ByteString(),
    name = name,
    advertTimestamp = Instant.fromEpochSeconds(advertTimestampEpochS),
    latitude = latitude,
    longitude = longitude,
    lastModified = Instant.fromEpochSeconds(lastModifiedEpochS),
)

private fun Contact.toEntity(deviceId: String, fetchedAtMs: Long): ContactEntity = ContactEntity(
    deviceId = deviceId,
    publicKeyHex = publicKey.toHex(),
    publicKeyBytes = publicKey.bytes.toByteArray(),
    type = type.raw,
    flags = flags,
    pathLength = pathLength,
    name = name,
    advertTimestampEpochS = advertTimestamp.epochSeconds,
    latitude = latitude,
    longitude = longitude,
    lastModifiedEpochS = lastModified.epochSeconds,
    fetchedAtMs = fetchedAtMs,
)

private fun ChannelEntity.toDomain(): ChannelInfo = ChannelInfo(
    index = channelIndex,
    name = name,
    psk = ByteString(psk),
)

private fun ChannelInfo.toEntity(deviceId: String, fetchedAtMs: Long): ChannelEntity = ChannelEntity(
    deviceId = deviceId,
    channelIndex = index,
    name = name,
    psk = psk.toByteArray(),
    fetchedAtMs = fetchedAtMs,
)

/** Reconstruct SelfInfo from DeviceStateEntity (for seedFromCache). */
fun DeviceStateEntity.toSelfInfo(): SelfInfo? {
    if (selfName == null) return null
    return SelfInfo(
        advertType = selfAdvertType ?: 0,
        txPowerDbm = selfTxPowerDbm ?: 0,
        maxPowerDbm = selfMaxPowerDbm ?: 0,
        publicKey = selfPublicKey?.let { PublicKey.fromBytes(ByteString(it)) }
            ?: PublicKey.fromBytes(ByteString(ByteArray(32))),
        latitude = selfLatitude ?: 0.0,
        longitude = selfLongitude ?: 0.0,
        multiAcks = 0,
        advertLocationPolicy = 0,
        telemetryFlags = 0,
        manualAddContacts = 0,
        radio = toRadio() ?: RadioSettings(0, 0, 0, 0),
        name = selfName,
    )
}

fun DeviceStateEntity.toBattery(): BatteryInfo? {
    if (batteryMillivolts == null) return null
    return BatteryInfo(batteryMillivolts, storageUsedKb ?: 0, storageTotalKb ?: 0)
}

fun DeviceStateEntity.toRadio(): RadioSettings? {
    if (radioFrequencyHz == null) return null
    return RadioSettings(radioFrequencyHz, radioBandwidthHz ?: 0, radioSpreadingFactor ?: 0, radioCodingRate ?: 0)
}

fun DeviceStateEntity.toDeviceInfo(): DeviceInfo? {
    if (protocolVersion == null) return null
    return DeviceInfo(protocolVersion, maxContacts ?: 0, maxChannels ?: 0)
}
