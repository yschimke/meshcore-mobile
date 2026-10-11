package ee.schimke.meshcore.catalog

import ee.schimke.composeai.discovery.*
import ee.schimke.composeai.uibuilder.export.UiBuilderDocument
import java.io.File
import java.nio.file.Files
import kotlin.test.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

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
