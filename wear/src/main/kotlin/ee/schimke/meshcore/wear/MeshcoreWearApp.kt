package ee.schimke.meshcore.wear

import android.app.Application
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.android.MetroAppComponentProviders
import dev.zacsweers.metrox.android.MetroApplication

class MeshcoreWearApp : Application(), MetroApplication {

  val wearGraph: WearAppGraph by lazy { createGraphFactory<WearAppGraph.Factory>().create(this) }

  override val appComponentProviders: MetroAppComponentProviders
    get() = wearGraph
}
