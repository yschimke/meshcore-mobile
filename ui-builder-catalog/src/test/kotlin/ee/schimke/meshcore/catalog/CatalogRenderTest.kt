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
import ee.schimke.meshcore.components.ui.ContactListEmpty
import ee.schimke.meshcore.components.ui.DeviceSummaryCard
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
                canvasScope(
                  node,
                  dispatch = events::add,
                  updateState = { key, value ->
                    writes += key to value
                    message = value.orEmpty()
                  },
                )
              )
            }
          }
        }
      }
      assertSamePixels("chat-input") { adapted = true }
      onNodeWithText("Draft").performTextReplacement("Hello mesh")
      runOnIdle { assertEquals("message" to "Hello mesh", writes.last()) }
      onNodeWithContentDescription("Send").performClick()
      runOnIdle { assertEquals("send", events.last()) }
    }

  @Test
  fun `native MeshcoreTheme and typed catalog draw the same pixels around the content slot`() =
    runDesktopComposeUiTest(width = 500, height = 180) {
      val catalog = MeshcoreCatalog(discoverCatalogComponents())
      val adapter = requireNotNull(catalog.registry()[catalog.theme.id])
      var adapted by mutableStateOf(false)
      val slots = mutableListOf<String>()
      setContent {
        // The outer theme is light, so the dark nested surface proves darkTheme was decoded.
        MeshcoreTheme(darkTheme = false) {
          Surface {
            if (!adapted) MeshcoreTheme(darkTheme = true) { Surface { ContactListEmpty() } }
            else {
              val node =
                UiBuilderNode(
                  id = "theme",
                  componentId = catalog.theme.id,
                  properties =
                    buildJsonObject {
                      put(
                        "darkTheme",
                        buildJsonObject {
                          put("type", "bool")
                          put("value", true)
                        },
                      )
                    },
                  slots = mapOf("content" to listOf("empty")),
                )
              adapter(
                canvasScope(
                  node,
                  renderSlot = { name, next ->
                    slots += name
                    Surface(modifier = next) { ContactListEmpty() }
                  },
                )
              )
            }
          }
        }
      }
      assertSamePixels("meshcore-theme") { adapted = true }
      runOnIdle { assertEquals(listOf("content"), slots.distinct()) }
    }

  @Test
  fun `native DeviceSummaryCard placeholder and typed catalog draw the same pixels`() =
    runDesktopComposeUiTest(width = 500, height = 180) {
      val catalog = MeshcoreCatalog(discoverCatalogComponents())
      val adapter = requireNotNull(catalog.registry()[catalog.deviceSummary.id])
      var adapted by mutableStateOf(false)
      setContent {
        MeshcoreTheme(darkTheme = false) {
          Surface {
            if (!adapted) DeviceSummaryCard(self = null, radio = null, battery = null)
            else
              adapter(
                canvasScope(UiBuilderNode(id = "summary", componentId = catalog.deviceSummary.id))
              )
          }
        }
      }
      assertSamePixels("device-summary") { adapted = true }
    }

  /** The resolved node the SDK hands an adapter; no document interpreter or device is involved. */
  private fun canvasScope(
    node: UiBuilderNode,
    renderSlot: @Composable (String, Modifier) -> Unit = { _, _ -> },
    dispatch: (String) -> Unit = {},
    updateState: (String, String?) -> Unit = { _, _ -> },
  ) =
    CanvasNodeScope(
      node,
      Modifier,
      CanvasMode.Device,
      renderSlot,
      { _, _ -> },
      { 0 },
      { _, _, _ -> },
      dispatch,
      updateState,
      {},
    )

  /** Save the native render, switch to the typed adapter, and require identical pixels. */
  private fun ComposeUiTest.assertSamePixels(name: String, adapt: () -> Unit) {
    val directory =
      File(requireNotNull(System.getProperty("typedCatalogRenders"))).also { it.mkdirs() }
    val directImage = onRoot().captureToImage()
    ImageIO.write(directImage.toAwtImage(), "png", directory.resolve("native-$name.png"))
    val direct = directImage.toPixelMap()
    runOnIdle(adapt)
    val typedImage = onRoot().captureToImage()
    ImageIO.write(typedImage.toAwtImage(), "png", directory.resolve("typed-$name.png"))
    val typed = typedImage.toPixelMap()
    assertEquals(direct.width, typed.width)
    assertEquals(direct.height, typed.height)
    for (y in 0 until direct.height) for (x in 0 until direct.width) assertEquals(
      direct[x, y],
      typed[x, y],
      "$name pixel $x,$y",
    )
  }
}
