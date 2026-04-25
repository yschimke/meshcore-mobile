package ee.schimke.meshcore.core.protocol

import ee.schimke.meshcore.core.protocol.BufferExt.buildByteString
import ee.schimke.meshcore.core.protocol.BufferExt.readU16Le
import ee.schimke.meshcore.core.protocol.MeshCoreConstants.MAX_FRAME_SIZE
import ee.schimke.meshcore.core.protocol.MeshCoreConstants.STREAM_HEADER_LEN
import ee.schimke.meshcore.core.protocol.MeshCoreConstants.STREAM_RX_START
import ee.schimke.meshcore.core.protocol.MeshCoreConstants.STREAM_TX_START
import kotlinx.io.Buffer
import kotlinx.io.bytestring.ByteString
import kotlinx.io.readByteString
import kotlinx.io.writeShortLe

/**
 * Length-prefixed framing used by the USB CDC-ACM and TCP companion transports. The BLE transport
 * does NOT use this – each BLE write / notification already delivers one complete frame payload.
 *
 * Frame layout on the wire (either direction): [start:u8][len_lo:u8][len_hi:u8][payload…]
 *
 * `start` is [STREAM_TX_START] (0x3C) for App → Device and [STREAM_RX_START] (0x3E) for Device →
 * App.
 */
object StreamFrameCodec {

  /** Wrap an outbound payload with the TX stream header. */
  fun encodeTx(payload: ByteString): ByteString {
    require(payload.size <= MAX_FRAME_SIZE) {
      "payload exceeds $MAX_FRAME_SIZE bytes (${payload.size})"
    }
    return buildByteString {
      writeByte(STREAM_TX_START)
      writeShortLe(payload.size.toShort())
      write(payload.toByteArray())
    }
  }

  /**
   * Stateful decoder: callers feed in bytes as they arrive from a stream transport, and get back
   * zero or more fully-decoded payloads. Resyncs silently on stray bytes or oversized lengths.
   * Internally buffers partial frames in a [Buffer].
   */
  class Decoder {
    private val buf = Buffer()

    fun reset() {
      buf.clear()
    }

    fun ingest(bytes: ByteString): List<DecodedPacket> {
      if (bytes.size == 0) return emptyList()
      buf.write(bytes.toByteArray())
      return drain()
    }

    fun ingest(bytes: ByteArray): List<DecodedPacket> {
      if (bytes.isEmpty()) return emptyList()
      buf.write(bytes)
      return drain()
    }

    private fun drain(): List<DecodedPacket> {
      val out = mutableListOf<DecodedPacket>()
      while (true) {
        if (buf.size == 0L) return out
        // Peek the header without consuming.
        val peek = buf.peek()
        val head = peek.readByte()
        if (head != STREAM_RX_START && head != STREAM_TX_START) {
          // Resync: drop a single byte and retry.
          buf.readByte()
          continue
        }
        if (buf.size < STREAM_HEADER_LEN.toLong()) return out
        val lenPeek = buf.peek()
        lenPeek.readByte() // skip start
        val payloadLen = lenPeek.readU16Le()
        if (payloadLen > MAX_FRAME_SIZE) {
          // Bogus length: drop start byte and resync.
          buf.readByte()
          continue
        }
        val total = STREAM_HEADER_LEN.toLong() + payloadLen.toLong()
        if (buf.size < total) return out
        // Consume one complete frame.
        val start = buf.readByte()
        buf.readShort() // length already validated
        val payload = buf.readByteString(payloadLen)
        out += DecodedPacket(start, payload)
      }
    }
  }

  data class DecodedPacket(val start: Byte, val payload: ByteString) {
    val isRx: Boolean
      get() = start == STREAM_RX_START
  }
}
