package ee.schimke.meshcore.data.sync

import ee.schimke.meshcore.core.model.MeshEvent
import ee.schimke.meshcore.core.model.PublicKey
import ee.schimke.meshcore.data.repository.MeshcoreRepository
import kotlinx.coroutines.flow.SharedFlow

/**
 * Collects [MeshEvent]s from a connected client and persists messages
 * to Room. Launch this on a coroutine scope that lives as long as the
 * connection.
 */
class MessagePersister(
    private val repository: MeshcoreRepository,
    private val deviceId: String,
    private val contactResolver: (PublicKey) -> String?,
) {
    suspend fun collect(events: SharedFlow<MeshEvent>) {
        events.collect { event ->
            when (event) {
                is MeshEvent.DirectMessage -> {
                    val msg = event.message
                    val fullHex = contactResolver(msg.senderPrefix) ?: msg.senderPrefix.toHex()
                    repository.insertReceivedDm(deviceId, msg, fullHex)
                }
                is MeshEvent.ChannelMessage -> {
                    repository.insertReceivedChannelMessage(deviceId, event.message)
                }
                is MeshEvent.SendConfirmedEvent -> {
                    repository.markConfirmed(event.confirmed.ackHash)
                }
                else -> Unit
            }
        }
    }
}
