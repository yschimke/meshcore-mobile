package ee.schimke.meshcore.mobile.permissions

import android.Manifest
import android.os.Build

object BlePermissions {
    /** Runtime permissions the host app must request before calling BLE APIs. */
    val required: Array<String>
        get() =
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
}
