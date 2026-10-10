package ee.schimke.meshcore.catalog

import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import ee.schimke.composeai.uibuilder.export.UiBuilderNode
import ee.schimke.composeai.uibuilder.renderer.sdk.*
import ee.schimke.meshcore.components.ui.ChatInput
import ee.schimke.meshcore.components.ui.theme.MeshcoreTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*
import kotlinx.serialization.json.*

@OptIn(ExperimentalTestApi::class)
class CatalogRenderTest {
  @Test
  fun `native ChatInput and typed catalog draw the same pixels then dispatch real edits and send`() =
    runDesktopComposeUiTest(width = 500, height = 180) {
      val catalog = MeshcoreCatalog(discoverCatalogComponents())
      val adapter = requireNotNull(catalog.registry()[catalog.input.id])
      var adapted by mutableStateOf(false)
      var message by mutableStateOf("Draft")
      val events = mutableListOf<String>()
      val writes = mutableListOf<Pair<String, String?>>()
      setContent {
        MeshcoreTheme(darkTheme = false) {
          Surface {
            if (!adapted)
              ChatInput(
                value = message,
                onValueChange = { message = it },
                onSend = {},
                placeholder = "Message",
              )
            else {
              val node =
                UiBuilderNode(
                  id = "input",
                  componentId = catalog.input.id,
                  properties =
                    buildJsonObject {
                      put(
                        "value",
                        buildJsonObject {
                          put("type", "state")
                          put("variable", "message")
                          put("value", message)
                        },
                      )
                    },
                )
              adapter(
                CanvasNodeScope(
                  node,
                  Modifier,
                  CanvasMode.Device,
                  { _, _ -> },
                  { _, _ -> },
                  { 0 },
                  { _, _, _ -> },
                  events::add,
                  { key, value ->
                    writes += key to value
                    message = value.orEmpty()
                  },
                  {},
                )
              )
            }
          }
        }
      }
      val directImage = onRoot().captureToImage()
      val directory =
        File(requireNotNull(System.getProperty("typedCatalogRenders"))).also { it.mkdirs() }
      ImageIO.write(directImage.toAwtImage(), "png", directory.resolve("native-chat-input.png"))
      val direct = directImage.toPixelMap()
      runOnIdle { adapted = true }
      val typedImage = onRoot().captureToImage()
      ImageIO.write(typedImage.toAwtImage(), "png", directory.resolve("typed-chat-input.png"))
      val typed = typedImage.toPixelMap()
      assertEquals(direct.width, typed.width)
      assertEquals(direct.height, typed.height)
      for (y in 0 until direct.height) for (x in 0 until direct.width) assertEquals(
        direct[x, y],
        typed[x, y],
        "pixel $x,$y",
      )
      onNodeWithText("Draft").performTextReplacement("Hello mesh")
      runOnIdle { assertEquals("message" to "Hello mesh", writes.last()) }
      onNodeWithContentDescription("Send").performClick()
      runOnIdle { assertEquals("send", events.last()) }
    }
}
