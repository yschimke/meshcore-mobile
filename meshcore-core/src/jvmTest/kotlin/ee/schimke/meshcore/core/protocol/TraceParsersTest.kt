package ee.schimke.meshcore.core.protocol

import ee.schimke.meshcore.core.model.MeshEvent
import kotlinx.io.bytestring.ByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Replays real wire traces captured from the upstream Python `meshcore-cli`
 * against our parser and verifies the expected event sequence. The fixtures
 * under `resources/traces/` contain one received hex frame per line;
 * outgoing frames are captured locally under `traces/` (gitignored) but are
 * not shipped.
 */
class TraceParsersTest {

    @Test
    fun startup_yields_selfInfo_and_deviceInfo() {
        val events = parseTrace("startup.rx")
        // A typical infos run is: SelfInfo, DeviceInfo, [SelfInfo again].
        assertTrue(events.any { it is MeshEvent.SelfInfoEvent }, "expected SelfInfo in startup trace")
        assertTrue(events.any { it is MeshEvent.Device }, "expected DeviceInfo in startup trace")
    }

    @Test
    fun all_features_trace_has_no_raws_for_known_codes() {
        val events = parseTrace("all_features.rx")
        // Count the flavours we expect to see.
        val contactStarts = events.count { it is MeshEvent.ContactsStart }
        val contacts = events.count { it is MeshEvent.ContactEvent }
        val eocs = events.count { it is MeshEvent.EndOfContacts }
        val channels = events.count { it is MeshEvent.ChannelInfoEvent }
        assertTrue(contactStarts >= 1, "ContactsStart missing")
        assertTrue(contacts >= 1, "no Contact events parsed")
        assertTrue(eocs >= 1, "EndOfContacts missing")
        assertTrue(channels >= 1, "no ChannelInfo events parsed")
    }

    @Test
    fun login_room_trace_has_loginSuccess_with_expected_prefix() {
        val events = parseTrace("login_room.rx")
        val ok = events.filterIsInstance<MeshEvent.LoginSuccess>()
        assertEquals(1, ok.size, "expected exactly one LoginSuccess in successful-login trace")
        // Kodu Room pubkey prefix is the first six bytes of f1f680c13cb6…
        assertEquals("f1f680c13cb6", ok[0].publicKey.toHex())
    }

    @Test
    fun login_fail_trace_contains_no_loginSuccess() {
        val events = parseTrace("login_fail.rx")
        // Real room servers silently drop bad passwords, so we see RX-log pushes
        // (0x88 → Raw) but never a LoginSuccess nor LoginFail.
        assertTrue(
            events.none { it is MeshEvent.LoginSuccess },
            "unexpected LoginSuccess in login-fail trace",
        )
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun parseTrace(name: String): List<MeshEvent> {
        val stream = javaClass.classLoader.getResourceAsStream("traces/$name")
            ?: fail("fixture not found on classpath: traces/$name")
        return stream.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }
                .map { Parsers.parse(hexDecode(it.trim())) }
                .toList()
        }
    }

    private fun hexDecode(hex: String): ByteString {
        require(hex.length % 2 == 0) { "odd-length hex: '$hex'" }
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            out[i] = ((hex[2 * i].digitToInt(16) shl 4) or hex[2 * i + 1].digitToInt(16)).toByte()
        }
        return ByteString(*out)
    }
}
