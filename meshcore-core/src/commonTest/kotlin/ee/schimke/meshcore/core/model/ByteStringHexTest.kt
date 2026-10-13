package ee.schimke.meshcore.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.io.bytestring.ByteString

class ByteStringHexTest {
  @Test
  fun hexRoundTripsBytes() {
    val bytes = ByteString(0x00, 0x1f, 0xab.toByte(), 0xff.toByte())
    assertEquals("001fabff", bytes.toHex())
    assertEquals(bytes, byteStringOfHex("001fabff"))
    assertEquals(bytes, byteStringOfHex("0x001FABFF"))
    assertEquals(ByteString(), byteStringOfHex(""))
    assertEquals("", ByteString().toHex())
  }

  @Test
  fun malformedHexIsRefused() {
    assertFailsWith<IllegalArgumentException> { byteStringOfHex("abc") }
    assertFailsWith<IllegalArgumentException> { byteStringOfHex("zz") }
  }
}
