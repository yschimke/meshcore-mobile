@file:OptIn(ExperimentalHorologistApi::class)

package ee.schimke.meshcore.wear

import android.app.Application
import android.content.Context
import android.os.Build
import com.google.android.gms.wearable.Wearable
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.data.WearDataLayerRegistry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.android.MetroAppComponentProviders
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import ee.schimke.meshcore.wear.data.MeshcoreWearClient
import ee.schimke.meshcore.wear.data.PreviewWearClient
import ee.schimke.meshcore.wear.data.WearClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Are we running inside the Robolectric-backed `@Preview` renderer rather than on a real watch?
 * Robolectric stamps its own build fingerprint, which is the only signal available this early — the
 * graph is built before any test or preview hook could inject one.
 */
private val isRenderer: Boolean
  get() = Build.FINGERPRINT == "robolectric"

/**
 * Metro dependency graph for the Wear OS app — the counterpart to the phone's `AppGraph`. Provides
 * the data-layer-backed [WearClient] (or [PreviewWearClient] under Robolectric), Android component
 * factories via [MetroAppComponentProviders], and ViewModel factories via [ViewModelGraph].
 */
@DependencyGraph(AppScope::class)
interface WearAppGraph : ViewModelGraph, MetroAppComponentProviders {

  /** App-lifetime coroutine scope; cancelled on [close]. */
  val applicationScope: CoroutineScope

  val meshClient: WearClient

  fun close() {
    applicationScope.cancel()
  }

  @Binds val Application.bindContext: Context

  @Provides
  @SingleIn(AppScope::class)
  fun provideApplicationScope(): CoroutineScope =
    CoroutineScope(SupervisorJob() + Dispatchers.Default)

  /**
   * The Wearable Data Layer client, or [PreviewWearClient] when running under Robolectric.
   *
   * Play Services' Wearable API does not exist under Robolectric, so the `@Preview` renderer (which
   * composes the real [WearMainActivity]) can only get as far as the first data-layer call before
   * it fails with `ApiException: 17`. That throw lands on this scope, not the caller's, so the UI's
   * own try/catch guards never see it — which is why `wear/activity__WearMainActivity` produced no
   * PNG at all rather than an error frame.
   */
  @Provides
  @SingleIn(AppScope::class)
  fun provideWearClient(
    context: Context,
    applicationScope: CoroutineScope,
  ): WearClient =
    if (isRenderer) {
      PreviewWearClient
    } else {
      MeshcoreWearClient(
        WearDataLayerRegistry(
          dataClient = Wearable.getDataClient(context),
          nodeClient = Wearable.getNodeClient(context),
          messageClient = Wearable.getMessageClient(context),
          capabilityClient = Wearable.getCapabilityClient(context),
          coroutineScope = applicationScope,
        ),
      )
    }

  @DependencyGraph.Factory
  fun interface Factory {
    fun create(@Provides application: Application): WearAppGraph
  }
}
