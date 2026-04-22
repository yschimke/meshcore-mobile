package ee.schimke.meshcore.core.protocol

import ee.schimke.meshcore.core.model.AdvertPushInfo
import ee.schimke.meshcore.core.model.BatteryInfo
import ee.schimke.meshcore.core.model.ChannelInfo
import ee.schimke.meshcore.core.model.Contact
import ee.schimke.meshcore.core.model.ContactType
import ee.schimke.meshcore.core.model.DeviceInfo
import ee.schimke.meshcore.core.model.MeshEvent
import ee.schimke.meshcore.core.model.PublicKey
import ee.schimke.meshcore.core.model.RadioSettings
import ee.schimke.meshcore.core.model.ReceivedChannelMessage
import ee.schimke.meshcore.core.model.ReceivedDirectMessage
import ee.schimke.meshcore.core.model.SelfInfo
import ee.schimke.meshcore.core.model.SendAck
import ee.schimke.meshcore.core.model.SendConfirmed
import ee.schimke.meshcore.core.protocol.BufferExt.readCStringFixed
import ee.schimke.meshcore.core.protocol.BufferExt.readCStringRemaining
import ee.schimke.meshcore.core.protocol.MeshCoreConstants.PUB_KEY_PREFIX_SIZE
import ee.schimke.meshcore.core.protocol.MeshCoreConstants.PUB_KEY_SIZE
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.isEmpty
import kotlinx.io.readByteString
import kotlinx.io.readIntLe
import kotlinx.io.readShortLe
import kotlin.time.Instant

/** Parse a raw frame payload `[code][body...]` into a [MeshEvent]. */
object Parsers {

    fun parse(frame: ByteString): MeshEvent {
        if (frame.isEmpty()) return MeshEvent.Raw(0, frame)
        val code = frame[0]
        val buf = Buffer().apply { write(frame.toByteArray()) }
        buf.readByte() // discard code byte
        return try {
            ResponseCode.fromRaw(code)?.let { parseResponse(it, buf, frame) }
                ?: PushCode.fromRaw(code)?.let { parsePush(it, buf) }
                ?: MeshEvent.Raw(code, frame.substring(1))
        } catch (_: Throwable) {
            MeshEvent.Raw(code, frame.substring(1))
        }
    }

    private fun parseResponse(code: ResponseCode, buf: Buffer, frame: ByteString): MeshEvent =
        when (code) {
            ResponseCode.Ok -> MeshEvent.Ok
            ResponseCode.Err -> MeshEvent.Err(if (frame.size > 1) frame[1].toInt() and 0xFF else 0)
            ResponseCode.ContactsStart -> MeshEvent.ContactsStart
            ResponseCode.EndOfContacts -> MeshEvent.EndOfContacts
            ResponseCode.NoMoreMessages -> MeshEvent.NoMoreMessages
            ResponseCode.SelfInfo -> parseSelfInfo(buf)
            ResponseCode.Contact -> parseContact(buf)
            ResponseCode.BatteryAndStorage -> parseBatteryAndStorage(buf)
            ResponseCode.DeviceInfo -> parseDeviceInfo(buf)
            ResponseCode.RadioSettings -> parseRadioSettings(buf)
            ResponseCode.CurrentTime -> MeshEvent.CurrentTime(readEpochSeconds(buf))
            ResponseCode.Sent -> parseSent(buf)
            ResponseCode.ContactMessageV3 -> parseContactMsgV3(buf)
            ResponseCode.ChannelMessageV3 -> parseChannelMsgV3(buf)
            ResponseCode.ChannelInfo -> parseChannelInfo(buf)
            else -> MeshEvent.Raw(code.raw, frame.substring(1))
        }

    private fun parsePush(code: PushCode, buf: Buffer): MeshEvent =
        when (code) {
            PushCode.Advert -> MeshEvent.Advert(AdvertPushInfo(readPubKey(buf)))
            PushCode.NewAdvert -> MeshEvent.NewAdvert(AdvertPushInfo(readPubKey(buf)))
            PushCode.PathUpdated -> MeshEvent.PathUpdated(readPubKey(buf))
            PushCode.SendConfirmed -> parseSendConfirmed(buf)
            PushCode.MessagesWaiting -> MeshEvent.MessagesWaiting
            PushCode.LoginSuccess -> {
                // Permissions byte precedes the prefix (bit 0 = is_admin).
                buf.readByte()
                MeshEvent.LoginSuccess(readPubKeyPrefix(buf))
            }
            PushCode.LoginFail -> {
                // Reserved byte precedes the prefix.
                buf.readByte()
                MeshEvent.LoginFail(readPubKeyPrefix(buf))
            }
            else -> MeshEvent.Raw(code.raw, ByteString())
        }

    private fun readEpochSeconds(src: Source): Instant =
        Instant.fromEpochSeconds(src.readIntLe().toLong() and 0xFFFFFFFFL)

    private fun readPubKey(src: Source): PublicKey =
        PublicKey.fromBytes(src.readByteString(PUB_KEY_SIZE))

    private fun readPubKeyPrefix(src: Source): PublicKey =
        PublicKey.ofPrefix(src.readByteString(PUB_KEY_PREFIX_SIZE))

    private fun parseSelfInfo(src: Source): MeshEvent.SelfInfoEvent {
        val advType = src.readByte().toInt() and 0xFF
        val txPwr = src.readByte().toInt()
        val maxPwr = src.readByte().toInt()
        val pub = PublicKey.fromBytes(src.readByteString(PUB_KEY_SIZE))
        val lat = src.readIntLe() / 1_000_000.0
        val lon = src.readIntLe() / 1_000_000.0
        val multiAcks = src.readByte().toInt() and 0xFF
        val advPolicy = src.readByte().toInt() and 0xFF
        val telemetry = src.readByte().toInt() and 0xFF
        val manualAdd = src.readByte().toInt() and 0xFF
        val freq = src.readIntLe()
        val bw = src.readIntLe()
        val sf = src.readByte().toInt() and 0xFF
        val cr = src.readByte().toInt() and 0xFF
        val name = src.readCStringRemaining()
        return MeshEvent.SelfInfoEvent(
            SelfInfo(
                advertType = advType,
                txPowerDbm = txPwr,
                maxPowerDbm = maxPwr,
                publicKey = pub,
                latitude = lat,
                longitude = lon,
                multiAcks = multiAcks,
                advertLocationPolicy = advPolicy,
                telemetryFlags = telemetry,
                manualAddContacts = manualAdd,
                radio = RadioSettings(freq, bw, sf, cr),
                name = name,
            ),
        )
    }

    private fun parseContact(src: Source): MeshEvent.ContactEvent {
        val pub = PublicKey.fromBytes(src.readByteString(PUB_KEY_SIZE))
        val type = src.readByte().toInt() and 0xFF
        val flags = src.readByte().toInt() and 0xFF
        val rawPathLen = src.readByte().toInt() and 0xFF
        val pathLen = if (rawPathLen == 0xFF) -1 else rawPathLen
        val rawPath = src.readByteString(64)
        val path = if (pathLen in 1..64) rawPath.substring(0, pathLen) else ByteString()
        val name = src.readCStringFixed(32)
        val ts = readEpochSeconds(src)
        val lat = src.readIntLe() / 1_000_000.0
        val lon = src.readIntLe() / 1_000_000.0
        val lastMod = readEpochSeconds(src)
        return MeshEvent.ContactEvent(
            Contact(pub, ContactType.fromRaw(type), flags, pathLen, path, name, ts, lat, lon, lastMod),
        )
    }

    private fun parseBatteryAndStorage(src: Source): MeshEvent.Battery {
        val mv = src.readShortLe().toInt() and 0xFFFF
        val used = src.readIntLe().toLong() and 0xFFFFFFFFL
        val total = src.readIntLe().toLong() and 0xFFFFFFFFL
        return MeshEvent.Battery(BatteryInfo(mv, used, total))
    }

    private fun parseDeviceInfo(src: Source): MeshEvent.Device {
        val pv = src.readByte().toInt() and 0xFF
        val contacts = (src.readByte().toInt() and 0xFF) * 2
        val channels = src.readByte().toInt() and 0xFF
        return MeshEvent.Device(DeviceInfo(pv, contacts, channels))
    }

    private fun parseRadioSettings(src: Source): MeshEvent.Radio {
        val freq = src.readIntLe()
        val bw = src.readIntLe()
        val sf = src.readByte().toInt() and 0xFF
        val cr = src.readByte().toInt() and 0xFF
        return MeshEvent.Radio(RadioSettings(freq, bw, sf, cr))
    }

    private fun parseSent(src: Source): MeshEvent.Sent {
        val isFlood = src.readByte().toInt() != 0
        val ackHash = src.readIntLe()
        val timeout = src.readIntLe().toLong() and 0xFFFFFFFFL
        return MeshEvent.Sent(SendAck(isFlood, ackHash, timeout))
    }

    private fun parseSendConfirmed(src: Source): MeshEvent.SendConfirmedEvent {
        val ackHash = src.readIntLe()
        val trip = src.readIntLe().toLong() and 0xFFFFFFFFL
        return MeshEvent.SendConfirmedEvent(SendConfirmed(ackHash, trip))
    }

    /** `[idx][name×32][psk×16]` */
    private fun parseChannelInfo(src: Source): MeshEvent.ChannelInfoEvent {
        val idx = src.readByte().toInt() and 0xFF
        val name = src.readCStringFixed(32)
        val psk = src.readByteString(16)
        return MeshEvent.ChannelInfoEvent(ChannelInfo(idx, name, psk))
    }

    private fun parseContactMsgV3(src: Source): MeshEvent.DirectMessage {
        val snr = src.readByte().toInt()
        src.readShort()
        val prefix = PublicKey.ofPrefix(src.readByteString(PUB_KEY_PREFIX_SIZE))
        val rawPathLen = src.readByte().toInt() and 0xFF
        val pathLen = if (rawPathLen == 0xFF) -1 else rawPathLen
        val rawTxt = src.readByte().toInt()
        val txtType = TextType.fromRaw(rawTxt)
            ?: error("unknown text type 0x${rawTxt.toString(16)}")
        val ts = readEpochSeconds(src)
        val text = src.readCStringRemaining()
        return MeshEvent.DirectMessage(
            ReceivedDirectMessage(snr, prefix, pathLen, ts, txtType, text),
        )
    }

    private fun parseChannelMsgV3(src: Source): MeshEvent.ChannelMessage {
        val snr = src.readByte().toInt()
        src.readShort()
        val channelIdx = src.readByte().toInt() and 0xFF
        val rawPathLen = src.readByte().toInt() and 0xFF
        val pathLen = if (rawPathLen == 0xFF) -1 else rawPathLen
        val rawTxt = src.readByte().toInt()
        val txtType = TextType.fromRaw(rawTxt)
            ?: error("unknown text type 0x${rawTxt.toString(16)}")
        val ts = readEpochSeconds(src)
        val body = src.readCStringRemaining()
        return MeshEvent.ChannelMessage(
            ReceivedChannelMessage(snr, channelIdx, pathLen, ts, txtType, body),
        )
    }
}
