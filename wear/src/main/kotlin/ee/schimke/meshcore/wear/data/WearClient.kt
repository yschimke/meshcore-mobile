package ee.schimke.meshcore.wear.data

import com.google.protobuf.ByteString
import ee.schimke.meshcore.grpc.BatteryInfoResponse
import ee.schimke.meshcore.grpc.ChannelList
import ee.schimke.meshcore.grpc.ConnectionStatus
import ee.schimke.meshcore.grpc.ContactList
import ee.schimke.meshcore.grpc.MeshMessage
import ee.schimke.meshcore.grpc.SelfInfoResponse
import ee.schimke.meshcore.grpc.SendAckResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The watch-side view of the phone's Meshcore service.
 *
 * Exists so the object graph can hand the UI something that does no I/O when
 * there is no phone to talk to — see [PreviewWearClient]. [MeshcoreWearClient]
 * is the real implementation, over the Wearable Data Layer.
 */
interface WearClient {
    suspend fun getConnectionStatus(): ConnectionStatus

    suspend fun getSelfInfo(): SelfInfoResponse

    suspend fun getContacts(refresh: Boolean = false): ContactList

    suspend fun getChannels(): ChannelList

    suspend fun getBatteryInfo(): BatteryInfoResponse

    suspend fun sendDirectMessage(recipientPublicKey: ByteString, text: String): SendAckResponse

    suspend fun sendChannelMessage(channelIndex: Int, text: String): SendAckResponse

    fun subscribeConnectionStatus(): Flow<ConnectionStatus>

    fun subscribeMessages(): Flow<MeshMessage>
}

/**
 * A [WearClient] that never touches the Wearable Data Layer.
 *
 * The Play Services Wearable API is unavailable under Robolectric — any call
 * fails with `ApiException: 17: Wearable.API is not available on this device`,
 * thrown on the data-layer registry's own coroutine scope, where the UI's
 * try/catch guards can't see it. That escaping exception is what stopped
 * `wear/activity__WearMainActivity` from ever producing a PNG. Substituting this
 * in [ee.schimke.meshcore.wear.WearAppGraph] lets the activity preview compose
 * the real navigation graph and render a deterministic frame.
 *
 * Every getter returns its message's default instance, so the UI resolves to its
 * disconnected state rather than inventing plausible-looking fake device data —
 * a preview that showed a fabricated battery level and contact list would be
 * misleading as a design reference.
 */
object PreviewWearClient : WearClient {
    override suspend fun getConnectionStatus(): ConnectionStatus =
        ConnectionStatus.getDefaultInstance()

    override suspend fun getSelfInfo(): SelfInfoResponse = SelfInfoResponse.getDefaultInstance()

    override suspend fun getContacts(refresh: Boolean): ContactList = ContactList.getDefaultInstance()

    override suspend fun getChannels(): ChannelList = ChannelList.getDefaultInstance()

    override suspend fun getBatteryInfo(): BatteryInfoResponse = BatteryInfoResponse.getDefaultInstance()

    override suspend fun sendDirectMessage(
        recipientPublicKey: ByteString,
        text: String,
    ): SendAckResponse = SendAckResponse.getDefaultInstance()

    override suspend fun sendChannelMessage(channelIndex: Int, text: String): SendAckResponse =
        SendAckResponse.getDefaultInstance()

    override fun subscribeConnectionStatus(): Flow<ConnectionStatus> = emptyFlow()

    override fun subscribeMessages(): Flow<MeshMessage> = emptyFlow()
}
