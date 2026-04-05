package ee.schimke.meshcore.app.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.squareup.wire.ProtoAdapter
import ee.schimke.meshcore.app.data.proto.BleTransportPb
import ee.schimke.meshcore.app.data.proto.SavedDevicePb
import ee.schimke.meshcore.app.data.proto.SavedDevicesPb
import ee.schimke.meshcore.app.data.proto.TcpTransportPb
import ee.schimke.meshcore.app.data.proto.UsbTransportPb
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

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
    val pin: String?,
    val transport: SavedTransport,
    val favorite: Boolean,
    val lastConnectedAtMs: Long,
)

sealed class SavedTransport {
    data class Ble(val identifier: String, val advertName: String?) : SavedTransport()
    data class Tcp(val host: String, val port: Int) : SavedTransport()
    data class Usb(val className: String, val vendorId: Int, val productId: Int) : SavedTransport()
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

    /** Snapshot lookup used during connect to resolve a PIN. */
    suspend fun get(id: String): SavedDevice? =
        store.data.first().devices.firstOrNull { it.id == id }?.toDomain(
            store.data.first().favorite_id,
        )

    suspend fun snapshot(): List<SavedDevice> = devices.first()

    /**
     * Insert-or-update a device entry. If [pin] is null the previous
     * PIN (if any) is preserved — callers who want to clear a PIN
     * should pass an empty string.
     */
    suspend fun upsert(
        id: String,
        label: String,
        transport: SavedTransport,
        pin: String? = null,
        markConnectedNow: Boolean = true,
    ) {
        store.updateData { current ->
            val now = if (markConnectedNow) System.currentTimeMillis() else 0L
            val existing = current.devices.firstOrNull { it.id == id }
            val merged = SavedDevicePb(
                id = id,
                label = label,
                last_connected_at_ms = if (markConnectedNow) now else (existing?.last_connected_at_ms ?: 0L),
                pin = pin ?: existing?.pin.orEmpty(),
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
        pin = pin.ifBlank { null },
        transport = transport,
        favorite = favoriteId.isNotEmpty() && favoriteId == id,
        lastConnectedAtMs = last_connected_at_ms,
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
