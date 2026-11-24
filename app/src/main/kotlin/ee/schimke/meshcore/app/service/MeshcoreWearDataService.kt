@file:OptIn(ExperimentalHorologistApi::class)

package ee.schimke.meshcore.app.service

import android.app.Service
import com.google.android.gms.wearable.Wearable
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.data.WearDataLayerRegistry
import com.google.android.horologist.datalayer.grpc.server.BaseGrpcDataService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.android.ServiceKey
import ee.schimke.meshcore.core.client.MeshCoreClient
import ee.schimke.meshcore.core.manager.ManagerState
import ee.schimke.meshcore.core.manager.MeshCoreManager
import ee.schimke.meshcore.grpc.MeshServiceBridge
import ee.schimke.meshcore.grpc.MeshcoreGrpcServiceImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow

/**
 * Phone-side gRPC service exposed over the Wearable Data Layer.
 *
 * Wraps the existing [MeshcoreGrpcServiceImpl] so the Wear OS companion app can call the same 9
 * RPCs (connection status, contacts, channels, battery, messaging) without any protocol
 * duplication. Horologist's [BaseGrpcDataService] handles the MessageClient transport; we just
 * supply the bridge.
 *
 * This mirrors [MeshcoreBinderService] — same bridge pattern, different transport (Data Layer
 * instead of Android Binder).
 */
@ContributesIntoMap(AppScope::class, binding<Service>())
@ServiceKey
@Inject
class MeshcoreWearDataService(
  private val manager: MeshCoreManager,
) : BaseGrpcDataService<MeshcoreGrpcServiceImpl>() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  override val registry: WearDataLayerRegistry by lazy {
    WearDataLayerRegistry(
      dataClient = Wearable.getDataClient(this),
      nodeClient = Wearable.getNodeClient(this),
      messageClient = Wearable.getMessageClient(this),
      capabilityClient = Wearable.getCapabilityClient(this),
      coroutineScope = serviceScope,
    )
  }

  override fun buildService(): MeshcoreGrpcServiceImpl {
    val bridge =
      object : MeshServiceBridge {
        override val managerState: StateFlow<ManagerState>
          get() = manager.state

        override val client: MeshCoreClient?
          get() = manager.client
      }
    return MeshcoreGrpcServiceImpl(bridge)
  }

  override fun onDestroy() {
    serviceScope.cancel()
    super.onDestroy()
  }
}
