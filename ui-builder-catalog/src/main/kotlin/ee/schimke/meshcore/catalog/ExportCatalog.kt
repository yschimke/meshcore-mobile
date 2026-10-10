package ee.schimke.meshcore.catalog

import ee.schimke.composeai.discovery.writeTo
import java.nio.file.Path

fun main(args: Array<String>) {
  require(args.size == 1) { "usage: ExportCatalog <new output directory>" }
  val records = discoverCatalogComponents()
  MeshcoreCatalog(records).generate(records).writeTo(Path.of(args.single()))
}
