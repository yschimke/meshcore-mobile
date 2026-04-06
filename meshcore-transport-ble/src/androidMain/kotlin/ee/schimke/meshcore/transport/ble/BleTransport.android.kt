package ee.schimke.meshcore.transport.ble

import android.bluetooth.le.ScanSettings
import com.juul.kable.ObsoleteKableApi
import com.juul.kable.PeripheralBuilder
import com.juul.kable.ScannerBuilder
import com.juul.kable.logs.Logging

internal actual fun PeripheralBuilder.applyMeshCoreDefaults(autoConnect: Boolean) {
    if (autoConnect) autoConnectIf { true }
    logging {
        level = Logging.Level.Events
        identifier = "meshcore-ble"
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
        level = Logging.Level.Events
        identifier = "meshcore-scan"
    }
}
