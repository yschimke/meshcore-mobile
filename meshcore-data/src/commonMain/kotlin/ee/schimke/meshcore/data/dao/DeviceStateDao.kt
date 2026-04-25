package ee.schimke.meshcore.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import ee.schimke.meshcore.data.entity.DeviceStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceStateDao {
  @Query("SELECT * FROM device_state WHERE deviceId = :deviceId")
  suspend fun getByDeviceId(deviceId: String): DeviceStateEntity?

  @Query("SELECT * FROM device_state WHERE deviceId = :deviceId")
  fun observeByDeviceId(deviceId: String): Flow<DeviceStateEntity?>

  @Query("SELECT deviceId FROM device_state WHERE selfPublicKey = :pubkey LIMIT 1")
  suspend fun findDeviceIdByPublicKey(pubkey: ByteArray): String?

  @Upsert suspend fun upsert(state: DeviceStateEntity)
}
