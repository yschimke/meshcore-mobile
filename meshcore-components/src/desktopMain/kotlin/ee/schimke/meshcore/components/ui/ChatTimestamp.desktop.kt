package ee.schimke.meshcore.components.ui

import kotlin.time.Instant

private val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
private val dateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm")

internal actual fun formatChatTimestamp(instant: Instant): String {
  val epochMs = instant.toEpochMilliseconds()
  if (epochMs == 0L) return ""
  val zdt = java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault())
  val now = java.time.LocalDate.now()
  return if (zdt.toLocalDate() == now) {
    zdt.format(timeFormatter)
  } else {
    zdt.format(dateTimeFormatter)
  }
}
