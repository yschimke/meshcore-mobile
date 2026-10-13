package ee.schimke.meshcore.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ee.schimke.composeai.discovery.ComponentRecordFile
import ee.schimke.composeai.uibuilder.protocol.UiBuilderRendererSurfaceModeV2
import ee.schimke.composeai.uibuilder.renderer.sdk.*
import ee.schimke.meshcore.components.ui.theme.MeshcoreTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

fun main() {
  val catalog =
    MeshcoreCatalog(Json.decodeFromString(ComponentRecordFile.serializer(), RUNTIME_RECORD))
  val foundation = canvasAdapterRegistry {
    register("layout/column") { Column(modifier) { Slot("children") } }
    register("m3/text") { Text(string("text"), modifier, onTextLayout = ::recordTextLayout) }
  }
  val registry = catalog.registry() + foundation
  val actions = UiBuilderSemanticActionController()
  startCatalogRenderer(actions) { document, surface, session, inspection ->
    val mode =
      if (surface.mode == UiBuilderRendererSurfaceModeV2.AUTHORING_UNROLLED)
        CanvasMode.AuthoringUnrolled
      else CanvasMode.Device
    val density = Density(surface.density)
    val dark = document.environment["theme"]?.jsonPrimitive?.contentOrNull == "dark"
    CompositionLocalProvider(LocalDensity provides density) {
      MeshcoreTheme(darkTheme = dark) {
        Surface(Modifier.requiredSize(surface.widthDp.dp, surface.heightDp.dp)) {
          CanvasDocumentHost(
            document = document,
            adapterIds = emptyMap(),
            adapterMappings = emptyMap(),
            mode = mode,
            density = density,
            modifier = Modifier.fillMaxSize(),
            renderSessionId = session,
            runtimeActionController = actions,
            onInspectionSnapshot = inspection,
          ) { entry, rootModifier ->
            RenderCanvasNode(
              entry = entry,
              registry = registry,
              modifier = rootModifier,
              applyModifier = { current, value ->
                current.applyCanvasModifier(
                  value,
                  mode,
                  resolveColor = { token ->
                    when (token) {
                      "primary" -> MaterialTheme.colorScheme.primary
                      "onPrimary" -> MaterialTheme.colorScheme.onPrimary
                      "background" -> MaterialTheme.colorScheme.background
                      "onBackground" -> MaterialTheme.colorScheme.onBackground
                      "surface" -> MaterialTheme.colorScheme.surface
                      "onSurface" -> MaterialTheme.colorScheme.onSurface
                      else -> Color.Unspecified
                    }
                  },
                  resolveShape = { RectangleShape },
                  unrolledHorizontally = unrolledHorizontally,
                )
              },
              missingComponent = { label, next -> Text("Missing $label", next) },
            ) {
              Text("Unsupported ${node.componentId}", prepared.modifier)
            }
          }
        }
      }
    }
  }
}
