package ee.schimke.meshcore.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import ee.schimke.meshcore.data.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
  @Query("SELECT * FROM contact WHERE deviceId = :deviceId ORDER BY name ASC")
  fun observeByDevice(deviceId: String): Flow<List<ContactEntity>>

  @Query("SELECT * FROM contact WHERE deviceId = :deviceId")
  suspend fun getByDevice(deviceId: String): List<ContactEntity>

  @Query("SELECT COUNT(*) FROM contact WHERE deviceId = :deviceId")
  fun countByDevice(deviceId: String): Flow<Int>

  @Upsert suspend fun upsertAll(contacts: List<ContactEntity>)

  @Query("DELETE FROM contact WHERE deviceId = :deviceId")
  suspend fun deleteByDevice(deviceId: String)

  @Transaction
  suspend fun replaceAll(deviceId: String, contacts: List<ContactEntity>) {
    deleteByDevice(deviceId)
    upsertAll(contacts)
  }
}
