package ee.schimke.meshcore.catalog

import ee.schimke.composeai.discovery.writeTo
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

fun main(args: Array<String>) {
  require(args.size == 1) { "usage: ExportCatalog <new output directory>" }
  val records = discoverCatalogComponents()
  val output = Path.of(args.single())
  MeshcoreCatalog(records).generate(records).writeTo(output)
  val designs = Files.createDirectories(output.resolve("ui-builder/designs"))
  for (name in listOf("chat-input", "device-overview", "component-sheet")) {
    Files.copy(
      Path.of("fixtures/$name.uid"),
      designs.resolve("$name.json"),
      StandardCopyOption.REPLACE_EXISTING,
    )
  }
}
