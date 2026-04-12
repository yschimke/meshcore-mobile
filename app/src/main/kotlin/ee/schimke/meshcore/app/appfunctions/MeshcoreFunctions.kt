package ee.schimke.meshcore.app.appfunctions

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.service.AppFunction
import ee.schimke.meshcore.app.MeshcoreApp
import ee.schimke.meshcore.app.connection.ConnectionUiState
import ee.schimke.meshcore.core.model.BatteryInfo
import ee.schimke.meshcore.core.model.PublicKey
import kotlin.time.Clock
import kotlinx.coroutines.flow.first

/**
 * Exposes MeshCore device functionality as Android App Functions,
 * making them discoverable by the system assistant and AI agents.
 *
 * All read functions operate on cached data from the user's favorite
 * device and work even when the device is disconnected.
 *
 * Write functions (sending messages) require an active connection.
 */
class MeshcoreFunctions {

    private val app get() = MeshcoreApp.get()
    private val repository get() = app.repository

    /**
     * Gets the status of the user's favorite mesh device, including
     * battery level, radio frequency, and storage usage.
     *
     * @param appFunctionContext The context in which the AppFunction is executed.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getDeviceStatus(appFunctionContext: AppFunctionContext): DeviceStatus? {
        val favorite = repository.observeFavorite().first() ?: return null
        val state = repository.getDeviceState(favorite.id) ?: return null

        val batteryPercent = state.batteryMillivolts?.let {
            BatteryInfo(it, state.storageUsedKb ?: 0, state.storageTotalKb ?: 0).estimatePercent()
        }

        return DeviceStatus(
            name = state.selfName ?: favorite.label,
            batteryPercent = batteryPercent,
            batteryMillivolts = state.batteryMillivolts,
            frequencyMhz = state.radioFrequencyHz?.let { it / 1_000_000.0 },
            bandwidthKhz = state.radioBandwidthHz?.let { it / 1000 },
            spreadingFactor = state.radioSpreadingFactor,
            storageUsedKb = state.storageUsedKb,
            storageTotalKb = state.storageTotalKb,
        )
    }

    /**
     * Lists all contacts on the user's favorite mesh device.
     *
     * @param appFunctionContext The context in which the AppFunction is executed.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getContacts(appFunctionContext: AppFunctionContext): List<MeshContact>? {
        val favorite = repository.observeFavorite().first() ?: return null
        val contacts = repository.getContacts(favorite.id)
        if (contacts.isEmpty()) return null

        return contacts.map { contact ->
            MeshContact(
                name = contact.name,
                type = contact.type.name,
                pathLength = contact.pathLength,
                latitude = contact.latitude,
                longitude = contact.longitude,
                publicKeyHex = contact.publicKey.toHex(),
            )
        }
    }

    /**
     * Gets recent messages (both DMs and channel messages) from the
     * user's favorite mesh device, ordered newest first.
     *
     * @param appFunctionContext The context in which the AppFunction is executed.
     * @param limit Maximum number of messages to return (default 20).
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getRecentMessages(
        appFunctionContext: AppFunctionContext,
        limit: Int = 20,
    ): List<RecentMessage>? {
        val favorite = repository.observeFavorite().first() ?: return null
        val messages = repository.getRecentMessages(favorite.id, limit)
        if (messages.isEmpty()) return null

        return messages.map { msg ->
            RecentMessage(
                text = msg.text,
                senderName = msg.senderName,
                kind = msg.kind.name,
                direction = msg.direction.name,
                timestampMs = msg.timestampEpochMs,
            )
        }
    }

    /**
     * Lists all channels available on the user's favorite mesh device.
     *
     * @param appFunctionContext The context in which the AppFunction is executed.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getChannels(appFunctionContext: AppFunctionContext): List<MeshChannel>? {
        val favorite = repository.observeFavorite().first() ?: return null
        val channels = repository.getChannels(favorite.id)
        if (channels.isEmpty()) return null

        return channels.map { ch ->
            MeshChannel(
                index = ch.index,
                name = ch.name,
            )
        }
    }

    /**
     * Sends a direct message to a contact on the mesh network.
     * Requires an active connection to the mesh device.
     *
     * @param appFunctionContext The context in which the AppFunction is executed.
     * @param contactPublicKeyHex The recipient contact's public key in hex.
     * @param message The message text to send.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun sendDirectMessage(
        appFunctionContext: AppFunctionContext,
        contactPublicKeyHex: String,
        message: String,
    ): SendResult {
        val connectionState = app.connectionController.state.value
        val client = (connectionState as? ConnectionUiState.Connected)?.client
            ?: return SendResult(success = false, error = "Not connected to a mesh device")

        val deviceId = app.connectionController.connectedDeviceId.value
            ?: return SendResult(success = false, error = "No device ID available")

        return try {
            val recipient = PublicKey.fromHex(contactPublicKeyHex)
            val now = Clock.System.now()
            val ack = client.sendText(recipient = recipient, text = message, timestamp = now)
            repository.insertSentDm(
                deviceId = deviceId,
                contactKeyHex = contactPublicKeyHex,
                text = message,
                timestamp = now,
                ackHash = ack.ackHash,
            )
            SendResult(success = true, error = null)
        } catch (e: Exception) {
            SendResult(success = false, error = e.message ?: "Failed to send message")
        }
    }

    /**
     * Sends a message to a channel on the mesh network.
     * Requires an active connection to the mesh device.
     *
     * @param appFunctionContext The context in which the AppFunction is executed.
     * @param channelIndex The index of the channel to send to.
     * @param message The message text to send.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun sendChannelMessage(
        appFunctionContext: AppFunctionContext,
        channelIndex: Int,
        message: String,
    ): SendResult {
        val connectionState = app.connectionController.state.value
        val client = (connectionState as? ConnectionUiState.Connected)?.client
            ?: return SendResult(success = false, error = "Not connected to a mesh device")

        return try {
            val now = Clock.System.now()
            client.sendChannelText(channelIdx = channelIndex, text = message, timestamp = now)
            // Message appears in history when the device echoes it back
            SendResult(success = true, error = null)
        } catch (e: Exception) {
            SendResult(success = false, error = e.message ?: "Failed to send message")
        }
    }
}
