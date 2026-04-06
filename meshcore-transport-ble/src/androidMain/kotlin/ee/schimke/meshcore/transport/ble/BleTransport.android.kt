package ee.schimke.meshcore.transport.ble

import android.bluetooth.le.ScanSettings
import android.util.Log
import com.juul.kable.AndroidPeripheral
import com.juul.kable.ObsoleteKableApi
import com.juul.kable.Peripheral
import com.juul.kable.PeripheralBuilder
import com.juul.kable.ScannerBuilder
import com.juul.kable.logs.Logging

internal actual fun PeripheralBuilder.applyMeshCoreDefaults(autoConnect: Boolean) {
    if (autoConnect) autoConnectIf { true }
    logging {
        level = Logging.Level.Warnings
        identifier = "meshcore-ble"
    }
}

internal actual suspend fun requestLargerMtu(peripheral: Peripheral) {
    val ap = peripheral as? AndroidPeripheral ?: return
    try {
        val mtu = ap.requestMtu(185)
        Log.d("meshcore-ble", "MTU negotiated: $mtu")
    } catch (e: Exception) {
        Log.w("meshcore-ble", "MTU request failed: ${e.message}")
    }
}

@OptIn(ObsoleteKableApi::class)
internal actual fun ScannerBuilder.applyMeshCoreScannerDefaults() {
    scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
        .build()
    logging {
        level = Logging.Level.Warnings
        identifier = "meshcore-scan"
    }
}
