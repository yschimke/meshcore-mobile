package ee.schimke.meshcore.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import ee.schimke.meshcore.data.entity.ChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
  @Query("SELECT * FROM channel WHERE deviceId = :deviceId ORDER BY channelIndex ASC")
  fun observeByDevice(deviceId: String): Flow<List<ChannelEntity>>

  @Query("SELECT * FROM channel WHERE deviceId = :deviceId")
  suspend fun getByDevice(deviceId: String): List<ChannelEntity>

  @Upsert suspend fun upsertAll(channels: List<ChannelEntity>)

  @Query("DELETE FROM channel WHERE deviceId = :deviceId")
  suspend fun deleteByDevice(deviceId: String)

  @Transaction
  suspend fun replaceAll(deviceId: String, channels: List<ChannelEntity>) {
    deleteByDevice(deviceId)
    upsertAll(channels)
  }
}
