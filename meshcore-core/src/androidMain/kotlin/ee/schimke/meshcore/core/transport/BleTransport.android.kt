package ee.schimke.meshcore.core.transport

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
    // Default Kable/Android scan mode is SCAN_MODE_LOW_POWER which
    // reports adverts at a trickle. Bump to LOW_LATENCY for a
    // responsive scanner screen; combined with CALLBACK_TYPE_ALL_MATCHES
    // + MATCH_MODE_AGGRESSIVE this surfaces devices within ~1s.
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
