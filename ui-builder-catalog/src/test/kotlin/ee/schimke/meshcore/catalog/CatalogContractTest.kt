package ee.schimke.meshcore.catalog

import ee.schimke.composeai.discovery.*
import ee.schimke.composeai.uibuilder.export.UiBuilderDocument
import ee.schimke.meshcore.core.model.ChannelInfo
import ee.schimke.meshcore.core.model.RadioSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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
    assertEquals(setOf("chat-input.uid", "device-overview.uid"), designs.map { it.name }.toSet())
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
