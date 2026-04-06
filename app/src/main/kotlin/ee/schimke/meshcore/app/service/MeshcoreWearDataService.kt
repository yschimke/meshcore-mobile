@file:OptIn(ExperimentalHorologistApi::class)

package ee.schimke.meshcore.app.service

import com.google.android.gms.wearable.Wearable
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.data.WearDataLayerRegistry
import com.google.android.horologist.datalayer.grpc.server.BaseGrpcDataService
import ee.schimke.meshcore.app.MeshcoreApp
import ee.schimke.meshcore.core.client.MeshCoreClient
import ee.schimke.meshcore.core.manager.ManagerState
import ee.schimke.meshcore.grpc.MeshServiceBridge
import ee.schimke.meshcore.grpc.MeshcoreGrpcServiceImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow

/**
 * Phone-side gRPC service exposed over the Wearable Data Layer.
 *
 * Wraps the existing [MeshcoreGrpcServiceImpl] so the Wear OS companion
 * app can call the same 9 RPCs (connection status, contacts, channels,
 * battery, messaging) without any protocol duplication. Horologist's
 * [BaseGrpcDataService] handles the MessageClient transport; we just
 * supply the bridge.
 *
 * This mirrors [MeshcoreBinderService] — same bridge pattern, different
 * transport (Data Layer instead of Android Binder).
 */
class MeshcoreWearDataService : BaseGrpcDataService<MeshcoreGrpcServiceImpl>() {

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
        val app = application as MeshcoreApp
        val bridge = object : MeshServiceBridge {
            override val managerState: StateFlow<ManagerState>
                get() = app.manager.state
            override val client: MeshCoreClient?
                get() = app.manager.client
        }
        return MeshcoreGrpcServiceImpl(bridge)
    }
}
