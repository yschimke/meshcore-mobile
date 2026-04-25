package ee.schimke.meshcore.core.protocol

/**
 * Type of a text-message payload. The MeshCore protocol encodes this as a single byte in both
 * outbound `CMD_SEND_TXT_MSG` frames and inbound message-receive responses.
 */
enum class TextType(val raw: Byte) {
  Plain(0x00),
  CliData(0x01);

  companion object {
    private val byRaw = entries.associateBy { it.raw }

    /**
     * Resolve [raw] to a known [TextType], or null if the protocol byte is not recognised. Callers
     * decide whether to treat an unknown type as a parse error or a raw fallback.
     */
    fun fromRaw(raw: Int): TextType? = byRaw[(raw and 0xFF).toByte()]
  }
}
