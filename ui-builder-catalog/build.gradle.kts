plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
}

kotlin { jvmToolchain(21) }

dependencies {
  implementation(project(":meshcore-components"))
  implementation("ee.schimke.composeai:ui-builder-renderer-sdk:0.0.0")
  implementation("ee.schimke.composeai:preview-discovery:2.74.1")
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

// A portable local delivery artifact: the host reads these JSON files and loads only JARs.
val stageJvmCatalogBundle =
  tasks.register<Sync>("stageJvmCatalogBundle") {
    dependsOn("exportTypedCatalog")
    into(layout.buildDirectory.dir("jvm-catalog-bundle"))
    from(layout.buildDirectory.dir("catalog"))
    from("fixtures") { into("designs") }
    into("lib") {
      from(tasks.named("jar"))
      from(configurations.runtimeClasspath)
    }
  }

val packageJvmCatalogBundle =
  tasks.register<Zip>("packageJvmCatalogBundle") {
    group = "ui builder"
    description = "Package the generated catalog and executable JVM adapters for a separate host."
    dependsOn(stageJvmCatalogBundle)
    from(layout.buildDirectory.dir("jvm-catalog-bundle"))
    archiveFileName.set("meshcore-mobile-jvm-catalog.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
  }

tasks.register<Sync>("unpackJvmCatalogBundle") {
  group = "verification"
  description = "Install the packaged ZIP into a task-owned directory for an external host test."
  dependsOn(packageJvmCatalogBundle)
  from(zipTree(packageJvmCatalogBundle.flatMap { it.archiveFile }))
  into(layout.buildDirectory.dir("installed-jvm-catalog"))
}
