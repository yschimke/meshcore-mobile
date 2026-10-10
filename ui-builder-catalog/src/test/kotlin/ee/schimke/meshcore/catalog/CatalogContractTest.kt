package ee.schimke.meshcore.catalog

import ee.schimke.composeai.discovery.*
import java.nio.file.Files
import kotlin.test.*
import kotlinx.serialization.json.Json

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
      setOf("meshcore-mobile/chat-input", "meshcore-mobile/empty-contacts"),
      pair.catalog.statusSemantics.components.keys,
    )
    assertTrue(pair.catalog.diagnostics.isEmpty())
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
}
