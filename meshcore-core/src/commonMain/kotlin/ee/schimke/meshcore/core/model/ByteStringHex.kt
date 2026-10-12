package ee.schimke.meshcore.core.model

import kotlinx.io.bytestring.ByteString

/**
 * [hex] (two digits per byte, either case, an optional `0x` prefix) as bytes: the text spelling of
 * a [ByteString] such as [Contact.path], as [PublicKey.fromHex] is a key's. Malformed text throws
 * [IllegalArgumentException]; the empty string is the empty [ByteString].
 */
fun byteStringOfHex(hex: String): ByteString {
  val clean = hex.trim().removePrefix("0x")
  require(clean.length % 2 == 0) { "hex string must have even length" }
  return ByteString(
    *ByteArray(clean.length / 2) { i ->
      ((hexDigit(clean[i * 2]) shl 4) or hexDigit(clean[i * 2 + 1])).toByte()
    }
  )
}

/** These bytes as lowercase hex, two digits per byte; the inverse of [byteStringOfHex]. */
fun ByteString.toHex(): String =
  buildString(size * 2) {
    for (i in 0 until size) {
      val v = this@toHex[i].toInt() and 0xFF
      append(HEX_DIGITS[v ushr 4])
      append(HEX_DIGITS[v and 0x0F])
    }
  }

private const val HEX_DIGITS = "0123456789abcdef"

private fun hexDigit(c: Char): Int =
  when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    else -> throw IllegalArgumentException("invalid hex char: $c")
  }
