package ee.schimke.meshcore.grpc

import ee.schimke.meshcore.core.model.MeshEvent
import ee.schimke.meshcore.core.model.PublicKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

class MeshcoreGrpcServiceImpl(
    private val bridge: MeshServiceBridge,
) : MeshcoreServiceGrpcKt.MeshcoreServiceCoroutineImplBase() {

    override suspend fun getConnectionStatus(request: Empty): ConnectionStatus =
        bridge.managerState.value.toConnectionStatus()

    override fun subscribeConnectionStatus(request: Empty): Flow<ConnectionStatus> =
        bridge.managerState.map { it.toConnectionStatus() }

    override suspend fun getSelfInfo(request: Empty): SelfInfoResponse {
        val info = bridge.client?.selfInfo?.value
            ?: return SelfInfoResponse.newBuilder().setAvailable(false).build()
        return info.toProto()
    }

    override suspend fun getContacts(request: GetContactsRequest): ContactList {
        val client = bridge.client
        if (request.refresh && client != null) {
            runCatching { client.getContacts() }
        }
        val contacts = client?.contacts?.value ?: emptyList()
        return ContactList.newBuilder()
            .addAllContacts(contacts.map { it.toProto() })
            .build()
    }

    override suspend fun getChannels(request: Empty): ChannelList {
        val channels = bridge.client?.channels?.value ?: emptyList()
        return ChannelList.newBuilder()
            .addAllChannels(channels.map { it.toProto() })
            .build()
    }

    override suspend fun getBatteryInfo(request: Empty): BatteryInfoResponse {
        val info = bridge.client?.battery?.value
            ?: return BatteryInfoResponse.newBuilder().setAvailable(false).build()
        return info.toProto()
    }

    override suspend fun sendDirectMessage(request: SendDirectMessageRequest): SendAckResponse {
        val client = bridge.client
            ?: return SendAckResponse.newBuilder()
                .setSuccess(false)
                .setErrorMessage("Not connected")
                .build()
        return try {
            val recipient = PublicKey.fromBytes(request.recipientPublicKey.toByteArray())
            val now = Clock.System.now()
            val ack = client.sendText(
                recipient = recipient,
                text = request.text,
                timestamp = now,
            )
            bridge.persistSentDm(
                contactKeyHex = recipient.toHex(),
                text = request.text,
                timestamp = now,
                ackHash = ack.ackHash,
            )
            SendAckResponse.newBuilder()
                .setSuccess(true)
                .setAckHash(ack.ackHash)
                .setIsFlood(ack.isFlood)
                .build()
        } catch (e: Exception) {
            SendAckResponse.newBuilder()
                .setSuccess(false)
                .setErrorMessage(e.message ?: "Send failed")
                .build()
        }
    }

    override suspend fun sendChannelMessage(request: SendChannelMessageRequest): SendAckResponse {
        val client = bridge.client
            ?: return SendAckResponse.newBuilder()
                .setSuccess(false)
                .setErrorMessage("Not connected")
                .build()
        return try {
            val now = Clock.System.now()
            val ack = client.sendChannelText(
                channelIdx = request.channelIndex,
                text = request.text,
                timestamp = now,
            )
            // Message appears in history when the device echoes it back
            SendAckResponse.newBuilder()
                .setSuccess(true)
                .setAckHash(ack.ackHash)
                .setIsFlood(ack.isFlood)
                .build()
        } catch (e: Exception) {
            SendAckResponse.newBuilder()
                .setSuccess(false)
                .setErrorMessage(e.message ?: "Send failed")
                .build()
        }
    }

    override fun subscribeMessages(request: Empty): Flow<MeshMessage> {
        val client = bridge.client ?: return emptyFlow()
        return client.events
            .filter { it is MeshEvent.DirectMessage || it is MeshEvent.ChannelMessage }
            .map { event ->
                val builder = MeshMessage.newBuilder()
                when (event) {
                    is MeshEvent.DirectMessage -> builder.setDirectMessage(event.message.toProto())
                    is MeshEvent.ChannelMessage -> builder.setChannelMessage(event.message.toProto())
                    else -> error("unreachable")
                }
                builder.build()
            }
    }
}
