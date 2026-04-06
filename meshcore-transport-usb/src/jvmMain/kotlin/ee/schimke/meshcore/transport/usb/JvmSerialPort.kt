package ee.schimke.meshcore.transport.usb

import com.fazecast.jSerialComm.SerialPort
import dev.mcarr.usb.interfaces.ISerialPortWrapper
import ee.schimke.meshcore.transport.usb.JvmSerialPort.Companion.listPorts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout

/**
 * JVM implementation of [ISerialPortWrapper] backed by jSerialComm.
 * Use [listPorts] to enumerate available serial ports.
 */
class JvmSerialPort(
    private val port: SerialPort,
) : ISerialPortWrapper {

    override var defaultReadTimeout: Long = 1000L
    override var defaultWriteTimeout: Long = 1000L
    override var vendorId: Int = port.vendorID
    override var productId: Int = port.productID

    val portName: String get() = port.systemPortName
    val descriptivePortName: String get() = port.descriptivePortName

    override suspend fun <T> use(callback: suspend (ISerialPortWrapper) -> T): T {
        open()
        return try { callback(this) } finally { close() }
    }

    override fun setDefaultTimeouts(readTimeout: Long, writeTimeout: Long) {
        defaultReadTimeout = readTimeout
        defaultWriteTimeout = writeTimeout
    }

    override suspend fun read(numBytes: Int): ByteArray = read(numBytes, defaultReadTimeout)

    override suspend fun read(numBytes: Int, timeout: Long): ByteArray = withTimeout(timeout) {
        val buf = ByteArray(numBytes)
        val n = port.readBytes(buf, numBytes)
        if (n <= 0) ByteArray(0) else buf.copyOf(n)
    }

    override fun read(numBytes: Int, scope: CoroutineScope): Deferred<ByteArray> =
        scope.async { read(numBytes) }

    override fun read(numBytes: Int, timeout: Long, scope: CoroutineScope): Deferred<ByteArray> =
        scope.async { read(numBytes, timeout) }

    override suspend fun write(bytes: ByteArray): Int = write(bytes, defaultWriteTimeout)

    override suspend fun write(bytes: ByteArray, timeout: Long): Int = withTimeout(timeout) {
        port.writeBytes(bytes, bytes.size)
    }

    override fun write(bytes: ByteArray, scope: CoroutineScope): Deferred<Int> =
        scope.async { write(bytes) }

    override fun write(bytes: ByteArray, timeout: Long, scope: CoroutineScope): Deferred<Int> =
        scope.async { write(bytes, timeout) }

    override fun open() {
        port.setComPortTimeouts(
            SerialPort.TIMEOUT_READ_BLOCKING or SerialPort.TIMEOUT_WRITE_BLOCKING,
            250, 1000,
        )
        if (!port.openPort()) error("Failed to open ${port.systemPortName}")
    }

    override fun close() {
        port.closePort()
    }

    override fun isOpen(): Boolean = port.isOpen

    override fun setBaudRate(rate: Int) {
        port.setBaudRate(rate)
    }

    override fun bytesAvailable(): Int = port.bytesAvailable()

    companion object {
        /** List all available serial ports on this JVM host. */
        fun listPorts(): List<JvmSerialPort> =
            SerialPort.getCommPorts().map { JvmSerialPort(it) }
    }
}
