package ee.schimke.meshcore.session

import com.juul.kable.toIdentifier
import ee.schimke.meshcore.core.transport.Transport
import ee.schimke.meshcore.transport.ble.BleTransport
import ee.schimke.meshcore.transport.tcp.TcpTransport

internal actual fun createTcpTransport(host: String, port: Int): Transport =
  TcpTransport(host, port)

internal actual fun createBleTransport(identifier: String): Transport =
  BleTransport.fromIdentifier(identifier.toIdentifier())
