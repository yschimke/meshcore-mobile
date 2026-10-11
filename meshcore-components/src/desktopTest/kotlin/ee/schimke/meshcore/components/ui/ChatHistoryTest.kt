package ee.schimke.meshcore.components.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ChatHistoryTest {
  private val sentAt = Instant.fromEpochMilliseconds(1_700_000_000_250)
  private val local =
    ChatMessage("sent-1", null, "hello", sentAt, isMine = true, status = MessageStatus.Sent)

  private fun echo(
    text: String = "hello",
    sender: String? = "me",
    at: Instant = Instant.fromEpochSeconds(sentAt.epochSeconds),
  ) = ChatMessage("channel-0-$at", sender, text, at, snr = 4, isMine = false)

  @Test
  fun echoedChannelSendRendersOnce() {
    val merged = mergeChatHistory(listOf(echo()), listOf(local), "me", dedupeEchoes = true)
    assertEquals(listOf(local), merged)
  }

  @Test
  fun echoMatchesWithoutKnownSelfName() {
    val merged = mergeChatHistory(listOf(echo()), listOf(local), null, dedupeEchoes = true)
    assertEquals(listOf(local), merged)
  }

  @Test
  fun sameTextFromAnotherSenderIsKept() {
    val other = echo(sender = "bob")
    val merged = mergeChatHistory(listOf(other), listOf(local), "me", dedupeEchoes = true)
    assertEquals(listOf(other, local), merged)
  }

  @Test
  fun differentSecondOrTextIsKept() {
    val later = echo(at = Instant.fromEpochSeconds(sentAt.epochSeconds + 1))
    val otherText = echo(text = "bye")
    val merged =
      mergeChatHistory(listOf(later, otherText), listOf(local), "me", dedupeEchoes = true)
    assertEquals(3, merged.size)
  }

  @Test
  fun eachSendAbsorbsAtMostOneEcho() {
    val merged = mergeChatHistory(listOf(echo(), echo()), listOf(local), "me", dedupeEchoes = true)
    assertEquals(2, merged.size)
  }

  @Test
  fun directMessagesAreNotDeduplicated() {
    val merged = mergeChatHistory(listOf(echo()), listOf(local), "me", dedupeEchoes = false)
    assertEquals(2, merged.size)
  }
}
