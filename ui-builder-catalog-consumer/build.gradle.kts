plugins { alias(libs.plugins.kotlinJvm) }

kotlin { jvmToolchain(21) }

dependencies {
  implementation("ee.schimke.composeai:compose-preview-ui-builder-runtime:3.123.0")
  testImplementation(kotlin("test"))
}

val catalogDirectory = project(":ui-builder-catalog").layout.buildDirectory.dir("catalog")

tasks.named<Test>("test") {
  dependsOn(":ui-builder-catalog:exportTypedCatalog")
  inputs.dir(catalogDirectory)
  systemProperty("typedCatalogDirectory", catalogDirectory.get().asFile.absolutePath)
  systemProperty("wasmSupported", "true")
}
