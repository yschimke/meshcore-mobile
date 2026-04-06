package ee.schimke.meshcore.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MessageKind { DM, CHANNEL }
enum class MessageDirection { SENT, RECEIVED }
enum class MessageStatus { SENDING, SENT, CONFIRMED, FAILED }

@Entity(
    tableName = "message",
    foreignKeys = [ForeignKey(
        entity = DeviceEntity::class,
        parentColumns = ["id"],
        childColumns = ["deviceId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index("deviceId", "contactPublicKeyHex"),
        Index("deviceId", "channelIndex"),
        Index("deviceId", "timestampEpochMs"),
    ],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val deviceId: String,
    val kind: MessageKind,
    val direction: MessageDirection,
    // DM fields (null for channel messages)
    val contactPublicKeyHex: String? = null,
    // Channel fields (null for DMs)
    val channelIndex: Int? = null,
    // Content
    val senderName: String? = null,
    val text: String,
    val timestampEpochMs: Long,
    val textType: Int = 0,
    // Received metadata
    val snr: Int? = null,
    val pathLength: Int? = null,
    // Sent tracking
    val ackHash: Int? = null,
    val status: MessageStatus = MessageStatus.SENT,
)
