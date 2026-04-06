package ee.schimke.meshcore.app.ble

import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.net.MacAddress
import android.util.Log
import ee.schimke.meshcore.data.repository.SavedDevice
import ee.schimke.meshcore.data.repository.SavedTransport
import java.util.regex.Pattern

private const val TAG = "MeshPresence"

/**
 * Manages [CompanionDeviceManager] device presence observation for the
 * favorite MeshCore BLE device. When the device appears in range, the
 * system delivers [CompanionDeviceManager.ACTION_COMPANION_DEVICE_APPEARING]
 * to [DevicePresenceReceiver].
 */
object DevicePresenceManager {

    /**
     * Start observing presence for a BLE favorite device.
     * If the device isn't already associated via CompanionDeviceManager,
     * we just start observing its MAC address.
     */
    fun startObserving(context: Context, device: SavedDevice) {
        val ble = device.transport as? SavedTransport.Ble ?: return
        val mac = ble.identifier
        val cdm = context.getSystemService(CompanionDeviceManager::class.java) ?: return

        // Check if already associated
        val associated = cdm.myAssociations.any { it.deviceMacAddress?.toString() == mac }
        if (!associated) {
            Log.d(TAG, "Device $mac not yet associated — skipping presence observation")
            // CompanionDeviceManager requires explicit user-approved association
            // before startObservingDevicePresence can be called. The association
            // should happen during the first manual connect flow.
            return
        }

        try {
            cdm.startObservingDevicePresence(mac)
            Log.d(TAG, "Started observing presence for $mac")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start presence observation: ${e.message}")
        }
    }

    fun stopObserving(context: Context, device: SavedDevice) {
        val ble = device.transport as? SavedTransport.Ble ?: return
        val cdm = context.getSystemService(CompanionDeviceManager::class.java) ?: return
        try {
            cdm.stopObservingDevicePresence(ble.identifier)
            Log.d(TAG, "Stopped observing presence for ${ble.identifier}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop presence observation: ${e.message}")
        }
    }
}
