package ee.schimke.meshcore.wear

import android.app.Application

class MeshcoreWearApp : Application(), WearGraphHolder {

  override lateinit var wearGraph: WearAppGraph
    private set

  override fun onCreate() {
    super.onCreate()
    wearGraph = WearAppGraph(this)
  }
}
