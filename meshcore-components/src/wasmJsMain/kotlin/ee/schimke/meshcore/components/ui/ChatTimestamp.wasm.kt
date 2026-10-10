@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package ee.schimke.meshcore.components.ui

import kotlin.time.Instant

internal actual fun formatChatTimestamp(instant: Instant): String =
  if (instant.toEpochMilliseconds() == 0L) ""
  else localTime(instant.toEpochMilliseconds().toDouble())

private fun localTime(epochMs: Double): String =
  js(
    """(() => {
    const date = new Date(epochMs), now = new Date();
    const pad = n => String(n).padStart(2, '0');
    const time = pad(date.getHours()) + ':' + pad(date.getMinutes());
    return date.toDateString() === now.toDateString() ? time :
      pad(date.getDate()) + '/' + pad(date.getMonth() + 1) + ' ' + time;
  })()"""
  )
