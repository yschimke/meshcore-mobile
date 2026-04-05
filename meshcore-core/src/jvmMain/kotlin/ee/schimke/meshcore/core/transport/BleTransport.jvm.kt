package ee.schimke.meshcore.core.transport

import com.juul.kable.PeripheralBuilder
import com.juul.kable.ScannerBuilder
import com.juul.kable.logs.Logging

internal actual fun PeripheralBuilder.applyMeshCoreDefaults(autoConnect: Boolean) {
    // Desktop JVM path (rarely used – BLE on JVM is limited); honour
    // logging but the autoConnect semantics don't apply outside Android.
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
