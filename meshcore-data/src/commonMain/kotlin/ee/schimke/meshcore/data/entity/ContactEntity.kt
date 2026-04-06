package ee.schimke.meshcore.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "contact",
    primaryKeys = ["deviceId", "publicKeyHex"],
    foreignKeys = [ForeignKey(
        entity = DeviceEntity::class,
        parentColumns = ["id"],
        childColumns = ["deviceId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("deviceId")],
)
data class ContactEntity(
    val deviceId: String,
    val publicKeyHex: String,
    val publicKeyBytes: ByteArray,
    val type: Int,
    val flags: Int,
    val pathLength: Int,
    val name: String,
    val advertTimestampEpochS: Long,
    val latitude: Double,
    val longitude: Double,
    val lastModifiedEpochS: Long,
    val fetchedAtMs: Long,
)
