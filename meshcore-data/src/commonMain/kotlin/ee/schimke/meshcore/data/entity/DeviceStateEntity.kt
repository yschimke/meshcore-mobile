package ee.schimke.meshcore.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
  tableName = "device_state",
  foreignKeys =
    [
      ForeignKey(
        entity = DeviceEntity::class,
        parentColumns = ["id"],
        childColumns = ["deviceId"],
        onDelete = ForeignKey.CASCADE,
      )
    ],
)
data class DeviceStateEntity(
  @PrimaryKey val deviceId: String,
  // SelfInfo
  val selfName: String? = null,
  val selfAdvertType: Int? = null,
  val selfTxPowerDbm: Int? = null,
  val selfMaxPowerDbm: Int? = null,
  val selfPublicKey: ByteArray? = null,
  val selfLatitude: Double? = null,
  val selfLongitude: Double? = null,
  val selfInfoFetchedAtMs: Long = 0L,
  // Battery
  val batteryMillivolts: Int? = null,
  val storageUsedKb: Long? = null,
  val storageTotalKb: Long? = null,
  val batteryFetchedAtMs: Long = 0L,
  // Radio
  val radioFrequencyHz: Int? = null,
  val radioBandwidthHz: Int? = null,
  val radioSpreadingFactor: Int? = null,
  val radioCodingRate: Int? = null,
  val radioFetchedAtMs: Long = 0L,
  // DeviceInfo
  val protocolVersion: Int? = null,
  val maxContacts: Int? = null,
  val maxChannels: Int? = null,
  val deviceInfoFetchedAtMs: Long = 0L,
)
