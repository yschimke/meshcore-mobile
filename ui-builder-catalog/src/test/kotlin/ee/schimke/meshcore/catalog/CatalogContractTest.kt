package ee.schimke.meshcore.catalog

import ee.schimke.composeai.discovery.*
import ee.schimke.composeai.uibuilder.export.UiBuilderDocument
import ee.schimke.meshcore.app.ui.DeviceConnectStatus
import ee.schimke.meshcore.app.ui.LoginDialogState
import ee.schimke.meshcore.components.ui.DeviceSettingsUiState
import ee.schimke.meshcore.components.ui.MessageStatus
import ee.schimke.meshcore.core.model.BatteryInfo
import ee.schimke.meshcore.core.model.ChannelInfo
import ee.schimke.meshcore.core.model.ContactType
import ee.schimke.meshcore.core.model.RadioSettings
import ee.schimke.meshcore.core.model.byteStringOfHex
import java.io.File
import java.nio.file.Files
import kotlin.test.*
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class CatalogContractTest {
  @Test
  fun `fresh app metadata generates a portable explicitly selected catalog`() {
    val records = discoverCatalogComponents()
    val adapters = MeshcoreCatalog(records)
    adapters.definitions.forEach { definition ->
      assertEquals(
        emptyList(),
        definition.validateAgainst(
          records.components.single { it.canonicalId == definition.component.canonicalId }
        ),
      )
    }
    val pair = adapters.generate(records)
    assertEquals(
      setOf(
        "meshcore-mobile/chat-input",
        "meshcore-mobile/empty-contacts",
        "meshcore-mobile/theme",
        "meshcore-mobile/device-summary",
        "meshcore-mobile/radio-line",
        "meshcore-mobile/channel-list",
        "meshcore-mobile/channel-row",
        "meshcore-mobile/battery",
        "meshcore-mobile/contact-row",
        "meshcore-mobile/contact-list",
        "meshcore-mobile/chat-bubble",
        "meshcore-mobile/chat-message-list",
        "meshcore-mobile/chat-body",
        "meshcore-mobile/tcp-connect",
        "meshcore-mobile/device-settings",
        "meshcore-mobile/theme-picker",
        "meshcore-mobile/login-dialog",
        "meshcore-mobile/device-status",
        "meshcore-mobile/device-body",
      ),
      pair.catalog.statusSemantics.components.keys,
    )
    assertTrue(pair.catalog.diagnostics.isEmpty())
    val theme = pair.catalog.statusSemantics.components.getValue("meshcore-mobile/theme")
    assertEquals(
      listOf(JsonPrimitive("darkTheme")),
      theme.propertyCapabilities!!.map { it.jsonObject["name"] },
    )
    assertEquals(
      listOf(JsonPrimitive("content")),
      theme.slotCapabilities!!.map { it.jsonObject["name"] },
    )
    // An object property publishes the real class and its fields, each required or defaulted.
    val radio =
      pair.catalog.statusSemantics.components
        .getValue("meshcore-mobile/radio-line")
        .propertyCapabilities!!
        .single()
        .jsonObject
    assertEquals(JsonPrimitive("radio"), radio["name"])
    assertEquals(JsonPrimitive("object"), radio["jsonType"])
    val radioEditor = radio.getValue("editor").jsonObject
    assertEquals(JsonPrimitive("object"), radioEditor["control"])
    assertEquals(
      JsonPrimitive("ee.schimke.meshcore.core.model.RadioSettings"),
      radioEditor["objectType"],
    )
    assertEquals(
      listOf("frequencyHz", "bandwidthHz", "spreadingFactor", "codingRate").map {
        JsonPrimitive(it)
      },
      radioEditor.getValue("fields").jsonArray.map { it.jsonObject["name"] },
    )
    // A list property publishes its element's shape: here an object with a defaulted name.
    val channels =
      pair.catalog.statusSemantics.components
        .getValue("meshcore-mobile/channel-list")
        .propertyCapabilities!!
        .single()
        .jsonObject
    assertEquals(JsonPrimitive("channels"), channels["name"])
    assertEquals(JsonPrimitive("array"), channels["jsonType"])
    val channelsEditor = channels.getValue("editor").jsonObject
    assertEquals(JsonPrimitive("list"), channelsEditor["control"])
    val element = channelsEditor.getValue("element").jsonObject.getValue("editor").jsonObject
    assertEquals(JsonPrimitive("ee.schimke.meshcore.core.model.ChannelInfo"), element["objectType"])
    assertEquals(
      listOf(JsonPrimitive(true), JsonPrimitive(false)),
      element.getValue("fields").jsonArray.map { it.jsonObject["required"] },
    )
    val json = Json { encodeDefaults = true }
    val portable =
      json.decodeFromString(
        UiBuilderCatalogFile.serializer(),
        json.encodeToString(UiBuilderCatalogFile.serializer(), pair.catalog),
      )
    assertEquals(pair.catalog, portable)
    val destination = Files.createTempDirectory("meshcore-catalog").resolve("generation")

    pair.writeTo(destination)
    assertEquals(
      pair.record,
      json.decodeFromString(
        ComponentRecordFile.serializer(),
        Files.readString(destination.resolve("components.json")),
      ),
    )
    assertEquals(
      pair.catalog,
      json.decodeFromString(
        UiBuilderCatalogFile.serializer(),
        Files.readString(destination.resolve("ui-builder.json")),
      ),
    )
    assertFailsWith<IllegalArgumentException> { pair.writeTo(destination) }
    destination.parent.toFile().deleteRecursively()
  }

  @Test
  fun `list and object values round trip through the stored wrappers the builder writes`() {
    val catalog = MeshcoreCatalog(discoverCatalogComponents())
    val radio = RadioSettings(869_525_000, 250_000, 11, 5)
    val stored = RadioSettingsCodec.wrap(radio)
    assertEquals(JsonPrimitive("object"), stored["type"])
    assertEquals(radio, catalog.radio.radio.unwrap(stored))
    // Every RadioSettings field is required: a stored radio missing one is refused, not guessed.
    assertFailsWith<IllegalArgumentException> {
      RadioSettingsCodec.unwrap(
        buildJsonObject {
          put("type", "object")
          put(
            "fields",
            buildJsonObject {
              put("frequencyHz", intWrapper(869_525_000))
              put("bandwidthHz", intWrapper(250_000))
              put("spreadingFactor", intWrapper(11))
            },
          )
        }
      )
    }
    // A channel may omit its name, which the row then labels by index.
    val list = buildJsonObject {
      put("type", "list")
      put(
        "values",
        buildJsonArray {
          add(
            buildJsonObject {
              put("type", "object")
              put("fields", buildJsonObject { put("index", intWrapper(3)) })
            }
          )
        },
      )
    }
    assertEquals(listOf(ChannelInfo(index = 3, name = "")), catalog.channels.channels.unwrap(list))
    assertEquals(catalog.channels.channels.default, catalog.channels.channels.unwrap(null))
  }

  @Test
  fun `tracked list and object values decode through the published adapters`() {
    val catalog = MeshcoreCatalog(discoverCatalogComponents())
    val document =
      Json.decodeFromString(
        UiBuilderDocument.serializer(),
        File("fixtures/device-overview.uid").readText(),
      )
    val radio = document.nodes.getValue("radio").properties.getValue("radio").jsonObject
    assertEquals(RadioSettings(869_525_000, 250_000, 11, 5), catalog.radio.radio.unwrap(radio))
    val channels = document.nodes.getValue("channels").properties.getValue("channels").jsonObject
    assertEquals(
      listOf(
        ChannelInfo(index = 0, name = "Public"),
        ChannelInfo(index = 1, name = "Hiking"),
        ChannelInfo(index = 2, name = ""),
      ),
      catalog.channels.channels.unwrap(channels),
    )
  }

  @Test
  fun `every adapter publishes its declared properties and an insertion default that decodes`() {
    val records = discoverCatalogComponents()
    val catalog = MeshcoreCatalog(records)
    val published = catalog.generate(records).catalog.statusSemantics.components
    for (definition in catalog.definitions) {
      val component = published.getValue(definition.id)
      assertEquals(
        definition.properties.map { JsonPrimitive(it.name) },
        component.propertyCapabilities.orEmpty().map { it.jsonObject["name"] },
        definition.id,
      )
      definition.properties.forEach { assertDefaultRoundTrips(it) }
    }
  }

  @Test
  fun `parsed, nullable, enum and union values publish their shapes and refuse malformed text`() {
    val records = discoverCatalogComponents()
    val components = MeshcoreCatalog(records).generate(records).catalog.statusSemantics.components
    fun capability(id: String, name: String) =
      components
        .getValue(id)
        .propertyCapabilities!!
        .single { it.jsonObject["name"] == JsonPrimitive(name) }
        .jsonObject
    // A nullable object: the summary card's details are each optional.
    assertEquals(
      JsonPrimitive(true),
      capability("meshcore-mobile/device-summary", "self")["nullable"],
    )
    // A union publishes its cases; the connecting status holds only the case a value can describe.
    val status = capability("meshcore-mobile/device-status", "status").getValue("editor").jsonObject
    assertEquals(JsonPrimitive("union"), status["control"])
    assertEquals(
      listOf(JsonPrimitive("connecting")),
      status.getValue("cases").jsonArray.map { it.jsonObject["name"] },
    )
    val settings =
      capability("meshcore-mobile/device-settings", "state").getValue("editor").jsonObject
    assertEquals(
      listOf("discovering", "error", "ready").map(::JsonPrimitive),
      settings.getValue("cases").jsonArray.map { it.jsonObject["name"] },
    )
    // A contact's key and route are hex text, its type a closed set of members.
    val contactFields =
      capability("meshcore-mobile/contact-row", "contact")
        .getValue("editor")
        .jsonObject
        .getValue("fields")
        .jsonArray
        .associateBy { it.jsonObject.getValue("name").jsonPrimitive.content }
    assertEquals(JsonPrimitive("hex"), contactFields.getValue("publicKey").jsonObject["format"])
    assertEquals(JsonPrimitive("hex"), contactFields.getValue("path").jsonObject["format"])
    assertEquals(
      ContactType.entries.map { JsonPrimitive(it.name) }.sortedBy { it.content },
      contactFields.getValue("type").jsonObject.getValue("allowedValues").jsonArray.toList(),
    )
    assertFailsWith<IllegalArgumentException> { PublicKeyCodec.decode(JsonPrimitive("abcd")) }
    assertFailsWith<IllegalArgumentException> { ByteStringHexCodec.decode(JsonPrimitive("xyz")) }
    assertFailsWith<IllegalArgumentException> { ContactTypeCodec.decode(JsonPrimitive("PHONE")) }
    assertFailsWith<IllegalArgumentException> {
      AdapterValueCodecs.Instant.decode(JsonPrimitive("yesterday"))
    }
    // A failure carries a Throwable, which no stored value can describe: it is not a case.
    assertFailsWith<IllegalArgumentException> {
      DeviceConnectStatusCodec.wrap(DeviceConnectStatus.Failed(IllegalStateException("timeout")))
    }
  }

  @Test
  fun `the tracked component sheet decodes its stored model values`() {
    val catalog = MeshcoreCatalog(discoverCatalogComponents())
    val document =
      Json.decodeFromString(
        UiBuilderDocument.serializer(),
        File("fixtures/component-sheet.uid").readText(),
      )
    fun stored(node: String, name: String) =
      document.nodes.getValue(node).properties.getValue(name).jsonObject
    val contact = catalog.contactRow.contact.unwrap(stored("contact-row", "contact"))
    assertEquals("hilltop", contact.name)
    assertEquals(ContactType.REPEATER, contact.type)
    assertEquals("7e".repeat(32), contact.publicKey.toHex())
    assertEquals(byteStringOfHex("a1b2c3d4"), contact.path)
    assertEquals(Instant.parse("2026-10-12T08:00:00Z"), contact.advertTimestamp)
    assertEquals(Instant.fromEpochSeconds(1_700_000_000), contact.lastModified)
    val message = catalog.bubble.message.unwrap(stored("chat-bubble", "message"))
    assertEquals("alice", message.senderName)
    assertNull(message.snr)
    assertEquals(MessageStatus.Sent, message.status)
    assertFalse(message.isMine)
    assertEquals(
      DeviceSettingsUiState.Error("No response from /help"),
      catalog.settings.state.unwrap(stored("device-settings", "state")),
    )
    assertEquals(
      LoginDialogState.Authenticating,
      catalog.login.state.unwrap(stored("login-dialog", "state")),
    )
    assertEquals(
      BatteryInfo(3650, 2048L, 8192L),
      catalog.battery.battery.unwrap(stored("battery", "battery")),
    )
  }

  private fun <T> assertDefaultRoundTrips(property: AdapterProperty<*, T>) =
    assertEquals(
      property.default,
      property.unwrap(property.codec.wrap(property.default)),
      property.name,
    )

  private fun intWrapper(value: Int) = buildJsonObject {
    put("type", "int")
    put("value", value)
  }

  @Test
  fun `app adapter refuses stale discovery records`() {
    val records = discoverCatalogComponents()
    val catalog = MeshcoreCatalog(records)
    val stale =
      records
        .newBuilder()
        .also { b ->
          b.components =
            records.components.map { c ->
              c.newBuilder().also { it.signatureKnown = false }.build()
            }
        }
        .build()
    assertFailsWith<IllegalArgumentException> { catalog.generate(stale) }
  }

  @Test
  fun `tracked designs place only published adapters and the host's structural builtins`() {
    val published = MeshcoreCatalog(discoverCatalogComponents()).definitions.map { it.id }.toSet()
    val placeable = published + setOf("layout/column", "m3/text")
    val designs = File("fixtures").listFiles { file -> file.extension == "uid" }.orEmpty()
    assertEquals(
      setOf("chat-input.uid", "device-overview.uid", "component-sheet.uid"),
      designs.map { it.name }.toSet(),
    )
    val placed = mutableSetOf<String>()
    for (design in designs) {
      val document = Json.decodeFromString(UiBuilderDocument.serializer(), design.readText())
      for (node in document.nodes.values) {
        assertTrue(node.componentId in placeable, "${design.name}: ${node.componentId}")
        node.slots.values.flatten().forEach { child ->
          assertTrue(child in document.nodes, "${design.name}: missing slot child $child")
        }
        placed += node.componentId
      }
    }
    assertEquals(published, placed intersect published)
  }
}
