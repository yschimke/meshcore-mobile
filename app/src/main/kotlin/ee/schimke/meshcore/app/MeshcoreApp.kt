package ee.schimke.meshcore.app

import android.app.Application
import androidx.work.Configuration
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.android.MetroAppComponentProviders
import dev.zacsweers.metrox.android.MetroApplication
import ee.schimke.meshcore.app.debug.DebugBridge
import ee.schimke.meshcore.app.di.AppGraph
import ee.schimke.meshcore.app.di.AppGraphHolder
import ee.schimke.meshcore.app.widget.WidgetStateBridge
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MeshcoreApp : Application(), AppGraphHolder, MetroApplication, Configuration.Provider {

  override val appGraph: AppGraph by lazy { createGraphFactory<AppGraph.Factory>().create(this) }

  override val appComponentProviders: MetroAppComponentProviders
    get() = appGraph

  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder().setWorkerFactory(appGraph.workerFactory).build()

  override fun onCreate() {
    super.onCreate()
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
