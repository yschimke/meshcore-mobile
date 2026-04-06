package ee.schimke.meshcore.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import ee.schimke.meshcore.data.entity.DeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM device ORDER BY isFavorite DESC, lastConnectedAtMs DESC")
    fun observeAll(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM device WHERE isFavorite = 1 LIMIT 1")
    fun observeFavorite(): Flow<DeviceEntity?>

    @Query("SELECT * FROM device WHERE id = :id")
    suspend fun getById(id: String): DeviceEntity?

    @Upsert
    suspend fun upsert(device: DeviceEntity)

    @Query("DELETE FROM device WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE device SET isFavorite = 0")
    suspend fun clearAllFavorites()

    @Query("UPDATE device SET isFavorite = 1 WHERE id = :id")
    suspend fun setFavorite(id: String)

    @Transaction
    suspend fun toggleFavorite(id: String) {
        val current = getById(id) ?: return
        clearAllFavorites()
        if (!current.isFavorite) setFavorite(id)
    }
}
