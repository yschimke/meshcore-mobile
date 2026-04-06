package ee.schimke.meshcore.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

actual fun createMeshcoreDatabase(platformContext: Any?): MeshcoreDatabase {
    val context = platformContext as Context
    return Room.databaseBuilder<MeshcoreDatabase>(
        context = context.applicationContext,
        name = context.getDatabasePath("meshcore.db").absolutePath,
    )
        .setDriver(BundledSQLiteDriver())
        .build()
}
