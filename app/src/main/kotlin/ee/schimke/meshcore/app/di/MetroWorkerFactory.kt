package ee.schimke.meshcore.app.di

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.MapKey
import dev.zacsweers.metro.SingleIn
import kotlin.reflect.KClass

@MapKey
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class WorkerKey(val value: KClass<out ListenableWorker>)

interface AssistedWorkerFactory {
  fun create(context: Context, params: WorkerParameters): ListenableWorker
}

@SingleIn(AppScope::class)
@Inject
class MetroWorkerFactory(
  private val workerFactories: Map<KClass<out ListenableWorker>, () -> AssistedWorkerFactory>,
) : WorkerFactory() {
  override fun createWorker(
    appContext: Context,
    workerClassName: String,
    workerParameters: WorkerParameters,
  ): ListenableWorker? {
    val clazz = runCatching { Class.forName(workerClassName).kotlin }.getOrNull() ?: return null
    val factoryProvider = workerFactories[clazz] ?: return null
    return factoryProvider().create(appContext, workerParameters)
  }
}
