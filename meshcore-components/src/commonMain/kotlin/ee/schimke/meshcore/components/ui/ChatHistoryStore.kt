package ee.schimke.meshcore.components.ui

import ee.schimke.meshcore.core.model.ReceivedChannelMessage
import ee.schimke.meshcore.core.model.ReceivedDirectMessage
import kotlinx.coroutines.flow.Flow

/** Optional host-owned history. Browser sessions can continue to use in-memory messages. */
interface ChatHistoryStore {
  fun messages(destination: ChatDestination): Flow<List<ChatMessage>>

  suspend fun receive(message: ReceivedDirectMessage)

  suspend fun receive(message: ReceivedChannelMessage)

  /** Store before sending, so an interrupted send can be shown as failed on the next launch. */
  suspend fun sending(destination: ChatDestination, message: ChatMessage): Long

  suspend fun updateStatus(id: Long, status: MessageStatus)
}
