package ee.schimke.meshcore.grpc

import ee.schimke.meshcore.core.manager.ManagerState
import ee.schimke.meshcore.core.model.BatteryInfo
import ee.schimke.meshcore.core.model.ChannelInfo
import ee.schimke.meshcore.core.model.Contact
import ee.schimke.meshcore.core.model.ContactType
import ee.schimke.meshcore.core.model.RadioSettings
import ee.schimke.meshcore.core.model.ReceivedChannelMessage
import ee.schimke.meshcore.core.model.ReceivedDirectMessage
import ee.schimke.meshcore.core.model.SelfInfo
import com.google.protobuf.ByteString as ProtoByteString

fun ManagerState.toConnectionStatus(): ConnectionStatus {
    val (state, error) = when (this) {
        is ManagerState.Idle -> ConnectionState.DISCONNECTED to ""
        is ManagerState.Connecting -> ConnectionState.CONNECTING to ""
        is ManagerState.Connected -> ConnectionState.CONNECTED to ""
        is ManagerState.Failed -> ConnectionState.FAILED to (cause.message ?: "Unknown error")
    }
    return ConnectionStatus.newBuilder()
        .setState(state)
        .setErrorMessage(error)
        .build()
}

fun SelfInfo.toProto(): SelfInfoResponse =
    SelfInfoResponse.newBuilder()
        .setAvailable(true)
        .setName(name)
        .setPublicKey(publicKey.bytes.toProtoBytes())
        .setLatitude(latitude)
        .setLongitude(longitude)
        .setTxPowerDbm(txPowerDbm)
        .setMaxPowerDbm(maxPowerDbm)
        .setRadio(radio.toProto())
        .build()

fun RadioSettings.toProto(): RadioSettingsMsg =
    RadioSettingsMsg.newBuilder()
        .setFrequencyHz(frequencyHz)
        .setBandwidthHz(bandwidthHz)
        .setSpreadingFactor(spreadingFactor)
        .setCodingRate(codingRate)
        .build()

fun Contact.toProto(): ContactMsg =
    ContactMsg.newBuilder()
        .setPublicKey(publicKey.bytes.toProtoBytes())
        .setType(type.toProto())
        .setName(name)
        .setLatitude(latitude)
        .setLongitude(longitude)
        .setAdvertTimestampEpochSeconds(advertTimestamp.epochSeconds)
        .setLastModifiedEpochSeconds(lastModified.epochSeconds)
        .setPathLength(pathLength)
        .setIsFlood(isFlood)
        .build()

fun ContactType.toProto(): ee.schimke.meshcore.grpc.ContactType = when (this) {
    ContactType.CHAT -> ee.schimke.meshcore.grpc.ContactType.CHAT
    ContactType.REPEATER -> ee.schimke.meshcore.grpc.ContactType.REPEATER
    ContactType.ROOM -> ee.schimke.meshcore.grpc.ContactType.ROOM
    ContactType.SENSOR -> ee.schimke.meshcore.grpc.ContactType.SENSOR
    ContactType.UNKNOWN -> ee.schimke.meshcore.grpc.ContactType.CONTACT_TYPE_UNKNOWN
}

fun ChannelInfo.toProto(): ChannelMsg =
    ChannelMsg.newBuilder()
        .setIndex(index)
        .setName(name)
        .build()

fun BatteryInfo.toProto(): BatteryInfoResponse =
    BatteryInfoResponse.newBuilder()
        .setAvailable(true)
        .setMillivolts(millivolts)
        .setStorageUsedKb(storageUsedKb)
        .setStorageTotalKb(storageTotalKb)
        .setEstimatedPercent(estimatePercent())
        .build()

fun ReceivedDirectMessage.toProto(): DirectMessageMsg =
    DirectMessageMsg.newBuilder()
        .setSenderPrefix(senderPrefix.bytes.toProtoBytes())
        .setText(text)
        .setTimestampEpochSeconds(timestamp.epochSeconds)
        .setSnr(snr)
        .setPathLength(pathLength)
        .build()

fun ReceivedChannelMessage.toProto(): ChannelMessageMsg =
    ChannelMessageMsg.newBuilder()
        .setChannelIndex(channelIndex)
        .setSender(sender ?: "")
        .setText(text)
        .setTimestampEpochSeconds(timestamp.epochSeconds)
        .setSnr(snr)
        .setPathLength(pathLength)
        .build()

/** Convert kotlinx-io ByteString (used by PublicKey) to protobuf ByteString. */
private fun kotlinx.io.bytestring.ByteString.toProtoBytes(): ProtoByteString =
    ProtoByteString.copyFrom(toByteArray())
