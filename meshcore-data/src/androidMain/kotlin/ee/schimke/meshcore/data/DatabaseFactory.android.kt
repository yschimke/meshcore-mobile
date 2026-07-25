package ee.schimke.meshcore.data

import android.content.Context
import android.os.Build
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

actual fun createMeshcoreDatabase(platformContext: Any?): MeshcoreDatabase {
    val context = platformContext as Context
    return Room.databaseBuilder<MeshcoreDatabase>(
        context = context.applicationContext,
        name = context.getDatabasePath("meshcore.db").absolutePath,
    )
        .apply {
            // BundledSQLiteDriver's Android artifact loads ABI-specific natives via
            // System.loadLibrary, which cannot resolve on a host JVM — Robolectric
            // (unit tests, compose-preview activity/tour renders) instead ships
            // natives for the framework SQLite that Room uses when no driver is set.
            if (Build.FINGERPRINT != "robolectric") setDriver(BundledSQLiteDriver())
        }
        .build()
}
