package ee.schimke.meshcore.core.transport

sealed class TransportState {
    object Disconnected : TransportState()
    object Connecting : TransportState()
    object Connected : TransportState()
    data class Error(val cause: Throwable) : TransportState()
}
