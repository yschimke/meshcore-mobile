import java.security.MessageDigest
import java.util.zip.ZipFile
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.Usage

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.kotlinSerialization)
}

// Both hosts compile the same typed definitions and the same real composable calls.
val generatedSources = layout.buildDirectory.dir("generated/catalog")
val generateRuntimeCatalog =
  tasks.register("generateRuntimeCatalog") {
    dependsOn(":ui-builder-catalog:exportTypedCatalog")
    val records =
      project(":ui-builder-catalog").layout.buildDirectory.file("catalog/components.json")
    inputs.file(records)
    val output = generatedSources
    outputs.dir(output)
    doLast {
      val literal =
        records
          .get()
          .asFile
          .readText()
          .replace("\\", "\\\\")
          .replace("\"", "\\\"")
          .replace("$", "\\$")
          .replace("\n", "\\n")
          .replace("\r", "\\r")
      output.get().file("RuntimeCatalog.kt").asFile.apply {
        parentFile.mkdirs()
        writeText(
          "package ee.schimke.meshcore.catalog\ninternal const val RUNTIME_RECORD = \"$literal\"\n"
        )
      }
    }
  }

kotlin {
  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
  wasmJs {
    browser()
    outputModuleName.set("meshcoreRenderer")
    binaries.executable()
  }
  sourceSets {
    commonMain {
      kotlin.srcDir("../ui-builder-catalog/src/main/kotlin")
      kotlin.exclude(
        "**/DiscoverCatalog.kt",
        "**/ExportCatalog.kt",
        "**/MeshcoreCanvasAdapterProvider.kt",
      )
      kotlin.srcDir(generateRuntimeCatalog)
      dependencies {
        implementation(project(":meshcore-components"))
        implementation("ee.schimke.composeai:ui-builder-renderer-sdk:0.0.0")
        implementation(libs.kotlinx.serialization.json)
      }
    }
  }
}

// Copy the exact Skiko runtime selected by this renderer's classpath, without a second version pin.
val skikoRuntimeAssets =
  configurations
    .named("wasmJsCompileClasspath")
    .get()
    .incoming
    .artifactView {
      withVariantReselection()
      componentFilter { id ->
        id is ModuleComponentIdentifier &&
          id.group == "org.jetbrains.skiko" &&
          id.module == "skiko-wasm-js"
      }
      attributes { attribute(Usage.USAGE_ATTRIBUTE, objects.named("skiko-runtime")) }
    }
    .files

abstract class ExtractSkikoRuntime : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val archives: ConfigurableFileCollection
  @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun extract() {
    val output = outputDirectory.get().asFile.apply { mkdirs() }
    ZipFile(archives.singleFile).use { zip ->
      for (name in listOf("skiko.mjs", "skiko.wasm", "js-skiko-reexport-symbols.mjs")) {
        zip.getInputStream(requireNotNull(zip.getEntry(name))).use { input ->
          output.resolve(name).outputStream().use { input.copyTo(it) }
        }
      }
    }
  }
}

val extractSkikoRuntime =
  tasks.register<ExtractSkikoRuntime>("extractSkikoRuntime") {
    archives.from(skikoRuntimeAssets)
    outputDirectory.set(layout.buildDirectory.dir("skikoRuntime"))
  }

val runtimeAssets =
  tasks.register<Sync>("runtimeAssets") {
    dependsOn("compileProductionExecutableKotlinWasmJs", "wasmJsProcessResources")
    from(layout.buildDirectory.dir("compileSync/wasmJs/main/productionExecutable/kotlin")) {
      exclude("*.map")
    }
    from(extractSkikoRuntime)
    from(layout.buildDirectory.dir("kotlin-multiplatform-resources/aggregated-resources/wasmJs"))
    from("src/wasmJsMain/resources")
    val sdk =
      providers
        .gradleProperty("composeUiBuilderDir")
        .orElse(providers.gradleProperty("uiBuilderSource"))
    from(sdk.map { file("$it/assets/js-joda/js-joda.esm.js") })
    into(layout.buildDirectory.dir("runtimeAssets"))
  }

val assembleRuntime =
  tasks.register("assembleRuntime") {
    dependsOn(runtimeAssets)
    val assets = layout.buildDirectory.dir("runtimeAssets")
    val output = layout.buildDirectory.dir("runtime")
    inputs.dir(assets)
    outputs.dir(output)
    doLast {
      val target = output.get().asFile
      target.deleteRecursively()
      assets.get().asFile.copyRecursively(target)
      val required =
        listOf(
          "index.html",
          "meshcoreRenderer.mjs",
          "meshcoreRenderer.wasm",
          "skiko.mjs",
          "skiko.wasm",
        )
      check(required.all { target.resolve(it).isFile }) { "Renderer distribution is incomplete" }
      val digest = MessageDigest.getInstance("SHA-256")
      target
        .walkTopDown()
        .filter { it.isFile }
        .map { it.relativeTo(target).invariantSeparatorsPath to it.readBytes() }
        .sortedBy { it.first }
        .forEach { (path, bytes) ->
          digest.update(path.toByteArray())
          digest.update(0)
          digest.update(bytes.size.toString().toByteArray())
          digest.update(0)
          digest.update(bytes)
        }
      val integrity = digest.digest().joinToString("") { "%02x".format(it) }
      // The id changes with the complete byte tree, including resources and bootstrap HTML.
      target
        .resolve("runtime-manifest.json")
        .writeText(
          """{"schema":"compose-ui-builder-runtime/v1","runtimeId":"meshcore-mobile-p2-${integrity.take(16)}","protocolVersion":2,"entrypoint":"index.html","integritySha256":"$integrity"}"""
        )
    }
  }

tasks.register<Zip>("rendererArchive") {
  group = "distribution"
  description = "Package MeshCore's typed Wasm renderer with an immutable runtime manifest."
  dependsOn(assembleRuntime)
  from(layout.buildDirectory.dir("runtime"))
  archiveFileName.set("ui-builder-runtime.zip")
  destinationDirectory.set(layout.buildDirectory)
  isPreserveFileTimestamps = false
  isReproducibleFileOrder = true
}
