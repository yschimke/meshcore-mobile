package ee.schimke.meshcore.grpc

import ee.schimke.meshcore.core.client.MeshCoreClient
import ee.schimke.meshcore.core.manager.ManagerState
import kotlinx.coroutines.flow.StateFlow

/**
 * Bridge interface that decouples the gRPC service implementation from
 * app-level DI. The app module's LifecycleService implements this by
 * delegating to MeshcoreApp's MeshCoreManager.
 */
interface MeshServiceBridge {
    val managerState: StateFlow<ManagerState>
    val client: MeshCoreClient?
}
