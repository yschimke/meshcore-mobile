@file:OptIn(ExperimentalHorologistApi::class)

package ee.schimke.meshcore.wear.data

import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.data.TargetNodeId
import com.google.android.horologist.data.WearDataLayerRegistry
import com.google.android.horologist.datalayer.grpc.client.MessageClientChannel
import ee.schimke.meshcore.grpc.Empty
import ee.schimke.meshcore.grpc.GetContactsRequest
import ee.schimke.meshcore.grpc.MeshcoreServiceGrpcKt
import ee.schimke.meshcore.grpc.SendChannelMessageRequest
import ee.schimke.meshcore.grpc.SendDirectMessageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow

/**
 * Watch-side gRPC client that communicates with the phone app's
 * MeshcoreWearDataService over the Wearable Data Layer.
 *
 * Uses Horologist's [MessageClientChannel] to create a gRPC channel,
 * then delegates to the generated coroutine stub.
 */
class MeshcoreWearClient(registry: WearDataLayerRegistry) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val channel = MessageClientChannel(
        nodeId = TargetNodeId.PairedPhone,
        path = "/WearGrpcService/ee.schimke.meshcore.grpc.MeshcoreService",
        wearDataLayerRegistry = registry,
        coroutineScope = scope,
    )

    private val stub = MeshcoreServiceGrpcKt.MeshcoreServiceCoroutineStub(channel)

    private val empty = Empty.getDefaultInstance()

    suspend fun getConnectionStatus() = stub.getConnectionStatus(empty)

    suspend fun getSelfInfo() = stub.getSelfInfo(empty)

    suspend fun getContacts(refresh: Boolean = false) =
        stub.getContacts(GetContactsRequest.newBuilder().setRefresh(refresh).build())

    suspend fun getChannels() = stub.getChannels(empty)

    suspend fun getBatteryInfo() = stub.getBatteryInfo(empty)

    suspend fun sendDirectMessage(recipientPublicKey: com.google.protobuf.ByteString, text: String) =
        stub.sendDirectMessage(
            SendDirectMessageRequest.newBuilder()
                .setRecipientPublicKey(recipientPublicKey)
                .setText(text)
                .build(),
        )

    suspend fun sendChannelMessage(channelIndex: Int, text: String) =
        stub.sendChannelMessage(
            SendChannelMessageRequest.newBuilder()
                .setChannelIndex(channelIndex)
                .setText(text)
                .build(),
        )

    fun subscribeConnectionStatus(): Flow<ee.schimke.meshcore.grpc.ConnectionStatus> =
        stub.subscribeConnectionStatus(empty)

    fun subscribeMessages(): Flow<ee.schimke.meshcore.grpc.MeshMessage> =
        stub.subscribeMessages(empty)
}
