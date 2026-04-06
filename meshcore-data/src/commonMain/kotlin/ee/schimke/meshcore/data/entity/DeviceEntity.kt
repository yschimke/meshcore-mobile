package ee.schimke.meshcore.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TransportType { BLE, TCP, USB }

@Entity(tableName = "device")
data class DeviceEntity(
    @PrimaryKey val id: String,
    val label: String,
    val isFavorite: Boolean = false,
    val lastConnectedAtMs: Long = 0L,
    val transportType: TransportType,
    // BLE
    val bleIdentifier: String? = null,
    val bleAdvertName: String? = null,
    // TCP
    val tcpHost: String? = null,
    val tcpPort: Int? = null,
    // USB
    val usbClassName: String? = null,
    val usbVendorId: Int? = null,
    val usbProductId: Int? = null,
)
