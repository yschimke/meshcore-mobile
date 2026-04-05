package ee.schimke.meshcore.core.protocol

import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.isEmpty
import kotlinx.io.readByteString
import kotlinx.io.readShortLe
import kotlinx.io.write

/**
 * Shared [kotlinx.io][kotlinx.io.Buffer] helpers used by frame builders
 * and parsers. MeshCore encodes multi-byte integers as little-endian,
 * and strings as null-terminated UTF-8 (padded to fixed widths in some
 * frame layouts).
 */
internal object BufferExt {

    /** Build a [ByteString] by writing into a fresh [Buffer]. */
    inline fun buildByteString(block: Buffer.() -> Unit): ByteString {
        val buf = Buffer()
        buf.block()
        return buf.readByteString()
    }

    /** Read a little-endian unsigned 16-bit integer as an Int. */
    fun Source.readU16Le(): Int = readShortLe().toInt() and 0xFFFF

    /**
     * Read a null-terminated UTF-8 string of at most [maxLen] bytes, consuming
     * exactly [maxLen] bytes from the source (padding is skipped).
     */
    fun Source.readCStringFixed(maxLen: Int): String {
        val bytes = readByteString(maxLen)
        val raw = bytes.toByteArray()
        var end = 0
        while (end < raw.size && raw[end].toInt() != 0) end++
        return raw.decodeToString(endIndex = end)
    }

    /** Read a null-terminated UTF-8 string that runs to the end of the source. */
    fun Source.readCStringRemaining(): String {
        val bs = readByteString()
        if (bs.isEmpty()) return ""
        val raw = bs.toByteArray()
        var end = 0
        while (end < raw.size && raw[end].toInt() != 0) end++
        return raw.decodeToString(endIndex = end)
    }

    /** Write a UTF-8 string followed by a trailing null terminator. */
    fun Buffer.writeCString(value: String) {
        write(value.encodeToByteArray())
        writeByte(0)
    }
}
