package ee.schimke.meshcore.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.io.bytestring.ByteString

class StreamFrameCodecTest {

  @Test
  fun encodeTx_prependsHeader() {
    val payload = ByteString(0x01, 0x02, 0x03)
    val framed = StreamFrameCodec.encodeTx(payload).toByteArray()
    assertEquals(6, framed.size)
    assertEquals(MeshCoreConstants.STREAM_TX_START, framed[0])
    assertEquals(3, framed[1].toInt())
    assertEquals(0, framed[2].toInt())
    assertTrue(payload.toByteArray().contentEquals(framed.copyOfRange(3, 6)))
  }

  @Test
  fun decoder_singleRxFrame() {
    val payload = ByteString(0x05, 0x10, 0x20, 0x30)
    val bytes =
      byteArrayOf(
        MeshCoreConstants.STREAM_RX_START,
        payload.size.toByte(),
        0,
        *payload.toByteArray(),
      )
    val packets = StreamFrameCodec.Decoder().ingest(bytes)
    assertEquals(1, packets.size)
    assertTrue(packets[0].isRx)
    assertEquals(payload, packets[0].payload)
  }

  @Test
  fun decoder_splitAcrossIngests() {
    val payload = ByteString(*ByteArray(10) { it.toByte() })
    val full =
      byteArrayOf(
        MeshCoreConstants.STREAM_RX_START,
        payload.size.toByte(),
        0,
        *payload.toByteArray(),
      )
    val decoder = StreamFrameCodec.Decoder()
    assertEquals(0, decoder.ingest(full.copyOfRange(0, 4)).size)
    val pkts = decoder.ingest(full.copyOfRange(4, full.size))
    assertEquals(1, pkts.size)
    assertEquals(payload, pkts[0].payload)
  }

  @Test
  fun decoder_resyncsOnJunk() {
    val payload = ByteString(0x05, 0x06)
    val bytes =
      byteArrayOf(
        0x00,
        0x11,
        0x22, // junk
        MeshCoreConstants.STREAM_RX_START,
        payload.size.toByte(),
        0,
        *payload.toByteArray(),
      )
    val pkts = StreamFrameCodec.Decoder().ingest(bytes)
    assertEquals(1, pkts.size)
    assertEquals(payload, pkts[0].payload)
  }

  @Test
  fun decoder_multipleFramesInOneIngest() {
    fun frame(p: ByteArray): ByteArray =
      byteArrayOf(MeshCoreConstants.STREAM_RX_START, p.size.toByte(), 0, *p)
    val a = byteArrayOf(0x01, 0x02)
    val b = byteArrayOf(0x0A, 0x0B, 0x0C)
    val pkts = StreamFrameCodec.Decoder().ingest(frame(a) + frame(b))
    assertEquals(2, pkts.size)
    assertEquals(ByteString(*a), pkts[0].payload)
    assertEquals(ByteString(*b), pkts[1].payload)
  }

  @Test
  fun decoder_oversizedFrame_isDropped() {
    // Build a frame whose length field exceeds MAX_FRAME_SIZE.
    // The decoder should drop the start byte and resync.
    val oversizedLen = MeshCoreConstants.MAX_FRAME_SIZE + 1
    val header =
      byteArrayOf(
        MeshCoreConstants.STREAM_RX_START,
        (oversizedLen and 0xFF).toByte(),
        ((oversizedLen shr 8) and 0xFF).toByte(),
      )
    // Follow with a valid frame so we can verify resync worked
    val validPayload = ByteString(0xAA.toByte())
    val valid =
      byteArrayOf(
        MeshCoreConstants.STREAM_RX_START,
        validPayload.size.toByte(),
        0,
        *validPayload.toByteArray(),
      )
    val pkts = StreamFrameCodec.Decoder().ingest(header + ByteArray(oversizedLen) + valid)
    assertEquals(1, pkts.size)
    assertEquals(validPayload, pkts[0].payload)
  }

  @Test
  fun decoder_zeroLengthFrame() {
    val bytes =
      byteArrayOf(
        MeshCoreConstants.STREAM_RX_START,
        0,
        0, // zero-length payload
      )
    val pkts = StreamFrameCodec.Decoder().ingest(bytes)
    assertEquals(1, pkts.size)
    assertEquals(ByteString(), pkts[0].payload)
  }

  @Test
  fun decoder_partialHeaderOnly_returnsEmpty() {
    val decoder = StreamFrameCodec.Decoder()
    // Only 2 of the 3 header bytes — decoder should buffer and return nothing
    val pkts = decoder.ingest(byteArrayOf(MeshCoreConstants.STREAM_RX_START, 0x05))
    assertEquals(0, pkts.size)
  }

  @Test
  fun decoder_interleavedJunkBetweenValidFrames() {
    fun frame(vararg payload: Byte): ByteArray =
      byteArrayOf(MeshCoreConstants.STREAM_RX_START, payload.size.toByte(), 0, *payload)
    val input = frame(0x01) + byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + frame(0x02)
    val pkts = StreamFrameCodec.Decoder().ingest(input)
    assertEquals(2, pkts.size)
    assertEquals(ByteString(0x01), pkts[0].payload)
    assertEquals(ByteString(0x02), pkts[1].payload)
  }

  @Test
  fun encodeTx_maxSizeBoundary() {
    val maxPayload = ByteString(*ByteArray(MeshCoreConstants.MAX_FRAME_SIZE) { 0x42 })
    val framed = StreamFrameCodec.encodeTx(maxPayload)
    assertEquals(MeshCoreConstants.MAX_FRAME_SIZE + 3, framed.size)

    // One byte over should throw
    val oversize = ByteString(*ByteArray(MeshCoreConstants.MAX_FRAME_SIZE + 1) { 0x42 })
    assertFailsWith<IllegalArgumentException> { StreamFrameCodec.encodeTx(oversize) }
  }

  @Test
  fun decoder_reset_clearsBufferedState() {
    val decoder = StreamFrameCodec.Decoder()
    // Feed a partial frame
    decoder.ingest(byteArrayOf(MeshCoreConstants.STREAM_RX_START, 0x05, 0x00))
    decoder.reset()
    // Now feed a complete, different frame — should decode cleanly
    val payload = ByteString(0xBB.toByte())
    val pkts =
      decoder.ingest(
        byteArrayOf(
          MeshCoreConstants.STREAM_RX_START,
          payload.size.toByte(),
          0,
          *payload.toByteArray(),
        )
      )
    assertEquals(1, pkts.size)
    assertEquals(payload, pkts[0].payload)
  }
}
