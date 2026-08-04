@file:OptIn(ExperimentalHorologistApi::class)

package ee.schimke.meshcore.wear

import android.content.Context
import android.os.Build
import com.google.android.gms.wearable.Wearable
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.data.WearDataLayerRegistry
import ee.schimke.meshcore.wear.data.MeshcoreWearClient
import ee.schimke.meshcore.wear.data.PreviewWearClient
import ee.schimke.meshcore.wear.data.WearClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Single, explicit object graph for the Wear app — the counterpart to
 * the phone's `AppGraph`. Constructs the data-layer registry and mesh
 * client once, off a cancellable app-scoped coroutine scope, replacing
 * the `MeshcoreWearApp.get()` service locator.
 */
/**
 * Are we running inside the Robolectric-backed `@Preview` renderer rather than on
 * a real watch? Robolectric stamps its own build fingerprint, which is the only
 * signal available this early — the graph is built from `Application.onCreate`,
 * before any test or preview hook could inject one.
 */
private val isRenderer: Boolean
  get() = Build.FINGERPRINT == "robolectric"

class WearAppGraph(context: Context) {

  /** App-lifetime coroutine scope; cancelled on [close]. */
  val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  /**
   * The Wearable Data Layer, or `null` when there is none to reach.
   *
   * Play Services' Wearable API does not exist under Robolectric, so the
   * `@Preview` renderer (which composes the real [WearMainActivity]) can only get
   * as far as the first data-layer call before it fails with `ApiException: 17`.
   * That throw lands on this scope, not the caller's, so the UI's own try/catch
   * guards never see it — which is why `wear/activity__WearMainActivity` produced
   * no PNG at all rather than an error frame.
   */
  private val registry: WearDataLayerRegistry? =
    if (isRenderer) {
      null
    } else {
      WearDataLayerRegistry(
        dataClient = Wearable.getDataClient(context),
        nodeClient = Wearable.getNodeClient(context),
        messageClient = Wearable.getMessageClient(context),
        capabilityClient = Wearable.getCapabilityClient(context),
        coroutineScope = applicationScope,
      )
    }

  val meshClient: WearClient =
    registry?.let { MeshcoreWearClient(it) } ?: PreviewWearClient

  fun close() {
    applicationScope.cancel()
  }
}

/** Implemented by the Wear [android.app.Application] so any context can reach the graph. */
interface WearGraphHolder {
  val wearGraph: WearAppGraph
}
