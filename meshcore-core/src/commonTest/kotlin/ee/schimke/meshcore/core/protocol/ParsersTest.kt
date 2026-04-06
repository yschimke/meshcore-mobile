package ee.schimke.meshcore.core.protocol

import ee.schimke.meshcore.core.model.MeshEvent
import kotlinx.io.bytestring.ByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParsersTest {

    @Test
    fun parse_ok() {
        val ev = Parsers.parse(ByteString(ResponseCode.Ok.raw))
        assertTrue(ev is MeshEvent.Ok)
    }

    @Test
    fun parse_err_withCode() {
        val ev = Parsers.parse(ByteString(ResponseCode.Err.raw, 2))
        assertTrue(ev is MeshEvent.Err)
        assertEquals(2, ev.code)
    }

    @Test
    fun parse_batteryAndStorage() {
        val bytes = ByteArray(1 + 2 + 4 + 4)
        bytes[0] = ResponseCode.BatteryAndStorage.raw
        bytes[1] = (3800 and 0xFF).toByte()
        bytes[2] = ((3800 shr 8) and 0xFF).toByte()
        bytes[3] = 0; bytes[4] = 2; bytes[5] = 0; bytes[6] = 0
        bytes[7] = 0; bytes[8] = 16; bytes[9] = 0; bytes[10] = 0
        val ev = Parsers.parse(ByteString(*bytes)) as MeshEvent.Battery
        assertEquals(3800, ev.info.millivolts)
        assertEquals(512, ev.info.storageUsedKb)
        assertEquals(4096, ev.info.storageTotalKb)
    }

    @Test
    fun parse_radioSettings() {
        val bytes = ByteArray(1 + 4 + 4 + 1 + 1)
        bytes[0] = ResponseCode.RadioSettings.raw
        val freq = 915_000_000
        bytes[1] = (freq and 0xFF).toByte()
        bytes[2] = ((freq ushr 8) and 0xFF).toByte()
        bytes[3] = ((freq ushr 16) and 0xFF).toByte()
        bytes[4] = ((freq ushr 24) and 0xFF).toByte()
        val bw = 125_000
        bytes[5] = (bw and 0xFF).toByte()
        bytes[6] = ((bw ushr 8) and 0xFF).toByte()
        bytes[7] = ((bw ushr 16) and 0xFF).toByte()
        bytes[8] = ((bw ushr 24) and 0xFF).toByte()
        bytes[9] = 7
        bytes[10] = 5
        val ev = Parsers.parse(ByteString(*bytes)) as MeshEvent.Radio
        assertEquals(915_000_000, ev.settings.frequencyHz)
        assertEquals(125_000, ev.settings.bandwidthHz)
        assertEquals(7, ev.settings.spreadingFactor)
        assertEquals(5, ev.settings.codingRate)
    }

    @Test
    fun parse_unknownBecomesRaw() {
        val ev = Parsers.parse(ByteString(0x7F, 1, 2, 3))
        assertTrue(ev is MeshEvent.Raw)
        assertEquals(0x7F.toByte(), ev.code)
        assertEquals(3, ev.body.size)
    }
}
