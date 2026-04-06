package ee.schimke.meshcore.transport.ble

import com.juul.kable.Peripheral
import com.juul.kable.PeripheralBuilder
import com.juul.kable.ScannerBuilder
import com.juul.kable.logs.Logging

internal actual suspend fun requestLargerMtu(peripheral: Peripheral) {
    // JVM BLE (btleplug) doesn't support MTU negotiation
}

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
