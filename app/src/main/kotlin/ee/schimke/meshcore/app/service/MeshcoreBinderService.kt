package ee.schimke.meshcore.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.android.ServiceKey
import ee.schimke.meshcore.app.connection.AppConnectionController
import ee.schimke.meshcore.core.client.MeshCoreClient
import ee.schimke.meshcore.core.manager.ManagerState
import ee.schimke.meshcore.core.manager.MeshCoreManager
import ee.schimke.meshcore.data.repository.MeshcoreRepository
import ee.schimke.meshcore.grpc.MeshServiceBridge
import ee.schimke.meshcore.grpc.MeshcoreGrpcServiceImpl
import ee.schimke.meshcore.grpc.MeshcoreServiceGrpc
import io.grpc.Server
import io.grpc.binder.AndroidComponentAddress
import io.grpc.binder.BinderServerBuilder
import io.grpc.binder.IBinderReceiver
import io.grpc.binder.SecurityPolicies
import io.grpc.binder.ServerSecurityPolicy
import kotlin.time.Instant
import kotlinx.coroutines.flow.StateFlow

/**
 * Android bound service that exposes MeshCore functionality to other apps via gRPC binder
 * transport. Client apps holding the [PERMISSION] (signature-level) can bind and make gRPC calls to
 * query device state, send messages, and subscribe to incoming messages.
 */
@ContributesIntoMap(AppScope::class, binding<Service>())
@ServiceKey
@Inject
class MeshcoreBinderService(
  private val manager: MeshCoreManager,
  private val connectionController: AppConnectionController,
  private val repository: MeshcoreRepository,
) : LifecycleService() {

  private var server: Server? = null
  private val binderReceiver = IBinderReceiver()

  override fun onCreate() {
    super.onCreate()

    val bridge =
      object : MeshServiceBridge {
        override val managerState: StateFlow<ManagerState>
          get() = manager.state

        override val client: MeshCoreClient?
          get() = manager.client

        override suspend fun persistSentDm(
          contactKeyHex: String,
          text: String,
          timestamp: Instant,
          ackHash: Int?,
        ) {
          val deviceId = connectionController.connectedDeviceId.value ?: return
          repository.insertSentDm(deviceId, contactKeyHex, text, timestamp, ackHash)
        }
      }

    val address = AndroidComponentAddress.forContext(this)
    val securityPolicy =
      ServerSecurityPolicy.newBuilder()
        .servicePolicy(
          MeshcoreServiceGrpc.SERVICE_NAME,
          SecurityPolicies.hasPermissions(
            packageManager,
            com.google.common.collect.ImmutableSet.of(PERMISSION),
          ),
        )
        .build()

    server =
      BinderServerBuilder.forAddress(address, binderReceiver)
        .securityPolicy(securityPolicy)
        .addService(MeshcoreGrpcServiceImpl(bridge))
        .build()
        .start()
  }

  override fun onBind(intent: Intent): IBinder? {
    super.onBind(intent)
    return binderReceiver.get()
  }

  override fun onDestroy() {
    server?.shutdownNow()
    super.onDestroy()
  }

  companion object {
    const val PERMISSION = "ee.schimke.meshcore.permission.MESH_SERVICE"
  }
}
