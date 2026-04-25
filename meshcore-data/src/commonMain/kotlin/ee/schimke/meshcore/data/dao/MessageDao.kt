package ee.schimke.meshcore.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ee.schimke.meshcore.data.entity.MessageEntity
import ee.schimke.meshcore.data.entity.MessageKind
import ee.schimke.meshcore.data.entity.MessageStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
  @Query(
    """
        SELECT * FROM message
        WHERE deviceId = :deviceId AND kind = 'DM' AND contactPublicKeyHex = :contactKeyHex
        ORDER BY timestampEpochMs ASC
    """
  )
  fun observeDms(deviceId: String, contactKeyHex: String): Flow<List<MessageEntity>>

  @Query(
    """
        SELECT * FROM message
        WHERE deviceId = :deviceId AND kind = 'CHANNEL' AND channelIndex = :channelIndex
        ORDER BY timestampEpochMs ASC
    """
  )
  fun observeChannelMessages(deviceId: String, channelIndex: Int): Flow<List<MessageEntity>>

  @Insert suspend fun insert(message: MessageEntity): Long

  @Query("UPDATE message SET status = :status WHERE ackHash = :ackHash AND direction = 'SENT'")
  suspend fun updateStatusByAckHash(ackHash: Int, status: MessageStatus)

  @Query("UPDATE message SET status = :status, ackHash = :ackHash WHERE rowId = :rowId")
  suspend fun updateStatusAndAckByRowId(rowId: Long, status: MessageStatus, ackHash: Int?)

  @Query("UPDATE message SET status = :status WHERE rowId = :rowId")
  suspend fun updateStatusByRowId(rowId: Long, status: MessageStatus)

  @Query(
    """
        SELECT * FROM message
        WHERE deviceId = :deviceId
        ORDER BY timestampEpochMs DESC
        LIMIT :limit
    """
  )
  suspend fun getRecentMessages(deviceId: String, limit: Int): List<MessageEntity>

  @Query(
    """
        SELECT * FROM message
        WHERE deviceId = :deviceId
        ORDER BY timestampEpochMs DESC
        LIMIT 1
    """
  )
  fun observeLatestMessage(deviceId: String): Flow<MessageEntity?>

  @Query(
    """
        SELECT DISTINCT contactPublicKeyHex FROM message
        WHERE deviceId = :deviceId AND kind = 'DM' AND contactPublicKeyHex IS NOT NULL
    """
  )
  fun observeContactedKeys(deviceId: String): Flow<List<String>>

  @Query(
    """
        SELECT DISTINCT channelIndex FROM message
        WHERE deviceId = :deviceId AND kind = 'CHANNEL' AND channelIndex IS NOT NULL
    """
  )
  fun observeContactedChannelIndices(deviceId: String): Flow<List<Int>>

  @Query(
    """
        SELECT COUNT(*) FROM message
        WHERE deviceId = :deviceId AND kind = :kind
          AND timestampEpochMs = :timestampMs AND text = :text
    """
  )
  suspend fun countDuplicates(
    deviceId: String,
    kind: MessageKind,
    timestampMs: Long,
    text: String,
  ): Int
}
