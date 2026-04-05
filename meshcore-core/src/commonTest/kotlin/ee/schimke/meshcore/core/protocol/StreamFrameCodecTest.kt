package ee.schimke.meshcore.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
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
        val bytes = byteArrayOf(
            MeshCoreConstants.STREAM_RX_START,
            payload.size.toByte(), 0,
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
        val full = byteArrayOf(
            MeshCoreConstants.STREAM_RX_START,
            payload.size.toByte(), 0,
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
        val bytes = byteArrayOf(
            0x00, 0x11, 0x22, // junk
            MeshCoreConstants.STREAM_RX_START,
            payload.size.toByte(), 0,
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
}
