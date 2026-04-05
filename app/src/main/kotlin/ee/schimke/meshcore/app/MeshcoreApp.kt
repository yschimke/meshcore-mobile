package ee.schimke.meshcore.app

import android.app.Application
import dev.zacsweers.metro.createGraphFactory
import ee.schimke.meshcore.core.manager.MeshCoreManager
import ee.schimke.meshcore.mobile.AndroidUsbPortLister
import ee.schimke.meshcore.mobile.MobileGraph
import ee.schimke.meshcore.app.connection.AppConnectionController
import ee.schimke.meshcore.app.data.SavedDevicesRepository
import ee.schimke.meshcore.app.ui.theme.ThemePreferences
import ee.schimke.meshcore.app.widget.WidgetStateBridge
import kotlinx.coroutines.launch

class MeshcoreApp : Application() {
    lateinit var graph: MobileGraph
        private set

    val manager: MeshCoreManager get() = graph.manager
    val usbPorts: AndroidUsbPortLister get() = graph.usbPorts

    val themePreferences: ThemePreferences by lazy { ThemePreferences(this) }
    val savedDevices: SavedDevicesRepository by lazy { SavedDevicesRepository(this) }
    val connectionController: AppConnectionController by lazy {
        AppConnectionController(manager = manager, savedDevices = savedDevices)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        graph = createGraphFactory<MobileGraph.Factory>().create(this)
        WidgetStateBridge.start(this, manager)
        // Touch connectionController so it starts observing manager.state
        // early, then auto-connect a favorite device if one is saved.
        // A GlobalScope launch is fine here because the work finishes
        // promptly (snapshot + requestReconnect is non-suspend) and the
        // controller owns its own long-lived scope for the connect.
        @Suppress("OPT_IN_USAGE")
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val favorite = savedDevices.snapshot().firstOrNull { it.favorite }
            if (favorite != null) connectionController.requestReconnect(favorite)
        }
    }

    companion object {
        @Volatile private var instance: MeshcoreApp? = null
        fun get(): MeshcoreApp =
            instance ?: error("MeshcoreApp not initialized")
    }
}
