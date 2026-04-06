@file:OptIn(ExperimentalHorologistApi::class)

package ee.schimke.meshcore.wear

import android.app.Application
import com.google.android.gms.wearable.Wearable
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.data.WearDataLayerRegistry
import ee.schimke.meshcore.wear.data.MeshcoreWearClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MeshcoreWearApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var registry: WearDataLayerRegistry
        private set

    lateinit var meshClient: MeshcoreWearClient
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        registry = WearDataLayerRegistry(
            dataClient = Wearable.getDataClient(this),
            nodeClient = Wearable.getNodeClient(this),
            messageClient = Wearable.getMessageClient(this),
            capabilityClient = Wearable.getCapabilityClient(this),
            coroutineScope = appScope,
        )

        meshClient = MeshcoreWearClient(registry)
    }

    companion object {
        private lateinit var instance: MeshcoreWearApp
        fun get(): MeshcoreWearApp = instance
    }
}
