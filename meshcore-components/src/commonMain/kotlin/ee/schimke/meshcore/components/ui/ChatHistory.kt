package ee.schimke.meshcore.components.ui

/**
 * Merges messages received from the device with this session's optimistic outbound messages into
 * one timeline.
 *
 * A companion radio echoes a channel message it sent back as an ordinary received channel message
 * (see `MeshcoreFunctions.sendChannelMessage`, which relies on that). Without reconciliation the
 * same send renders twice: once as the local optimistic bubble and once as the echo.
 *
 * An echo is matched to a local send on the fields the wire round-trips unchanged: the channel is
 * implied by the per-conversation [sent] list, the text must be equal, and the timestamp must be in
 * the same epoch second (`Frames.sendChannelTextMessage` writes `epochSeconds`, so the echo carries
 * the send's timestamp truncated to the second). When [selfName] is known, the echo's sender must
 * also be this device. Each local send absorbs at most one echo; the local entry is kept because it
 * carries the delivery status.
 *
 * Only pass [dedupeEchoes] for channel conversations: direct messages are not echoed.
 */
internal fun mergeChatHistory(
  received: List<ChatMessage>,
  sent: List<ChatMessage>,
  selfName: String?,
  dedupeEchoes: Boolean,
): List<ChatMessage> {
  if (!dedupeEchoes || sent.isEmpty()) return (received + sent).sortedBy { it.timestamp }
  val unmatched = sent.filter { it.isMine }.toMutableList()
  val kept = received.filter { message ->
    if (selfName != null && message.senderName != selfName) return@filter true
    val echoed = unmatched.indexOfFirst {
      it.text == message.text && it.timestamp.epochSeconds == message.timestamp.epochSeconds
    }
    if (echoed >= 0) {
      unmatched.removeAt(echoed)
      false
    } else true
  }
  return (kept + sent).sortedBy { it.timestamp }
}
