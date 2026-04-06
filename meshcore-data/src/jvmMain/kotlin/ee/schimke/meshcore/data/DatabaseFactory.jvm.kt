package ee.schimke.meshcore.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

actual fun createMeshcoreDatabase(platformContext: Any?): MeshcoreDatabase {
    val dbFile = File(System.getProperty("user.home"), ".meshcore/meshcore.db")
    dbFile.parentFile?.mkdirs()
    return Room.databaseBuilder<MeshcoreDatabase>(
        name = dbFile.absolutePath,
    )
        .setDriver(BundledSQLiteDriver())
        .build()
}
