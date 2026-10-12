package ee.schimke.meshcore.catalog

import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import ee.schimke.composeai.discovery.AdapterValueCodecs
import ee.schimke.composeai.uibuilder.export.UiBuilderNode
import ee.schimke.composeai.uibuilder.renderer.sdk.*
import ee.schimke.meshcore.app.ui.DeviceConnectStatus
import ee.schimke.meshcore.app.ui.DeviceStatusView
import ee.schimke.meshcore.app.ui.LoginDialog
import ee.schimke.meshcore.app.ui.LoginDialogState
import ee.schimke.meshcore.app.ui.theme.ThemeMode
import ee.schimke.meshcore.app.ui.theme.ThemePalette
import ee.schimke.meshcore.app.ui.theme.ThemePickerDialog
import ee.schimke.meshcore.app.ui.theme.ThemeSettings
import ee.schimke.meshcore.components.ui.BatterySection
import ee.schimke.meshcore.components.ui.ChannelList
import ee.schimke.meshcore.components.ui.ChannelRow
import ee.schimke.meshcore.components.ui.ChatBody
import ee.schimke.meshcore.components.ui.ChatBubble
import ee.schimke.meshcore.components.ui.ChatInput
import ee.schimke.meshcore.components.ui.ChatMessage
import ee.schimke.meshcore.components.ui.ChatMessageList
import ee.schimke.meshcore.components.ui.ContactList
import ee.schimke.meshcore.components.ui.ContactListEmpty
import ee.schimke.meshcore.components.ui.ContactRow
import ee.schimke.meshcore.components.ui.DeviceBody
import ee.schimke.meshcore.components.ui.DeviceSettingsBody
import ee.schimke.meshcore.components.ui.DeviceSettingsUiState
import ee.schimke.meshcore.components.ui.DeviceSummaryCard
import ee.schimke.meshcore.components.ui.LastMessageInfo
import ee.schimke.meshcore.components.ui.MessageStatus
import ee.schimke.meshcore.components.ui.RadioLine
import ee.schimke.meshcore.components.ui.TcpConnectPanel
import ee.schimke.meshcore.components.ui.theme.MeshcoreTheme
import ee.schimke.meshcore.core.model.BatteryInfo
import ee.schimke.meshcore.core.model.ChannelInfo
import ee.schimke.meshcore.core.model.ContactType
import ee.schimke.meshcore.core.model.RadioSettings
import ee.schimke.meshcore.core.model.byteStringOfHex
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*
import kotlin.time.Instant
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

  @Test
  fun `native RadioLine and typed catalog draw the same pixels from an object property`() =
    runDesktopComposeUiTest(width = 500, height = 120) {
      val catalog = MeshcoreCatalog(discoverCatalogComponents())
      val adapter = requireNotNull(catalog.registry()[catalog.radio.id])
      // Not the adapter's default, so the line can only match if the stored object was decoded.
      val radio = RadioSettings(915_000_000, 125_000, 10, 7)
      var adapted by mutableStateOf(false)
      setContent {
        MeshcoreTheme(darkTheme = false) {
          Surface {
            if (!adapted) RadioLine(radio = radio)
            else {
              val node =
                UiBuilderNode(
                  id = "radio",
                  componentId = catalog.radio.id,
                  properties = buildJsonObject { put("radio", RadioSettingsCodec.wrap(radio)) },
                )
              adapter(canvasScope(node))
            }
          }
        }
      }
      assertSamePixels("radio-line") { adapted = true }
      onNodeWithText("915.000 MHz · BW 125 kHz · SF 10 · CR 7").assertExists()
    }

  @Test
  fun `native ChannelList and typed catalog draw the same pixels from a list of objects`() =
    runDesktopComposeUiTest(width = 500, height = 260) {
      val catalog = MeshcoreCatalog(discoverCatalogComponents())
      val adapter = requireNotNull(catalog.registry()[catalog.channels.id])
      // The unnamed third channel shows the row's own index label in place of a name.
      val channels =
        listOf(
          ChannelInfo(index = 0, name = "General"),
          ChannelInfo(index = 1, name = "Emergency"),
          ChannelInfo(index = 2, name = ""),
        )
      var adapted by mutableStateOf(false)
      setContent {
        MeshcoreTheme(darkTheme = false) {
          Surface {
            if (!adapted) ChannelList(channels = channels, onChannelClick = {})
            else {
              val node =
                UiBuilderNode(
                  id = "channels",
                  componentId = catalog.channels.id,
                  properties =
                    buildJsonObject {
                      put("channels", AdapterValueCodecs.list(ChannelInfoCodec).wrap(channels))
                    },
                )
              adapter(canvasScope(node))
            }
          }
        }
      }
      assertSamePixels("channel-list") { adapted = true }
      onNodeWithText("General").assertExists()
      onNodeWithText("Emergency").assertExists()
    }

  @Test
  fun `a channel list that repeats an index draws its first channel instead of failing`() =
    runDesktopComposeUiTest(width = 500, height = 260) {
      val catalog = MeshcoreCatalog(discoverCatalogComponents())
      val adapter = requireNotNull(catalog.registry()[catalog.channels.id])
      val channels =
        listOf(ChannelInfo(index = 0, name = "General"), ChannelInfo(index = 0, name = "Repeat"))
      setContent {
        MeshcoreTheme(darkTheme = false) {
          Surface {
            val node =
              UiBuilderNode(
                id = "channels",
                componentId = catalog.channels.id,
                properties =
                  buildJsonObject {
                    put("channels", AdapterValueCodecs.list(ChannelInfoCodec).wrap(channels))
                  },
              )
            adapter(canvasScope(node))
          }
        }
      }
      onNodeWithText("General").assertExists()
      onNodeWithText("Repeat").assertDoesNotExist()
    }

  @Test
  fun `native DeviceSummaryCard and typed catalog draw the same pixels from nullable objects`() {
    val self = MeshcoreSamples.self.copy(name = "ridge-node", publicKey = MeshcoreSamples.key(0x5C))
    val radio = RadioSettings(915_000_000, 250_000, 9, 6)
    val battery = BatteryInfo(3650, 2048, 8192)
    parity(
      "device-summary-filled",
      catalog.deviceSummary.id,
      props(
        "self" to SelfInfoCodec.wrap(self),
        "radio" to RadioSettingsCodec.wrap(radio),
        "battery" to BatteryInfoCodec.wrap(battery),
      ),
      height = 260,
      native = { DeviceSummaryCard(self = self, radio = radio, battery = battery) },
    ) {
      onNodeWithText("ridge-node").assertExists()
      onNodeWithText("5c5c5c5c5c5c5c5c").assertExists()
    }
  }

  @Test
  fun `native BatterySection and typed catalog draw the same pixels from Long fields`() {
    val battery = BatteryInfo(3300, 3_000_000_000L, 4_000_000_000L)
    parity(
      "battery",
      catalog.battery.id,
      props("battery" to BatteryInfoCodec.wrap(battery)),
      height = 120,
      native = { BatterySection(battery = battery) },
    )
  }

  @Test
  fun `native ContactRow and typed catalog draw the same pixels and dispatch its click`() {
    val contact =
      MeshcoreSamples.contact("hilltop", 4, 0x7E, ContactType.REPEATER)
        .copy(path = byteStringOfHex("a1b2c3d4"))
    parity(
      "contact-row",
      catalog.contactRow.id,
      props("contact" to ContactCodec.wrap(contact)),
      height = 120,
      native = { ContactRow(contact = contact, onClick = {}) },
    ) { events ->
      onNodeWithText("hilltop").performClick()
      runOnIdle { assertEquals(listOf("click"), events) }
    }
  }

  @Test
  fun `native ContactList and typed catalog draw the same pixels and dispatch a payload click`() {
    val contacts = MeshcoreSamples.contacts.take(3)
    parity(
      "contact-list",
      catalog.contacts.id,
      props("contacts" to AdapterValueCodecs.list(ContactCodec).wrap(contacts)),
      height = 300,
      native = { ContactList(contacts = contacts, onContactClick = {}) },
    ) { events ->
      onNodeWithText("common-room").performClick()
      runOnIdle { assertEquals(listOf("contactClick"), events) }
    }
  }

  @Test
  fun `native ChannelRow and typed catalog draw the same pixels and dispatch its click`() {
    val channel = ChannelInfo(index = 4, name = "Search & rescue")
    parity(
      "channel-row",
      catalog.channelRow.id,
      props("channel" to ChannelInfoCodec.wrap(channel)),
      height = 120,
      native = { ChannelRow(channel = channel, onClick = {}) },
    ) { events ->
      onNodeWithText("Search & rescue").performClick()
      runOnIdle { assertEquals(listOf("click"), events) }
    }
  }

  @Test
  fun `native ChatBubble and typed catalog draw the same pixels and dispatch retry`() {
    val failed =
      ChatMessage(
        id = "f1",
        senderName = null,
        text = "anyone copy?",
        timestamp = Instant.parse("2026-10-12T08:00:00Z"),
        snr = 5,
        isMine = true,
        status = MessageStatus.Failed,
      )
    parity(
      "chat-bubble",
      catalog.bubble.id,
      props("message" to ChatMessageCodec.wrap(failed)),
      height = 120,
      native = { ChatBubble(message = failed, onRetry = {}) },
    ) { events ->
      onNodeWithText("Failed — tap to retry").performClick()
      runOnIdle { assertEquals(listOf("retry"), events) }
    }
  }

  @Test
  fun `native ChatMessageList and typed catalog draw the same pixels and dispatch a payload retry`() {
    val messages =
      MeshcoreSamples.contactMessages.take(2) +
        MeshcoreSamples.channelMessage +
        MeshcoreSamples.contactMessages[3].copy(id = "f2", status = MessageStatus.Failed)
    parity(
      "chat-message-list",
      catalog.messages.id,
      props("messages" to AdapterValueCodecs.list(ChatMessageCodec).wrap(messages)),
      height = 420,
      native = { ChatMessageList(messages = messages, onRetry = {}) },
    ) { events ->
      onNodeWithText("alice").assertExists()
      onNodeWithText("Failed — tap to retry").performClick()
      runOnIdle { assertEquals(listOf("retry"), events) }
    }
  }

  @Test
  fun `native ChatBody and typed catalog draw the same screen and dispatch back`() {
    val messages = listOf(MeshcoreSamples.channelMessage)
    parity(
      "chat-body",
      catalog.chat.id,
      props(
        "title" to AdapterValueCodecs.String.wrap("General"),
        "subtitle" to AdapterValueCodecs.nullable(AdapterValueCodecs.String).wrap("Channel 0"),
        "messages" to AdapterValueCodecs.list(ChatMessageCodec).wrap(messages),
        "draft" to AdapterValueCodecs.String.wrap("on my way"),
        "inputPlaceholder" to AdapterValueCodecs.String.wrap("Message General"),
      ),
      width = 420,
      height = 640,
      native = {
        ChatBody(
          title = "General",
          subtitle = "Channel 0",
          messages = messages,
          draft = "on my way",
          onDraftChange = {},
          onSend = {},
          onBack = {},
          inputPlaceholder = "Message General",
          onRetry = {},
        )
      },
    ) { events ->
      onNodeWithContentDescription("Back").performClick()
      onNodeWithContentDescription("Send").performClick()
      runOnIdle { assertEquals(listOf("back", "send"), events) }
    }
  }

  @Test
  fun `native TcpConnectPanel and typed catalog draw the same pixels and dispatch a two-argument connect`() =
    parity(
      "tcp-connect",
      catalog.tcp.id,
      props(
        "defaultHost" to AdapterValueCodecs.String.wrap("10.0.0.42"),
        "defaultPort" to AdapterValueCodecs.Int.wrap(4403),
      ),
      height = 340,
      native = {
        TcpConnectPanel(
          busy = false,
          onConnect = { _, _ -> },
          defaultHost = "10.0.0.42",
          defaultPort = 4403,
        )
      },
    ) { events ->
      onNodeWithText("Connect").performClick()
      runOnIdle { assertEquals(listOf("connect"), events) }
    }

  @Test
  fun `native DeviceSettingsBody and typed catalog draw each union case`() {
    val states =
      listOf(
        "error" to DeviceSettingsUiState.Error("No response from /help"),
        "ready" to DeviceSettingsUiState.Ready(hasBuzzer = true, buzzerMode = "off"),
      )
    for ((case, state) in states) {
      parity(
        "device-settings-$case",
        catalog.settings.id,
        props("state" to DeviceSettingsUiStateCodec.wrap(state)),
        height = 300,
        native = { DeviceSettingsBody(state = state, onToggleBuzzer = {}, onBack = {}) },
      ) { events ->
        if (state is DeviceSettingsUiState.Ready) {
          onNode(isToggleable()).performClick()
          runOnIdle { assertEquals(listOf("toggleBuzzer"), events) }
        } else onNodeWithText("No response from /help").assertExists()
      }
    }
  }

  @Test
  fun `native ThemePickerDialog and typed catalog draw the same dialog and dispatch payload events`() {
    val current = ThemeSettings(mode = ThemeMode.Dark, palette = ThemePalette.Dynamic)
    parity(
      "theme-picker",
      catalog.themePicker.id,
      props("current" to ThemeSettingsCodec.wrap(current)),
      height = 720,
      native = {
        ThemePickerDialog(
          current = current,
          onModeSelect = {},
          onPaletteSelect = {},
          onDismiss = {},
        )
      },
    ) { events ->
      onNodeWithText("Dynamic (system)").performClick()
      onNodeWithText("Dark").performClick()
      runOnIdle { assertEquals(listOf("paletteSelect", "modeSelect"), events) }
    }
  }

  @Test
  fun `native LoginDialog and typed catalog draw the same dialog for a prompting case`() {
    val state = LoginDialogState.Prompting(errorMessage = "Wrong password")
    parity(
      "login-dialog",
      catalog.login.id,
      props(
        "contactName" to AdapterValueCodecs.String.wrap("bob-repeater"),
        "contactType" to AdapterValueCodecs.String.wrap("repeater"),
        "state" to LoginDialogStateCodec.wrap(state),
      ),
      height = 520,
      native = {
        LoginDialog(
          contactName = "bob-repeater",
          contactType = "repeater",
          state = state,
          onLogin = {},
          onDismiss = {},
        )
      },
    ) { events ->
      onNodeWithText("Wrong password").assertExists()
      onNodeWithText("Join").performClick()
      runOnIdle { assertEquals(listOf("login"), events) }
    }
  }

  @Test
  fun `native DeviceStatusView and typed catalog draw the same connecting card at the pinned clock`() {
    val status = DeviceConnectStatus.Connecting(startedAtMs = 1_000L, timeoutMs = 30_000L)
    parity(
      "device-status",
      catalog.status.id,
      props(
        "title" to AdapterValueCodecs.String.wrap("ridge-node"),
        "status" to DeviceConnectStatusCodec.wrap(status),
      ),
      height = 420,
      native = {
        DeviceStatusView(
          title = "ridge-node",
          status = status,
          onCancel = {},
          onOpenThemePicker = {},
          nowMs = { 8_000L },
        )
      },
    ) { events ->
      // 7 s into a 30 s attempt: 23 s remain.
      onNodeWithText("Waiting for the radio to respond — 23s").assertExists()
      onNodeWithText("Cancel").performClick()
      runOnIdle { assertEquals(listOf("cancel"), events) }
    }
  }

  @Test
  fun `native DeviceBody and typed catalog draw the same screen and dispatch payload clicks`() {
    val lastMessage =
      LastMessageInfo.Channel(
        channelIndex = 0,
        channelName = "General",
        sender = "carol",
        text = "anyone near the north ridge?",
        snr = 7,
      )
    val contacts = MeshcoreSamples.contacts.take(2)
    val channels = MeshcoreSamples.channels.take(2)
    parity(
      "device-body",
      catalog.device.id,
      props(
        "self" to SelfInfoCodec.wrap(MeshcoreSamples.self.copy(name = "ridge-node")),
        "contacts" to AdapterValueCodecs.list(ContactCodec).wrap(contacts),
        "channels" to AdapterValueCodecs.list(ChannelInfoCodec).wrap(channels),
        "lastMessage" to LastMessageInfoCodec.wrap(lastMessage),
        "contactedKeys" to
          SetCodec(AdapterValueCodecs.String).wrap(setOf(contacts[0].publicKey.toHex())),
        "contactedChannelIndices" to SetCodec(AdapterValueCodecs.Int).wrap(setOf(1)),
        "warnings" to
          AdapterValueCodecs.list(AdapterValueCodecs.String).wrap(listOf("Clock drift")),
      ),
      width = 420,
      height = 780,
      native = {
        DeviceBody(
          self = MeshcoreSamples.self.copy(name = "ridge-node"),
          battery = MeshcoreSamples.battery,
          radio = MeshcoreSamples.radio,
          contacts = contacts,
          channels = channels,
          contactedKeys = setOf(contacts[0].publicKey.toHex()),
          contactedChannelIndices = setOf(1),
          lastMessage = lastMessage,
          warnings = listOf("Clock drift"),
          onDisconnect = {},
        )
      },
    ) { events ->
      onAllNodesWithText("ridge-node").onFirst().assertExists()
      // Only the contacted channel is listed; General is filtered out of its section.
      onNodeWithText("Emergency").assertExists()
      onNodeWithText("alice").performClick()
      runOnIdle { assertEquals(listOf("contactClick"), events) }
    }
  }

  /** The default catalog, discovered once per test class. */
  private val catalog = MeshcoreCatalog(discoverCatalogComponents())

  private fun props(vararg values: Pair<String, JsonObject>) = JsonObject(values.toMap())

  /**
   * Draw [native] and then the typed adapter [id] with the stored [properties] in the same light
   * theme, require identical pixels, and hand [check] the events the adapter dispatched. Inspection
   * mode is on, as in a preview render, so frame-driven animations hold still.
   */
  private fun parity(
    name: String,
    id: String,
    properties: JsonObject,
    width: Int = 500,
    height: Int = 180,
    native: @Composable () -> Unit,
    check: ComposeUiTest.(List<String>) -> Unit = {},
  ) =
    runDesktopComposeUiTest(width = width, height = height) {
      val adapter = requireNotNull(catalog.registry()[id])
      var adapted by mutableStateOf(false)
      val events = mutableListOf<String>()
      setContent {
        CompositionLocalProvider(LocalInspectionMode provides true) {
          MeshcoreTheme(darkTheme = false) {
            Surface {
              if (!adapted) native()
              else
                adapter(
                  canvasScope(
                    UiBuilderNode(id = name, componentId = id, properties = properties),
                    dispatch = events::add,
                  )
                )
            }
          }
        }
      }
      assertSamePixels(name) { adapted = true }
      check(events)
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

  /**
   * Save the native render, switch to the typed adapter, and require identical pixels. A dialog
   * draws in a layer of its own, a second root: the visible root (the largest) is compared.
   */
  private fun ComposeUiTest.assertSamePixels(name: String, adapt: () -> Unit) {
    val directory =
      File(requireNotNull(System.getProperty("typedCatalogRenders"))).also { it.mkdirs() }
    val directImage = visibleRoot().captureToImage()
    ImageIO.write(directImage.toAwtImage(), "png", directory.resolve("native-$name.png"))
    val direct = directImage.toPixelMap()
    runOnIdle(adapt)
    val typedImage = visibleRoot().captureToImage()
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

  private fun ComposeUiTest.visibleRoot(): SemanticsNodeInteraction {
    val roots = onAllNodes(isRoot())
    val areas = roots.fetchSemanticsNodes().map { it.boundsInRoot.width * it.boundsInRoot.height }
    return roots[areas.indices.maxBy { areas[it] }]
  }
}
