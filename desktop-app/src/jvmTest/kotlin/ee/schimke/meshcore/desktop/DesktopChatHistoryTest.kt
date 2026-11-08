package ee.schimke.meshcore.desktop

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import ee.schimke.meshcore.components.ui.ChatDestination
import ee.schimke.meshcore.components.ui.ChatMessage
import ee.schimke.meshcore.components.ui.MessageStatus
import ee.schimke.meshcore.core.model.PublicKey
import ee.schimke.meshcore.core.model.RadioSettings
import ee.schimke.meshcore.core.model.ReceivedChannelMessage
import ee.schimke.meshcore.core.model.ReceivedDirectMessage
import ee.schimke.meshcore.core.model.SelfInfo
import ee.schimke.meshcore.core.protocol.TextType
import ee.schimke.meshcore.data.MeshcoreDatabase
import ee.schimke.meshcore.data.repository.SavedTransport
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString

class DesktopChatHistoryTest {
  private val at = Instant.fromEpochSeconds(1_700_000_000)
  private val channel = ChatDestination.Channel(0, "General")

  private fun key(fill: Int) = PublicKey.fromBytes(ByteString(*ByteArray(32) { fill.toByte() }))

  private fun self(fill: Int = 1) =
    SelfInfo(
      1,
      14,
      22,
      key(fill),
      0.0,
      0.0,
      0,
      0,
      0,
      0,
      RadioSettings(869525000, 125000, 10, 5),
      "My radio",
    )

  private fun history(db: MeshcoreDatabase, radio: Int = 1, host: String = "radio.local") =
    DesktopChatHistory(db, self(radio), SavedTransport.Tcp(host, 5000))

  private fun received(index: Int = 0, sender: String = "Alice") =
    ReceivedChannelMessage(4, index, 0, at, TextType.Plain, "$sender: hello")

  @Test
  fun historySurvivesDatabaseReopenAndAddressChange() = runTest {
    val directory = Files.createTempDirectory("meshcore-history").toFile()
    fun open() =
      Room.databaseBuilder<MeshcoreDatabase>(directory.resolve("history.db").path)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
    try {
      val first = open()
      try {
        val store = history(first)
        store.receive(received())
        store.receive(received())
        val id = store.sending(channel, ChatMessage("local", null, "reply", at, isMine = true))
        store.updateStatus(id, MessageStatus.Sent)
      } finally {
        first.close()
      }
      val reopened = open()
      try {
        val saved = history(reopened, host = "new-address.local").messages(channel).first()
        assertEquals(listOf("hello", "reply"), saved.map { it.text })
        assertEquals(MessageStatus.Sent, saved.last().status)
        assertEquals(emptyList(), history(reopened, radio = 2).messages(channel).first())
      } finally {
        reopened.close()
      }
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun deduplicationPreservesOtherChannelsAndSendersAndContacts() = runTest {
    val db =
      Room.inMemoryDatabaseBuilder<MeshcoreDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
    try {
      val store = history(db)
      store.receive(received())
      store.receive(received(sender = "Bob"))
      store.receive(received(index = 1))
      assertEquals(2, store.messages(channel).first().size)
      assertEquals(1, store.messages(ChatDestination.Channel(1, "Other")).first().size)
      for (sender in listOf(3, 4)) {
        val prefix = PublicKey.fromBytes(ByteString(*ByteArray(6) { sender.toByte() }))
        store.receive(ReceivedDirectMessage(4, prefix, 0, at, TextType.Plain, "hello"))
      }
      assertEquals(1, store.messages(ChatDestination.Contact(key(3).toHex(), "Alice")).first().size)
      assertEquals(1, store.messages(ChatDestination.Contact(key(4).toHex(), "Bob")).first().size)
    } finally {
      db.close()
    }
  }

  @Test
  fun interruptedSendIsFailedOnNextSession() = runTest {
    val db =
      Room.inMemoryDatabaseBuilder<MeshcoreDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
    try {
      history(db).sending(channel, ChatMessage("local", null, "reply", at, isMine = true))
      assertEquals(MessageStatus.Failed, history(db).messages(channel).first().single().status)
    } finally {
      db.close()
    }
  }
}
