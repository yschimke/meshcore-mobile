package ee.schimke.meshcore.app.di

import android.app.Application
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.work.ListenableWorker
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.android.MetroAppComponentProviders
import ee.schimke.meshcore.app.connection.AndroidConnectionSideEffects
import ee.schimke.meshcore.app.connection.AppConnectionController
import ee.schimke.meshcore.app.connection.ConnectionSideEffects
import ee.schimke.meshcore.app.ui.theme.ThemePreferences
import ee.schimke.meshcore.core.manager.MeshCoreManager
import ee.schimke.meshcore.data.MeshcoreDatabase
import ee.schimke.meshcore.data.createMeshcoreDatabase
import ee.schimke.meshcore.data.repository.MeshcoreRepository
import ee.schimke.meshcore.mobile.AndroidUsbPortLister
import ee.schimke.meshcore.mobile.AndroidUsbPortResolver
import ee.schimke.meshcore.session.DefaultTransportFactory
import ee.schimke.meshcore.session.TransportFactory
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Metro dependency graph for the phone application. Provides app-scoped singletons, Android
 * component factories via [MetroAppComponentProviders], and WorkManager worker factories via
 * [MetroWorkerFactory].
 */
@DependencyGraph(AppScope::class)
interface AppGraph : MetroAppComponentProviders {

  @Multibinds(allowEmpty = true)
  val workerFactories: Map<KClass<out ListenableWorker>, () -> AssistedWorkerFactory>

  val workerFactory: MetroWorkerFactory

  /** App-lifetime coroutine scope; cancelled on [close]. */
  val applicationScope: CoroutineScope

  val manager: MeshCoreManager

  val usbPorts: AndroidUsbPortLister

  val transportFactory: TransportFactory

  val database: MeshcoreDatabase

  val repository: MeshcoreRepository

  val themePreferences: ThemePreferences

  val connectionController: AppConnectionController

  /** Tear down app-scoped coroutines. */
  fun close() {
    applicationScope.cancel()
  }

  @Binds val Application.bindContext: Context

  @Provides
  @SingleIn(AppScope::class)
  fun provideApplicationScope(): CoroutineScope =
    CoroutineScope(SupervisorJob() + Dispatchers.Default)

  @Provides
  @SingleIn(AppScope::class)
  fun provideMeshCoreManager(): MeshCoreManager = MeshCoreManager()

  @Provides
  @SingleIn(AppScope::class)
  fun provideUsbPorts(context: Context): AndroidUsbPortLister = AndroidUsbPortLister(context)

  @Provides
  @SingleIn(AppScope::class)
  fun provideTransportFactory(usbPorts: AndroidUsbPortLister): TransportFactory =
    DefaultTransportFactory(AndroidUsbPortResolver(usbPorts))

  @Provides
  @SingleIn(AppScope::class)
  fun provideDatabase(context: Context): MeshcoreDatabase = createMeshcoreDatabase(context)

  @Provides
  @SingleIn(AppScope::class)
  fun provideRepository(database: MeshcoreDatabase): MeshcoreRepository =
    MeshcoreRepository(database)

  @Provides
  @SingleIn(AppScope::class)
  fun provideThemePreferences(context: Context): ThemePreferences = ThemePreferences(context)

  @Provides
  @SingleIn(AppScope::class)
  fun provideConnectionSideEffects(
    context: Context,
    repository: MeshcoreRepository,
    scope: CoroutineScope,
    controller: () -> AppConnectionController,
  ): ConnectionSideEffects =
    AndroidConnectionSideEffects(
      appContext = context,
      repository = repository,
      scope = scope,
      onWarning = { controller().addWarning(it) },
    )

  @Provides
  @SingleIn(AppScope::class)
  fun provideConnectionController(
    manager: MeshCoreManager,
    repository: MeshcoreRepository,
    transportFactory: TransportFactory,
    sideEffects: ConnectionSideEffects,
    scope: CoroutineScope,
  ): AppConnectionController =
    AppConnectionController(
      manager = manager,
      repository = repository,
      transportFactory = transportFactory,
      sideEffects = sideEffects,
      scope = scope,
    )

  @DependencyGraph.Factory
  fun interface Factory {
    fun create(@Provides application: Application): AppGraph
  }
}

/**
 * Implemented by the [android.app.Application] so components that cannot be constructor-injected
 * (such as KSP-generated AppFunction services or debug introspection) can reach the graph.
 */
interface AppGraphHolder {
  val appGraph: AppGraph
}

/** Resolve the [AppGraph] from any [Context] without a global singleton. */
fun Context.appGraph(): AppGraph = (applicationContext as AppGraphHolder).appGraph

/**
 * CompositionLocal carrying the [AppGraph] to composables, provided once at the activity root.
 * Avoids screens reaching into the Application.
 */
val LocalAppGraph =
  staticCompositionLocalOf<AppGraph> {
    error("AppGraph not provided. Wrap content in MeshcoreApp.")
  }
