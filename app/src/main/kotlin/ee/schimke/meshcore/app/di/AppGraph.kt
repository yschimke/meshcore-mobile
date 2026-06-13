package ee.schimke.meshcore.app.di

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import ee.schimke.meshcore.app.connection.AndroidConnectionSideEffects
import ee.schimke.meshcore.app.connection.AppConnectionController
import ee.schimke.meshcore.app.ui.theme.ThemePreferences
import ee.schimke.meshcore.core.manager.MeshCoreManager
import ee.schimke.meshcore.data.MeshcoreDatabase
import ee.schimke.meshcore.data.createMeshcoreDatabase
import ee.schimke.meshcore.data.repository.MeshcoreRepository
import ee.schimke.meshcore.mobile.AndroidUsbPortLister
import ee.schimke.meshcore.mobile.AndroidUsbPortResolver
import ee.schimke.meshcore.session.DefaultTransportFactory
import ee.schimke.meshcore.session.TransportFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Single, explicit application object graph. Every app-scoped singleton
 * is constructed here and handed out via properties, replacing the old
 * `MeshcoreApp.get()` service locator and the half-finished Metro graph.
 *
 * It is a plain class so dependencies are obvious, ordering is explicit,
 * and individual collaborators (repository, controller) can be built in
 * tests without standing up an [android.app.Application].
 */
class AppGraph(private val appContext: Context) {

  /** App-lifetime coroutine scope; cancelled on [close]. */
  val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  val manager: MeshCoreManager = MeshCoreManager()

  val usbPorts: AndroidUsbPortLister = AndroidUsbPortLister(appContext)

  val transportFactory: TransportFactory =
    DefaultTransportFactory(AndroidUsbPortResolver(usbPorts))

  val database: MeshcoreDatabase by lazy { createMeshcoreDatabase(appContext) }

  val repository: MeshcoreRepository by lazy { MeshcoreRepository(database) }

  val themePreferences: ThemePreferences by lazy { ThemePreferences(appContext) }

  val connectionController: AppConnectionController by lazy { buildController() }

  private fun buildController(): AppConnectionController {
    lateinit var controller: AppConnectionController
    val sideEffects =
      AndroidConnectionSideEffects(
        appContext = appContext,
        repository = repository,
        scope = applicationScope,
        onWarning = { controller.addWarning(it) },
      )
    controller =
      AppConnectionController(
        manager = manager,
        repository = repository,
        transportFactory = transportFactory,
        sideEffects = sideEffects,
        scope = applicationScope,
      )
    return controller
  }

  /** Tear down app-scoped coroutines. */
  fun close() {
    applicationScope.cancel()
  }
}

/** Implemented by the [android.app.Application] so any [Context] can reach the graph. */
interface AppGraphHolder {
  val appGraph: AppGraph
}

/** Resolve the [AppGraph] from any [Context] without a global singleton. */
fun Context.appGraph(): AppGraph = (applicationContext as AppGraphHolder).appGraph

/**
 * CompositionLocal carrying the [AppGraph] to composables, provided once
 * at the activity root. Avoids screens reaching into the Application.
 */
val LocalAppGraph =
  staticCompositionLocalOf<AppGraph> { error("AppGraph not provided. Wrap content in MeshcoreApp.") }
