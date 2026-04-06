package ee.schimke.meshcore.app.connection

import dev.mcarr.usb.interfaces.ISerialPortWrapper
import ee.schimke.meshcore.core.client.MeshCoreClient
import ee.schimke.meshcore.transport.ble.BleAdvertisement

/**
 * Everything the UI needs to know about the current connection
 * attempt / active session. Owned by [AppConnectionController] and
 * exposed as a `StateFlow<ConnectionUiState>`; the UI never talks to
 * the lower-level [ee.schimke.meshcore.core.manager.MeshCoreManager] directly.
 */
sealed class ConnectionUiState {
    /** No active connection and no pending attempt. */
    data object Idle : ConnectionUiState()

    /**
     * A connect attempt is in progress. [startedAtMs] is stable for
     * the duration of the attempt; the connecting card animates its
     * own progress bar against the wall clock so recomposition
     * doesn't need external ticks.
     */
    data class Connecting(
        val startedAtMs: Long,
        val timeoutMs: Long,
        val deviceLabel: String,
    ) : ConnectionUiState()

    data class Connected(val client: MeshCoreClient) : ConnectionUiState()

    data class Failed(val cause: Throwable, val deviceLabel: String?) : ConnectionUiState()

    /** Pausing before the next automatic reconnect attempt. */
    data class Retrying(
        val attempt: Int,
        val maxAttempts: Int,
        val nextRetryAtMs: Long,
        val deviceLabel: String,
    ) : ConnectionUiState()
}

/** Description of a connect request, independent of whether it's been dispatched. */
sealed class ConnectionRequest {
    abstract val label: String

    data class Ble(val adv: BleAdvertisement) : ConnectionRequest() {
        override val label: String get() = adv.name ?: adv.identifier
    }

    data class Tcp(val host: String, val port: Int) : ConnectionRequest() {
        override val label: String get() = "$host:$port"
    }

    data class Usb(
        val port: ISerialPortWrapper,
        override val label: String = "USB %04X:%04X".format(port.vendorId, port.productId),
    ) : ConnectionRequest()
}
