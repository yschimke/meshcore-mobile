package ee.schimke.meshcore.core.test

import ee.schimke.meshcore.core.protocol.ResponseCode
import kotlinx.io.Buffer
import kotlinx.io.bytestring.ByteString
import kotlinx.io.readByteString
import kotlinx.io.writeIntLe

/** Frame builders shared across core tests. */
object TestFrames {
  /**
   * Build a minimal `SelfInfo` response frame, the reply the device sends in response to `AppStart`
   * during the handshake. [publicKey] defaults to a fixed 32-byte filler.
   */
  fun selfInfoFrame(
    name: String = "test-node",
    publicKey: ByteArray = ByteArray(32) { 0xAB.toByte() },
  ): ByteString {
    require(publicKey.size == 32) { "public key must be 32 bytes" }
    val buf = Buffer()
    buf.writeByte(ResponseCode.SelfInfo.raw) // code
    buf.writeByte(1) // advType
    buf.writeByte(14) // txPower
    buf.writeByte(22) // maxPower
    buf.write(publicKey) // public key
    buf.writeIntLe(53_000_000) // lat * 1_000_000
    buf.writeIntLe(-1_500_000) // lon * 1_000_000
    buf.writeByte(0) // multiAcks
    buf.writeByte(0) // advertLocationPolicy
    buf.writeByte(0) // telemetryFlags
    buf.writeByte(0) // manualAddContacts
    buf.writeIntLe(869_525_000) // freq Hz
    buf.writeIntLe(125_000) // bandwidth Hz
    buf.writeByte(10) // spreading factor
    buf.writeByte(5) // coding rate
    buf.write(name.encodeToByteArray())
    buf.writeByte(0) // null terminator
    return buf.readByteString()
  }
}
