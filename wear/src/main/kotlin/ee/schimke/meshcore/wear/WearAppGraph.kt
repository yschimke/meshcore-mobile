@file:OptIn(ExperimentalHorologistApi::class)

package ee.schimke.meshcore.wear

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.data.WearDataLayerRegistry
import ee.schimke.meshcore.wear.data.MeshcoreWearClient
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
class WearAppGraph(context: Context) {

  /** App-lifetime coroutine scope; cancelled on [close]. */
  val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  val registry: WearDataLayerRegistry =
    WearDataLayerRegistry(
      dataClient = Wearable.getDataClient(context),
      nodeClient = Wearable.getNodeClient(context),
      messageClient = Wearable.getMessageClient(context),
      capabilityClient = Wearable.getCapabilityClient(context),
      coroutineScope = applicationScope,
    )

  val meshClient: MeshcoreWearClient = MeshcoreWearClient(registry)

  fun close() {
    applicationScope.cancel()
  }
}

/** Implemented by the Wear [android.app.Application] so any context can reach the graph. */
interface WearGraphHolder {
  val wearGraph: WearAppGraph
}
