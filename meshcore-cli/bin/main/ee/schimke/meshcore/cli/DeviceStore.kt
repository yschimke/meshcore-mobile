package ee.schimke.meshcore.cli

import ee.schimke.meshcore.app.data.proto.DeviceSnapshotPb
import ee.schimke.meshcore.app.data.proto.SavedDevicePb
import ee.schimke.meshcore.app.data.proto.SavedDevicesPb
import ee.schimke.meshcore.app.data.proto.TcpTransportPb
import java.io.File

/**
 * File-based device store backed by [SavedDevicesPb] proto.
 * Persists at `~/.meshcore/devices.pb` so the CLI can remember
 * the last-used device across invocations.
 */
class DeviceStore(
    private val file: File = File(System.getProperty("user.home"), ".meshcore/devices.pb"),
) {
    fun load(): SavedDevicesPb {
        if (!file.exists()) return SavedDevicesPb()
        return file.inputStream().use { SavedDevicesPb.ADAPTER.decode(it) }
    }

    fun save(data: SavedDevicesPb) {
        file.parentFile?.mkdirs()
        file.outputStream().use { SavedDevicesPb.ADAPTER.encode(it, data) }
    }

    /** Returns the favorite device's TCP host and port, or null. */
    fun getFavorite(): Pair<String, Int>? {
        val data = load()
        if (data.favorite_id.isBlank()) return null
        val dev = data.devices.firstOrNull { it.id == data.favorite_id } ?: return null
        val tcp = dev.tcp ?: return null
        return tcp.host to tcp.port
    }

    /**
     * Record a successful TCP connection. Upserts the device entry,
     * sets it as favorite, and optionally attaches a snapshot.
     */
    fun recordConnect(
        host: String,
        port: Int,
        label: String,
        snapshot: DeviceSnapshotPb? = null,
    ) {
        val id = "tcp:$host:$port"
        val now = System.currentTimeMillis()
        val data = load()
        val existing = data.devices.firstOrNull { it.id == id }
        val device = (existing ?: SavedDevicePb(id = id)).copy(
            label = label,
            last_connected_at_ms = now,
            tcp = TcpTransportPb(host = host, port = port),
            snapshot = snapshot ?: existing?.snapshot,
        )
        val devices = if (existing != null) {
            data.devices.map { if (it.id == id) device else it }
        } else {
            data.devices + device
        }
        save(data.copy(devices = devices, favorite_id = id))
    }
}
