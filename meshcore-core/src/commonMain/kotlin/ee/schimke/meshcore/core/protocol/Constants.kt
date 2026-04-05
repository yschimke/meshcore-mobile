package ee.schimke.meshcore.core.protocol

object MeshCoreConstants {
    const val PUB_KEY_SIZE = 32
    const val PUB_KEY_PREFIX_SIZE = 6
    const val MAX_PATH_SIZE = 64
    const val MAX_NAME_SIZE = 32
    const val MAX_FRAME_SIZE = 172
    const val MAX_TEXT_PAYLOAD_BYTES = 160
    const val APP_PROTOCOL_VERSION = 3

    // Stream framing markers (USB CDC-ACM + TCP)
    const val STREAM_TX_START: Byte = 0x3C
    const val STREAM_RX_START: Byte = 0x3E
    const val STREAM_HEADER_LEN = 3

    // Nordic UART Service (BLE companion)
    const val BLE_SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
    const val BLE_RX_CHAR_UUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
    const val BLE_TX_CHAR_UUID = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"

    val BLE_NAME_PREFIXES = listOf("MeshCore-", "Whisper-", "WisCore-", "HT-")
}
