package ee.schimke.meshcore.core.protocol

import ee.schimke.meshcore.core.model.PublicKey
import ee.schimke.meshcore.core.protocol.BufferExt.buildByteString
import ee.schimke.meshcore.core.protocol.BufferExt.writeCString
import ee.schimke.meshcore.core.protocol.MeshCoreConstants.MAX_NAME_SIZE
import ee.schimke.meshcore.core.protocol.MeshCoreConstants.MAX_TEXT_PAYLOAD_BYTES
import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.io.writeIntLe

/**
 * Pure frame builders. They return the raw MeshCore frame payload `[code][body]` ready to hand to a
 * [ee.schimke.meshcore.core.transport.Transport]. Stream transports will wrap this further via
 * [StreamFrameCodec.encodeTx].
 */
object Frames {

  fun appStart(
    appName: String,
    appVersion: Int = MeshCoreConstants.APP_PROTOCOL_VERSION,
  ): ByteString = buildByteString {
    writeByte(CommandCode.AppStart.raw)
    writeByte(appVersion.toByte())
    repeat(6) { writeByte(0) } // reserved
    writeCString(appName)
  }

  /**
   * `[0x1A][recipient pub_key × 32][password]\0` — authenticate to a specific contact (repeater or
   * room) so subsequent admin commands addressed to that contact are honored. This is **not** a
   * device-level bootstrap step — connecting to a companion radio over BLE/USB/TCP requires no
   * login. Only call this when the user wants to unlock a password-protected contact.
   *
   * Verified against the MeshCore Flutter client's `buildSendLoginFrame` in
   * `connector/meshcore_protocol.dart`.
   */
  fun sendLogin(recipient: PublicKey, password: String): ByteString = buildByteString {
    writeByte(CommandCode.SendLogin.raw)
    write(recipient.bytes.toByteArray())
    writeCString(password)
  }

  fun deviceQuery(): ByteString = single(CommandCode.DeviceQuery)

  fun getBatteryAndStorage(): ByteString = single(CommandCode.GetBatteryAndStorage)

  fun getRadioSettings(): ByteString = single(CommandCode.GetRadioSettings)

  fun getDeviceTime(): ByteString = single(CommandCode.GetDeviceTime)

  fun setDeviceTime(time: Instant): ByteString = buildByteString {
    writeByte(CommandCode.SetDeviceTime.raw)
    writeIntLe(time.epochSeconds.toInt())
  }

  fun getContacts(since: Instant? = null): ByteString = buildByteString {
    writeByte(CommandCode.GetContacts.raw)
    if (since != null) writeIntLe(since.epochSeconds.toInt())
  }

  fun sendSelfAdvert(floodMode: Boolean = false): ByteString = buildByteString {
    writeByte(CommandCode.SendSelfAdvert.raw)
    writeByte(if (floodMode) 1 else 0)
  }

  fun setAdvertName(name: String): ByteString {
    val nameBytes = name.encodeToByteArray()
    val trimmed =
      if (nameBytes.size > MAX_NAME_SIZE - 1) {
        nameBytes.copyOf(MAX_NAME_SIZE - 1)
      } else {
        nameBytes
      }
    return buildByteString {
      writeByte(CommandCode.SetAdvertName.raw)
      write(trimmed)
    }
  }

  fun setAdvertLatLon(lat: Double, lon: Double): ByteString = buildByteString {
    writeByte(CommandCode.SetAdvertLatLon.raw)
    writeIntLe((lat * 1_000_000.0).roundToInt())
    writeIntLe((lon * 1_000_000.0).roundToInt())
  }

  fun setRadioParams(freqHz: Int, bwHz: Int, sf: Int, cr: Int): ByteString = buildByteString {
    writeByte(CommandCode.SetRadioParams.raw)
    writeIntLe(freqHz)
    writeIntLe(bwHz)
    writeByte(sf.toByte())
    writeByte(cr.toByte())
  }

  fun setRadioTxPower(dbm: Int): ByteString = buildByteString {
    writeByte(CommandCode.SetRadioTxPower.raw)
    writeByte(dbm.toByte())
  }

  /** `[0x1F][channel_idx]` — request info for a single channel. */
  fun getChannel(index: Int): ByteString = buildByteString {
    writeByte(CommandCode.GetChannel.raw)
    writeByte(index.toByte())
  }

  /** `[0x20][channel_idx][name×32][psk×16]` — create or update a channel. */
  fun setChannel(index: Int, name: String, psk: ByteString): ByteString = buildByteString {
    writeByte(CommandCode.SetChannel.raw)
    writeByte(index.toByte())
    // Fixed 32-byte name field, null-padded
    val nameBytes = name.encodeToByteArray()
    val nameField = ByteArray(32)
    nameBytes.copyInto(nameField, endIndex = minOf(nameBytes.size, 31))
    write(nameField)
    // 16-byte PSK
    val pskBytes = psk.toByteArray()
    val pskField = ByteArray(16)
    pskBytes.copyInto(pskField, endIndex = minOf(pskBytes.size, 16))
    write(pskField)
  }

  fun reboot(): ByteString = single(CommandCode.Reboot)

  fun syncNextMessage(): ByteString = single(CommandCode.SyncNextMessage)

  /** `[0x02][txt_type][attempt][timestamp x4][pub_key_prefix x6][text]\0` */
  fun sendTextMessage(
    recipient: PublicKey,
    text: String,
    timestamp: Instant,
    attempt: Int = 0,
    textType: TextType = TextType.Plain,
  ): ByteString {
    val textBytes = text.encodeToByteArray()
    require(textBytes.size <= MAX_TEXT_PAYLOAD_BYTES) {
      "text exceeds $MAX_TEXT_PAYLOAD_BYTES bytes"
    }
    return buildByteString {
      writeByte(CommandCode.SendTextMessage.raw)
      writeByte(textType.raw)
      writeByte(attempt.toByte())
      writeIntLe(timestamp.epochSeconds.toInt())
      write(recipient.prefix.toByteArray())
      writeCString(text)
    }
  }

  /** `[0x03][txt_type][channel_idx][timestamp x4][text]\0` */
  fun sendChannelTextMessage(
    channelIdx: Int,
    text: String,
    timestamp: Instant,
    textType: TextType = TextType.Plain,
  ): ByteString = buildByteString {
    writeByte(CommandCode.SendChannelTextMessage.raw)
    writeByte(textType.raw)
    writeByte(channelIdx.toByte())
    writeIntLe(timestamp.epochSeconds.toInt())
    writeCString(text)
  }

  private fun single(code: CommandCode): ByteString {
    val b = ByteStringBuilder(1)
    b.append(code.raw)
    return b.toByteString()
  }
}
