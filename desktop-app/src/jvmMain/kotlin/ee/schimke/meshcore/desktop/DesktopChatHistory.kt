package ee.schimke.meshcore.desktop

import ee.schimke.meshcore.components.ui.ChatDestination
import ee.schimke.meshcore.components.ui.ChatHistoryStore
import ee.schimke.meshcore.components.ui.ChatMessage
import ee.schimke.meshcore.components.ui.MessageStatus
import ee.schimke.meshcore.core.model.ReceivedChannelMessage
import ee.schimke.meshcore.core.model.ReceivedDirectMessage
import ee.schimke.meshcore.core.model.SelfInfo
import ee.schimke.meshcore.data.MeshcoreDatabase
import ee.schimke.meshcore.data.entity.MessageDirection
import ee.schimke.meshcore.data.entity.MessageEntity
import ee.schimke.meshcore.data.entity.MessageKind
import ee.schimke.meshcore.data.entity.MessageStatus as StoredStatus
import ee.schimke.meshcore.data.repository.MeshcoreRepository
import ee.schimke.meshcore.data.repository.SavedTransport
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A physical radio owns its history, even when its TCP address changes. */
internal class DesktopChatHistory(
  private val database: MeshcoreDatabase,
  private val self: SelfInfo,
  private val transport: SavedTransport.Tcp,
) : ChatHistoryStore {
  private val deviceId = "radio:${self.publicKey.toHex()}"
  private val mutex = Mutex()
  private var initialized = false
  private val messages = database.messageDao()
  private val received = mutableSetOf<MessageEntity>()

  private suspend fun initialize() {
    if (initialized) return
    val repository = MeshcoreRepository(database)
    repository.upsertDevice(deviceId, self.name, transport)
    repository.updateSelfInfo(deviceId, self, System.currentTimeMillis())
    messages.failInterruptedSends(deviceId)
    initialized = true
  }

  override fun messages(destination: ChatDestination): Flow<List<ChatMessage>> = flow {
    mutex.withLock { initialize() }
    val rows =
      when (destination) {
        is ChatDestination.Contact -> messages.observeDesktopDms(deviceId, destination.key)
        is ChatDestination.Channel -> messages.observeChannelMessages(deviceId, destination.index)
      }
    emitAll(rows.map { entries -> entries.map { it.toChatMessage(destination) } })
  }

  override suspend fun receive(message: ReceivedDirectMessage) {
    // Keep the wire prefix stable: receiving the same message before/after fetching contacts
    // must not create two records. The conversation query resolves prefixes to full keys.
    receive(
      MessageEntity(
        deviceId = deviceId,
        kind = MessageKind.DM,
        direction = MessageDirection.RECEIVED,
        contactPublicKeyHex = message.senderPrefix.toHex(),
        text = message.text,
        timestampEpochMs = message.timestamp.toEpochMilliseconds(),
        snr = message.snr,
        pathLength = message.pathLength,
        textType = message.textType.raw.toInt(),
      ),
    )
  }

  override suspend fun receive(message: ReceivedChannelMessage) {
    receive(
      MessageEntity(
        deviceId = deviceId,
        kind = MessageKind.CHANNEL,
        direction = MessageDirection.RECEIVED,
        channelIndex = message.channelIndex,
        senderName = message.sender,
        text = message.text,
        timestampEpochMs = message.timestamp.toEpochMilliseconds(),
        snr = message.snr,
        pathLength = message.pathLength,
        textType = message.textType.raw.toInt(),
      ),
    )
  }

  private suspend fun receive(message: MessageEntity) = mutex.withLock {
    initialize()
    if (message in received) return@withLock
    if (
      messages.countReceivedDuplicates(
        deviceId,
        message.kind,
        message.contactPublicKeyHex,
        message.channelIndex,
        message.senderName,
        message.timestampEpochMs,
        message.text,
      ) == 0
    ) {
      messages.insert(message)
    }
    received.add(message)
  }

  override suspend fun sending(destination: ChatDestination, message: ChatMessage): Long =
    mutex.withLock {
      initialize()
      messages.insert(
        MessageEntity(
          deviceId = deviceId,
          kind =
            if (destination is ChatDestination.Contact) MessageKind.DM else MessageKind.CHANNEL,
          direction = MessageDirection.SENT,
          contactPublicKeyHex = (destination as? ChatDestination.Contact)?.key,
          channelIndex = (destination as? ChatDestination.Channel)?.index,
          senderName = self.name,
          text = message.text,
          timestampEpochMs = message.timestamp.toEpochMilliseconds(),
          status = StoredStatus.SENDING,
        ),
      )
    }

  override suspend fun updateStatus(id: Long, status: MessageStatus) {
    messages.updateStatusByRowId(
      id,
      when (status) {
        MessageStatus.Sending -> StoredStatus.SENDING
        MessageStatus.Sent -> StoredStatus.SENT
        MessageStatus.Confirmed -> StoredStatus.CONFIRMED
        MessageStatus.Failed -> StoredStatus.FAILED
      },
    )
  }

  private fun MessageEntity.toChatMessage(destination: ChatDestination) =
    ChatMessage(
      id = "stored-$rowId",
      senderName = if (destination is ChatDestination.Contact) destination.name else senderName,
      text = text,
      timestamp = Instant.fromEpochMilliseconds(timestampEpochMs),
      snr = snr,
      isMine = direction == MessageDirection.SENT,
      status =
        when (status) {
          StoredStatus.SENDING -> MessageStatus.Sending
          StoredStatus.SENT -> MessageStatus.Sent
          StoredStatus.CONFIRMED -> MessageStatus.Confirmed
          StoredStatus.FAILED -> MessageStatus.Failed
        },
    )
}
