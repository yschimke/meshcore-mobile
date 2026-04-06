package ee.schimke.meshcore.app.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.squareup.wire.ProtoAdapter
import ee.schimke.meshcore.app.data.proto.BatteryInfoPb
import ee.schimke.meshcore.app.data.proto.BleTransportPb
import ee.schimke.meshcore.app.data.proto.ContactPb
import ee.schimke.meshcore.app.data.proto.DeviceInfoPb
import ee.schimke.meshcore.app.data.proto.DeviceSnapshotPb
import ee.schimke.meshcore.app.data.proto.RadioSettingsPb
import ee.schimke.meshcore.app.data.proto.SavedDevicePb
import ee.schimke.meshcore.app.data.proto.SavedDevicesPb
import ee.schimke.meshcore.app.data.proto.SelfInfoPb
import ee.schimke.meshcore.app.data.proto.TcpTransportPb
import ee.schimke.meshcore.app.data.proto.UsbTransportPb
import ee.schimke.meshcore.app.data.proto.ChannelInfoPb
import ee.schimke.meshcore.core.model.BatteryInfo
import ee.schimke.meshcore.core.model.ChannelInfo
import ee.schimke.meshcore.core.model.Contact
import ee.schimke.meshcore.core.model.ContactType
import ee.schimke.meshcore.core.model.DeviceInfo
import ee.schimke.meshcore.core.model.PublicKey
import ee.schimke.meshcore.core.model.RadioSettings
import ee.schimke.meshcore.core.model.SelfInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.time.Instant

// ---------------------------------------------------------------------------
// Saved-devices repository backed by a proto DataStore.
//
// The on-disk format is a [SavedDevicesPb] Wire-generated message. The
// raw proto types are only used at the storage boundary — the rest of
// the app talks in terms of [SavedDevice] / [SavedTransport], which are
// regular Kotlin data classes that the UI is free to reshape without
// touching the wire format.
// ---------------------------------------------------------------------------

/** Generic [Serializer] that delegates read/write to a Wire [ProtoAdapter]. */
internal class WireProtoSerializer<T : Any>(
    private val adapter: ProtoAdapter<T>,
    override val defaultValue: T,
) : Serializer<T> {
    override suspend fun readFrom(input: InputStream): T = try {
        adapter.decode(input)
    } catch (e: IOException) {
        throw CorruptionException("Unable to read ${adapter.type?.simpleName}", e)
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        adapter.encode(output, t)
    }
}

private val SavedDevicesSerializer = WireProtoSerializer(
    adapter = SavedDevicesPb.ADAPTER,
    defaultValue = SavedDevicesPb(),
)

// Separate DataStore file from the Preferences-backed theme store.
// `corruptionHandler` resets to an empty catalog if the file goes bad
// — losing the saved-devices list is annoying but recoverable; the
// alternative is a boot loop.
private val Context.savedDevicesStore: DataStore<SavedDevicesPb> by dataStore(
    fileName = "meshcore_devices.pb",
    serializer = SavedDevicesSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { SavedDevicesPb() },
)

// --- Domain model ---------------------------------------------------------

/** Domain-layer description of a previously-seen companion device. */
data class SavedDevice(
    val id: String,
    val label: String,
    val transport: SavedTransport,
    val favorite: Boolean,
    val lastConnectedAtMs: Long,
    val snapshot: DeviceSnapshot? = null,
)

sealed class SavedTransport {
    data class Ble(val identifier: String, val advertName: String?) : SavedTransport()
    data class Tcp(val host: String, val port: Int) : SavedTransport()
    data class Usb(val className: String, val vendorId: Int, val productId: Int) : SavedTransport()
}

/** Wraps a value with the wall-clock time it was fetched from the device. */
data class Timestamped<T>(val value: T, val fetchedAtMs: Long)

/** Cached snapshot of device state from the last successful connection. */
data class DeviceSnapshot(
    val selfInfo: Timestamped<SelfInfo>? = null,
    val contacts: Timestamped<List<Contact>>? = null,
    val battery: Timestamped<BatteryInfo>? = null,
    val radio: Timestamped<RadioSettings>? = null,
    val deviceInfo: Timestamped<DeviceInfo>? = null,
    val channels: Timestamped<List<ChannelInfo>>? = null,
) {
    val deviceName: String? get() = selfInfo?.value?.name
    val contactCount: Int get() = contacts?.value?.size ?: 0
    val channelCount: Int get() = channels?.value?.size ?: 0
}

fun bleDeviceId(identifier: String): String = "ble:$identifier"
fun tcpDeviceId(host: String, port: Int): String = "tcp:$host:$port"
fun usbDeviceId(className: String, vid: Int, pid: Int): String = "usb:$className:$vid:$pid"

// --- Repository -----------------------------------------------------------

/**
 * Read/write facade over the saved-devices proto DataStore. All public
 * methods are suspending so they can be called from the connection
 * controller's own scope — the UI never touches this directly.
 */
class SavedDevicesRepository(context: Context) {
    private val store: DataStore<SavedDevicesPb> =
        context.applicationContext.savedDevicesStore

    /** Live list of saved devices with the favorite (if any) first. */
    val devices: Flow<List<SavedDevice>> = store.data.map { pb ->
        pb.devices.map { it.toDomain(pb.favorite_id) }
            .sortedWith(
                compareByDescending<SavedDevice> { it.favorite }
                    .thenByDescending { it.lastConnectedAtMs },
            )
    }

    val favorite: Flow<SavedDevice?> = devices.map { list -> list.firstOrNull { it.favorite } }

    /** Snapshot lookup for a specific device. */
    suspend fun get(id: String): SavedDevice? =
        store.data.first().devices.firstOrNull { it.id == id }?.toDomain(
            store.data.first().favorite_id,
        )

    suspend fun snapshot(): List<SavedDevice> = devices.first()

    /** Insert-or-update a device entry. */
    suspend fun upsert(
        id: String,
        label: String,
        transport: SavedTransport,
        markConnectedNow: Boolean = true,
    ) {
        store.updateData { current ->
            val now = if (markConnectedNow) System.currentTimeMillis() else 0L
            val existing = current.devices.firstOrNull { it.id == id }
            val merged = SavedDevicePb(
                id = id,
                label = label,
                last_connected_at_ms = if (markConnectedNow) now else (existing?.last_connected_at_ms ?: 0L),
                ble = (transport as? SavedTransport.Ble)?.toPb(),
                tcp = (transport as? SavedTransport.Tcp)?.toPb(),
                usb = (transport as? SavedTransport.Usb)?.toPb(),
            )
            current.copy(
                devices = current.devices.filterNot { it.id == id } + merged,
            )
        }
    }

    /** Remove a device entry entirely, clearing the favorite if needed. */
    suspend fun forget(id: String) {
        store.updateData { current ->
            current.copy(
                devices = current.devices.filterNot { it.id == id },
                favorite_id = if (current.favorite_id == id) "" else current.favorite_id,
            )
        }
    }

    /** Set (or clear with null) the auto-connect favorite. */
    suspend fun setFavorite(id: String?) {
        store.updateData { current ->
            current.copy(favorite_id = id ?: "")
        }
    }

    /** Persist a device snapshot (selfInfo, contacts, battery, etc.) for [id]. */
    suspend fun updateSnapshot(id: String, snapshot: DeviceSnapshot) {
        store.updateData { current ->
            val devices = current.devices.map { dev ->
                if (dev.id == id) {
                    val updatedLabel = snapshot.deviceName ?: dev.label
                    dev.copy(label = updatedLabel, snapshot = snapshot.toPb())
                } else {
                    dev
                }
            }
            current.copy(devices = devices)
        }
    }
}

// --- Conversions ----------------------------------------------------------

private fun SavedDevicePb.toDomain(favoriteId: String): SavedDevice {
    val transport: SavedTransport = when {
        ble != null -> SavedTransport.Ble(
            identifier = ble!!.identifier,
            advertName = ble!!.advert_name.ifBlank { null },
        )
        tcp != null -> SavedTransport.Tcp(host = tcp!!.host, port = tcp!!.port)
        usb != null -> SavedTransport.Usb(
            className = usb!!.class_name,
            vendorId = usb!!.vendor_id,
            productId = usb!!.product_id,
        )
        else -> SavedTransport.Ble(identifier = id, advertName = null) // fallback
    }
    return SavedDevice(
        id = id,
        label = label,
        transport = transport,
        favorite = favoriteId.isNotEmpty() && favoriteId == id,
        lastConnectedAtMs = last_connected_at_ms,
        snapshot = snapshot?.toDomain(),
    )
}

private fun SavedTransport.Ble.toPb() = BleTransportPb(
    identifier = identifier,
    advert_name = advertName.orEmpty(),
)

private fun SavedTransport.Tcp.toPb() = TcpTransportPb(host = host, port = port)

private fun SavedTransport.Usb.toPb() = UsbTransportPb(
    class_name = className,
    vendor_id = vendorId,
    product_id = productId,
)

// --- Snapshot conversions ---------------------------------------------------

private fun DeviceSnapshotPb.toDomain(): DeviceSnapshot = DeviceSnapshot(
    selfInfo = self_info?.toDomain()?.let { Timestamped(it, self_info_at_ms) },
    contacts = if (contacts_at_ms > 0) Timestamped(contacts.map { it.toDomain() }, contacts_at_ms) else null,
    battery = battery?.toDomain()?.let { Timestamped(it, battery_at_ms) },
    radio = radio?.toDomain()?.let { Timestamped(it, radio_at_ms) },
    deviceInfo = device_info?.toDomain()?.let { Timestamped(it, device_info_at_ms) },
    channels = if (channels_at_ms > 0) Timestamped(channels.map { it.toDomain() }, channels_at_ms) else null,
)

private fun DeviceSnapshot.toPb(): DeviceSnapshotPb = DeviceSnapshotPb(
    self_info = selfInfo?.value?.toPb(),
    self_info_at_ms = selfInfo?.fetchedAtMs ?: 0L,
    contacts = contacts?.value?.map { it.toPb() } ?: emptyList(),
    contacts_at_ms = contacts?.fetchedAtMs ?: 0L,
    battery = battery?.value?.toPb(),
    battery_at_ms = battery?.fetchedAtMs ?: 0L,
    radio = radio?.value?.toPb(),
    radio_at_ms = radio?.fetchedAtMs ?: 0L,
    device_info = deviceInfo?.value?.toPb(),
    device_info_at_ms = deviceInfo?.fetchedAtMs ?: 0L,
    channels = channels?.value?.map { it.toPb() } ?: emptyList(),
    channels_at_ms = channels?.fetchedAtMs ?: 0L,
)

// SelfInfo
private fun SelfInfoPb.toDomain(): SelfInfo = SelfInfo(
    advertType = advert_type,
    txPowerDbm = tx_power_dbm,
    maxPowerDbm = max_power_dbm,
    publicKey = PublicKey.fromBytes(
        kotlinx.io.bytestring.ByteString(public_key.toByteArray()),
    ),
    latitude = latitude,
    longitude = longitude,
    multiAcks = 0,
    advertLocationPolicy = 0,
    telemetryFlags = 0,
    manualAddContacts = 0,
    radio = RadioSettings(0, 0, 0, 0), // stored separately
    name = name,
)

private fun SelfInfo.toPb(): SelfInfoPb = SelfInfoPb(
    advert_type = advertType,
    tx_power_dbm = txPowerDbm,
    max_power_dbm = maxPowerDbm,
    public_key = okio.ByteString.of(*publicKey.bytes.toByteArray()),
    latitude = latitude,
    longitude = longitude,
    name = name,
)

// Contact
private fun ContactPb.toDomain(): Contact = Contact(
    publicKey = PublicKey.fromBytes(
        kotlinx.io.bytestring.ByteString(public_key.toByteArray()),
    ),
    type = ContactType.fromRaw(type),
    flags = flags,
    pathLength = path_length,
    path = kotlinx.io.bytestring.ByteString(),
    name = name,
    advertTimestamp = Instant.fromEpochSeconds(advert_timestamp_epoch_s),
    latitude = latitude,
    longitude = longitude,
    lastModified = Instant.fromEpochSeconds(last_modified_epoch_s),
)

private fun Contact.toPb(): ContactPb = ContactPb(
    public_key = okio.ByteString.of(*publicKey.bytes.toByteArray()),
    type = type.raw,
    flags = flags,
    path_length = pathLength,
    name = name,
    advert_timestamp_epoch_s = advertTimestamp.epochSeconds,
    latitude = latitude,
    longitude = longitude,
    last_modified_epoch_s = lastModified.epochSeconds,
)

// BatteryInfo
private fun BatteryInfoPb.toDomain(): BatteryInfo = BatteryInfo(
    millivolts = millivolts,
    storageUsedKb = storage_used_kb,
    storageTotalKb = storage_total_kb,
)

private fun BatteryInfo.toPb(): BatteryInfoPb = BatteryInfoPb(
    millivolts = millivolts,
    storage_used_kb = storageUsedKb,
    storage_total_kb = storageTotalKb,
)

// RadioSettings
private fun RadioSettingsPb.toDomain(): RadioSettings = RadioSettings(
    frequencyHz = frequency_hz,
    bandwidthHz = bandwidth_hz,
    spreadingFactor = spreading_factor,
    codingRate = coding_rate,
)

private fun RadioSettings.toPb(): RadioSettingsPb = RadioSettingsPb(
    frequency_hz = frequencyHz,
    bandwidth_hz = bandwidthHz,
    spreading_factor = spreadingFactor,
    coding_rate = codingRate,
)

// DeviceInfo
private fun DeviceInfoPb.toDomain(): DeviceInfo = DeviceInfo(
    protocolVersion = protocol_version,
    maxContacts = max_contacts,
    maxChannels = max_channels,
)

private fun DeviceInfo.toPb(): DeviceInfoPb = DeviceInfoPb(
    protocol_version = protocolVersion,
    max_contacts = maxContacts,
    max_channels = maxChannels,
)

// ChannelInfo
private fun ChannelInfoPb.toDomain(): ChannelInfo = ChannelInfo(
    index = index,
    name = name,
    psk = kotlinx.io.bytestring.ByteString(psk.toByteArray()),
)

private fun ChannelInfo.toPb(): ChannelInfoPb = ChannelInfoPb(
    index = index,
    name = name,
    psk = okio.ByteString.of(*psk.toByteArray()),
)
