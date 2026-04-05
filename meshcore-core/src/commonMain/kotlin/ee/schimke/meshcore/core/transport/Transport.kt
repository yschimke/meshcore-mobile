package ee.schimke.meshcore.core.transport

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.io.bytestring.ByteString

/**
 * Abstract I/O link to a MeshCore device. Concrete implementations wrap
 * BLE (Nordic UART), USB CDC-ACM, or TCP. Each [incoming] emission is one
 * complete MeshCore frame payload `[code][body...]` – stream-transport
 * framing has already been stripped.
 */
interface Transport {
    val state: StateFlow<TransportState>

    /** Hot flow of decoded frame payloads. */
    val incoming: SharedFlow<ByteString>

    /** Open the underlying link. Suspends until connected or throws. */
    suspend fun connect()

    /** Send a raw MeshCore frame `[code][body...]`. */
    suspend fun send(frame: ByteString)

    /** Close the link and release resources. */
    suspend fun close()
}
