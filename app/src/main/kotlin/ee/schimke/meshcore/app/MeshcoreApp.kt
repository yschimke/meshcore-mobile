package ee.schimke.meshcore.app

import android.app.Application
import dev.zacsweers.metro.createGraphFactory
import ee.schimke.meshcore.app.ble.DevicePresenceManager
import ee.schimke.meshcore.app.connection.AppConnectionController
import ee.schimke.meshcore.app.ui.theme.ThemePreferences
import ee.schimke.meshcore.app.widget.PeriodicRefreshWorker
import ee.schimke.meshcore.app.widget.WidgetStateBridge
import ee.schimke.meshcore.core.manager.MeshCoreManager
import ee.schimke.meshcore.data.MeshcoreDatabase
import ee.schimke.meshcore.data.createMeshcoreDatabase
import ee.schimke.meshcore.data.repository.MeshcoreRepository
import ee.schimke.meshcore.mobile.AndroidUsbPortLister
import ee.schimke.meshcore.mobile.MobileGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MeshcoreApp : Application() {
    lateinit var graph: MobileGraph
        private set

    val manager: MeshCoreManager get() = graph.manager
    val usbPorts: AndroidUsbPortLister get() = graph.usbPorts

    val themePreferences: ThemePreferences by lazy { ThemePreferences(this) }
    val database: MeshcoreDatabase by lazy { createMeshcoreDatabase(this) }
    val repository: MeshcoreRepository by lazy { MeshcoreRepository(database) }
    val connectionController: AppConnectionController by lazy {
        AppConnectionController(manager = manager, repository = repository, appContext = this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        graph = createGraphFactory<MobileGraph.Factory>().create(this)
        WidgetStateBridge.start(this, manager)
        @Suppress("OPT_IN_USAGE")
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val favorite = repository.observeFavorite().first()
            if (favorite != null) {
                connectionController.requestReconnect(favorite)
                PeriodicRefreshWorker.scheduleIfFavoriteExists(this@MeshcoreApp)
                DevicePresenceManager.startObserving(this@MeshcoreApp, favorite)
            }
        }
    }

    companion object {
        @Volatile private var instance: MeshcoreApp? = null
        fun get(): MeshcoreApp =
            instance ?: error("MeshcoreApp not initialized")
    }
}
