plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
}

kotlin { jvmToolchain(21) }

dependencies {
  implementation(project(":meshcore-components"))
  implementation("ee.schimke.composeai:ui-builder-renderer-sdk:0.0.0")
  implementation("ee.schimke.composeai:preview-discovery:0.0.0")
  implementation(compose.desktop.currentOs)
  implementation(libs.compose.material3)
  testImplementation(kotlin("test"))
  testImplementation(libs.compose.uiTest)
  testImplementation(libs.compose.uiTestJunit4)
}

tasks.named<Test>("test") {
  systemProperty("java.awt.headless", "true")
  outputs.dir(layout.buildDirectory.dir("renders"))
  systemProperty(
    "typedCatalogRenders",
    layout.buildDirectory.dir("renders").get().asFile.absolutePath,
  )
  systemProperty("skiko.data.path", layout.buildDirectory.dir("skiko").get().asFile.absolutePath)
}

tasks.register<JavaExec>("exportTypedCatalog") {
  group = "ui builder"
  description = "Generate the explicitly opted-in MeshCore component catalog from compiled code."
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("ee.schimke.meshcore.catalog.ExportCatalogKt")
  args(layout.buildDirectory.dir("catalog").get().asFile.absolutePath)
  val output = layout.buildDirectory.dir("catalog")
  outputs.dir(output)
  doFirst { output.get().asFile.deleteRecursively() }
}
