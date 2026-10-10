package ee.schimke.meshcore.catalog

import ee.schimke.composeai.discovery.ComponentRecordFile
import ee.schimke.composeai.uibuilder.renderer.sdk.CanvasAdapterRegistry
import ee.schimke.composeai.uibuilder.renderer.sdk.JvmCanvasAdapterProvider

/** Executable entry point used by an external JVM host, without app discovery or startup. */
class MeshcoreCanvasAdapterProvider : JvmCanvasAdapterProvider {
  override val catalogId: String = "meshcore-mobile"

  override fun registry(records: ComponentRecordFile): CanvasAdapterRegistry =
    MeshcoreCatalog(records).registry()
}
