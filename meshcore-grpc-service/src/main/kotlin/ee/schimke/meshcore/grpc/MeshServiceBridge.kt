package ee.schimke.meshcore.grpc

import ee.schimke.meshcore.core.client.MeshCoreClient
import ee.schimke.meshcore.core.manager.ManagerState
import kotlin.time.Instant
import kotlinx.coroutines.flow.StateFlow

/**
 * Bridge interface that decouples the gRPC service implementation from app-level DI. The app
 * module's LifecycleService implements this by delegating to MeshcoreApp's MeshCoreManager.
 */
interface MeshServiceBridge {
  val managerState: StateFlow<ManagerState>
  val client: MeshCoreClient?

  /** Persist a sent DM to the local message store. */
  suspend fun persistSentDm(
    contactKeyHex: String,
    text: String,
    timestamp: Instant,
    ackHash: Int?,
  ) {}
}
