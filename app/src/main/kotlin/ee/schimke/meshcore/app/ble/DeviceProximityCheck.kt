package ee.schimke.meshcore.app.ble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import ee.schimke.meshcore.data.repository.SavedDevice
import ee.schimke.meshcore.data.repository.SavedTransport
import ee.schimke.meshcore.transport.ble.BleScanner
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "MeshProximity"

/**
 * Quick check whether a BLE device is nearby before attempting a
 * full connection. Used by background workers to avoid wasting
 * battery on connections that will time out.
 *
 * For TCP devices, always returns true (no proximity check possible).
 */
object DeviceProximityCheck {

    /**
     * Returns true if the device is likely reachable:
     * - TCP devices: always true
     * - BLE devices: runs a short scan and checks if the MAC appears
     */
    suspend fun isNearby(context: Context, device: SavedDevice, scanTimeoutMs: Long = 5_000): Boolean {
        val ble = device.transport as? SavedTransport.Ble ?: return true // TCP/USB — assume reachable

        // Check bluetooth is enabled
        val btManager = context.getSystemService(BluetoothManager::class.java)
        val adapter = btManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            Log.d(TAG, "Bluetooth disabled, skipping")
            return false
        }

        // Check scan permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "BLUETOOTH_SCAN not granted, skipping proximity check")
            return true // Can't scan — optimistically assume nearby
        }

        Log.d(TAG, "Scanning for ${ble.identifier} (${scanTimeoutMs}ms)...")
        val scanner = BleScanner()
        val found = withTimeoutOrNull(scanTimeoutMs) {
            scanner.advertisements.firstOrNull { adv ->
                adv.identifier == ble.identifier
            }
        }

        val nearby = found != null
        Log.d(TAG, if (nearby) "Device found nearby" else "Device not found")
        return nearby
    }
}
