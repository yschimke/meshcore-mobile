package ee.schimke.meshcore.transport.ble

import com.juul.kable.PeripheralBuilder
import com.juul.kable.ScannerBuilder
import com.juul.kable.logs.Logging

internal actual fun PeripheralBuilder.applyMeshCoreDefaults(autoConnect: Boolean) {
    logging {
        level = Logging.Level.Events
        identifier = "meshcore-ble"
    }
}

internal actual fun ScannerBuilder.applyMeshCoreScannerDefaults() {
    logging {
        level = Logging.Level.Events
        identifier = "meshcore-scan"
    }
}
