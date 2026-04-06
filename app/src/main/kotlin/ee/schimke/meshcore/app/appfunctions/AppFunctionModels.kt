package ee.schimke.meshcore.app.appfunctions

import androidx.appfunctions.AppFunctionSerializable

/**
 * Status of the connected mesh device including battery, radio, and storage info.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class DeviceStatus(
    /** The device name. */
    val name: String,
    /** Estimated battery percentage (0-100). */
    val batteryPercent: Int?,
    /** Raw battery voltage in millivolts. */
    val batteryMillivolts: Int?,
    /** Radio frequency in MHz. */
    val frequencyMhz: Double?,
    /** Radio bandwidth in kHz. */
    val bandwidthKhz: Int?,
    /** Radio spreading factor. */
    val spreadingFactor: Int?,
    /** Storage used in KB. */
    val storageUsedKb: Long?,
    /** Storage total in KB. */
    val storageTotalKb: Long?,
)

/**
 * A contact in the mesh network.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MeshContact(
    /** The contact's display name. */
    val name: String,
    /** The contact type (e.g. CHAT, REPEATER, ROOM, SENSOR). */
    val type: String,
    /** Number of hops to reach this contact. */
    val pathLength: Int,
    /** The contact's latitude, or 0.0 if unknown. */
    val latitude: Double,
    /** The contact's longitude, or 0.0 if unknown. */
    val longitude: Double,
    /** The contact's public key in hex. */
    val publicKeyHex: String,
)

/**
 * A recent message (DM or channel) on the mesh network.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class RecentMessage(
    /** The message text. */
    val text: String,
    /** The sender's name, if known. */
    val senderName: String?,
    /** The message kind: DM or CHANNEL. */
    val kind: String,
    /** Whether this message was SENT or RECEIVED. */
    val direction: String,
    /** Timestamp in epoch milliseconds. */
    val timestampMs: Long,
)

/**
 * A channel available on the mesh device.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MeshChannel(
    /** The channel index. */
    val index: Int,
    /** The channel name. */
    val name: String,
)

/**
 * Result of sending a message.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class SendResult(
    /** Whether the message was sent successfully. */
    val success: Boolean,
    /** Error message if the send failed. */
    val error: String?,
)
