package ee.schimke.meshcore.core.model

import ee.schimke.meshcore.core.protocol.TextType
import kotlin.time.Instant
import kotlinx.io.bytestring.ByteString

/** Device identity + radio config returned by `CMD_APP_START`. */
data class SelfInfo(
    val advertType: Int,
    val txPowerDbm: Int,
    val maxPowerDbm: Int,
    val publicKey: PublicKey,
    val latitude: Double,
    val longitude: Double,
    val multiAcks: Int,
    val advertLocationPolicy: Int,
    val telemetryFlags: Int,
    val manualAddContacts: Int,
    val radio: RadioSettings,
    val name: String,
)

data class RadioSettings(
    val frequencyHz: Int,
    val bandwidthHz: Int,
    val spreadingFactor: Int,
    val codingRate: Int,
)

enum class ContactType(val raw: Int) {
    CHAT(1), REPEATER(2), ROOM(3), SENSOR(4), UNKNOWN(-1);
    companion object {
        fun fromRaw(r: Int) = entries.firstOrNull { it.raw == r } ?: UNKNOWN
    }
}

data class Contact(
    val publicKey: PublicKey,
    val type: ContactType,
    val flags: Int,
    val pathLength: Int, // -1 means flood mode (path_len was 0xFF)
    val path: ByteString,
    val name: String,
    val advertTimestamp: Instant,
    val latitude: Double,
    val longitude: Double,
    val lastModified: Instant,
) {
    val isFlood: Boolean get() = pathLength < 0
}

data class BatteryInfo(
    val millivolts: Int,
    val storageUsedKb: Long,
    val storageTotalKb: Long,
) {
    /** Simple estimate assuming LiPo/NMC chemistry (3.0–4.2 V). */
    fun estimatePercent(minMv: Int = 3000, maxMv: Int = 4200): Int {
        if (millivolts <= minMv) return 0
        if (millivolts >= maxMv) return 100
        return ((millivolts - minMv) * 100) / (maxMv - minMv)
    }
}

data class DeviceInfo(
    val protocolVersion: Int,
    val maxContacts: Int,
    val maxChannels: Int,
)

data class ChannelInfo(
    val index: Int,
    val name: String,
    val psk: ByteString,
)

data class ReceivedDirectMessage(
    val snr: Int,
    val senderPrefix: PublicKey,
    val pathLength: Int,
    val timestamp: Instant,
    val textType: TextType,
    val text: String,
)

data class ReceivedChannelMessage(
    val snr: Int,
    val channelIndex: Int,
    val pathLength: Int,
    val timestamp: Instant,
    val textType: TextType,
    /** Raw "sender: text" combined form as delivered by the device. */
    val body: String,
) {
    val sender: String? get() = body.substringBefore(": ", "").ifEmpty { null }
    val text: String get() = if (body.contains(": ")) body.substringAfter(": ") else body
}

data class SendAck(
    val isFlood: Boolean,
    val ackHash: Int,
    val timeoutMs: Long,
)

data class SendConfirmed(
    val ackHash: Int,
    val tripTimeMs: Long,
)

data class AdvertPushInfo(
    val publicKey: PublicKey,
)
