package ee.schimke.meshcore.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
  tableName = "channel",
  primaryKeys = ["deviceId", "channelIndex"],
  foreignKeys =
    [
      ForeignKey(
        entity = DeviceEntity::class,
        parentColumns = ["id"],
        childColumns = ["deviceId"],
        onDelete = ForeignKey.CASCADE,
      )
    ],
  indices = [Index("deviceId")],
)
data class ChannelEntity(
  val deviceId: String,
  val channelIndex: Int,
  val name: String,
  val psk: ByteArray,
  val fetchedAtMs: Long,
)
