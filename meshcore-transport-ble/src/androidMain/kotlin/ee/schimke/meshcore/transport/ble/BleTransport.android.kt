package ee.schimke.meshcore.transport.ble

import android.bluetooth.le.ScanSettings
import com.juul.kable.ObsoleteKableApi
import com.juul.kable.PeripheralBuilder
import com.juul.kable.ScannerBuilder
import com.juul.kable.logs.Logging

internal actual fun PeripheralBuilder.applyMeshCoreDefaults(autoConnect: Boolean) {
  if (autoConnect) autoConnectIf { true }
  // Negotiate a larger MTU so MeshCore frames (up to 172 bytes) fit in a single BLE
  // notification rather than splitting at the 20-byte ATT MTU 23 payload limit.
  mtu = 185
  logging {
    level = Logging.Level.Warnings
    identifier = "meshcore-ble"
  }
}

@OptIn(ObsoleteKableApi::class)
internal actual fun ScannerBuilder.applyMeshCoreScannerDefaults() {
  scanSettings =
    ScanSettings.Builder()
      .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
      .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
      .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
      .build()
  logging {
    level = Logging.Level.Warnings
    identifier = "meshcore-scan"
  }
}
