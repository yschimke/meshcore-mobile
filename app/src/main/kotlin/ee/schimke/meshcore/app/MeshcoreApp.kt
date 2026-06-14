package ee.schimke.meshcore.app

import android.app.Application
import ee.schimke.meshcore.app.debug.DebugBridge
import ee.schimke.meshcore.app.di.AppGraph
import ee.schimke.meshcore.app.di.AppGraphHolder
import ee.schimke.meshcore.app.widget.WidgetStateBridge
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MeshcoreApp : Application(), AppGraphHolder {

  override lateinit var appGraph: AppGraph
    private set

  override fun onCreate() {
    super.onCreate()
    appGraph = AppGraph(this)
    DebugBridge.instance?.onAppReady()
    WidgetStateBridge.start(this, appGraph.manager)

    // Reconnect to the favorite device on startup, in a cancellable
    // app-scoped coroutine (not GlobalScope).
    appGraph.applicationScope.launch {
      val favorite = appGraph.repository.observeFavorite().first()
      if (favorite != null) {
        appGraph.connectionController.requestReconnect(favorite)
        // Schedules periodic refresh + presence observation for the favorite.
        appGraph.connectionController.scheduleFavorite(favorite)
      }
    }
  }
}
