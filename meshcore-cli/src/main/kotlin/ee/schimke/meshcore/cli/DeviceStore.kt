package ee.schimke.meshcore.cli

import ee.schimke.meshcore.data.createMeshcoreDatabase
import ee.schimke.meshcore.data.repository.MeshcoreRepository
import ee.schimke.meshcore.data.repository.SavedTransport
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * CLI device store backed by Room (same DB as the Android app on JVM).
 * Stored at `~/.meshcore/meshcore.db`.
 */
class DeviceStore {
    val repository: MeshcoreRepository by lazy {
        MeshcoreRepository(createMeshcoreDatabase())
    }

    /** Returns the favorite device's TCP host and port, or null. */
    fun getFavorite(): Pair<String, Int>? = runBlocking {
        val fav = repository.observeFavorite().first() ?: return@runBlocking null
        val tcp = fav.transport as? SavedTransport.Tcp ?: return@runBlocking null
        tcp.host to tcp.port
    }
}
