package ee.schimke.meshcore.core.model

import ee.schimke.meshcore.core.model.PublicKey.Companion.fromBytes
import ee.schimke.meshcore.core.model.PublicKey.Companion.fromHex
import ee.schimke.meshcore.core.model.PublicKey.Companion.ofPrefix
import ee.schimke.meshcore.core.protocol.MeshCoreConstants.PUB_KEY_PREFIX_SIZE
import ee.schimke.meshcore.core.protocol.MeshCoreConstants.PUB_KEY_SIZE
import kotlinx.io.bytestring.ByteString

/**
 * MeshCore identity as a strongly-typed wrapper around a 32-byte Ed25519 public key. Uses an inline
 * value class so there's no runtime overhead over the underlying [ByteString].
 *
 * Construct via [fromBytes], [fromHex] or [ofPrefix]. MeshCore packets routinely identify contacts
 * by just the first 6 bytes ([prefix]).
 */
@JvmInline
value class PublicKey private constructor(val bytes: ByteString) {

  init {
    require(bytes.size == PUB_KEY_SIZE || bytes.size == PUB_KEY_PREFIX_SIZE) {
      "PublicKey must be $PUB_KEY_SIZE bytes (full) or $PUB_KEY_PREFIX_SIZE bytes (prefix)"
    }
  }

  /** First 6 bytes – MeshCore uses this to identify routing targets. */
  val prefix: ByteString
    get() =
      if (bytes.size >= PUB_KEY_PREFIX_SIZE) {
        bytes.substring(0, PUB_KEY_PREFIX_SIZE)
      } else {
        bytes
      }

  val isPrefixOnly: Boolean
    get() = bytes.size == PUB_KEY_PREFIX_SIZE

  fun toHex(): String {
    val arr = bytes.toByteArray()
    val sb = StringBuilder(arr.size * 2)
    for (b in arr) {
      val v = b.toInt() and 0xFF
      sb.append(HEX[v ushr 4])
      sb.append(HEX[v and 0x0F])
    }
    return sb.toString()
  }

  override fun toString(): String = toHex()

  companion object {
    private val HEX = "0123456789abcdef".toCharArray()

    fun fromBytes(bytes: ByteString): PublicKey = PublicKey(bytes)

    fun fromBytes(bytes: ByteArray): PublicKey = PublicKey(ByteString(*bytes))

    fun ofPrefix(prefix: ByteString): PublicKey {
      require(prefix.size == PUB_KEY_PREFIX_SIZE) { "prefix must be $PUB_KEY_PREFIX_SIZE bytes" }
      return PublicKey(prefix)
    }

    fun fromHex(hex: String): PublicKey {
      val clean = hex.trim().removePrefix("0x")
      require(clean.length % 2 == 0) { "hex string must have even length" }
      val out = ByteArray(clean.length / 2)
      for (i in out.indices) {
        val hi = hexDigit(clean[i * 2])
        val lo = hexDigit(clean[i * 2 + 1])
        out[i] = ((hi shl 4) or lo).toByte()
      }
      return fromBytes(out)
    }

    private fun hexDigit(c: Char): Int =
      when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> throw IllegalArgumentException("invalid hex char: $c")
      }
  }
}
