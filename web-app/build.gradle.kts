import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.kotlinSerialization)
}

kotlin {
  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
  wasmJs {
    outputModuleName = "meshcore"
    browser { commonWebpackConfig { outputFileName = "meshcore.js" } }
    binaries.executable()
  }
  sourceSets {
    wasmJsMain.dependencies {
      implementation(projects.meshcoreComponents)
      implementation(projects.meshcoreTransportWebserial)
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.kotlinx.serialization.json)
    }
  }
}

// The Wasm web build as a Wasmo app (static resources only); see docs/wasmo-app.md. The directory
// form installs in Wasmo's dev mode; the archive is the distributable .wasmo file.
val appVersion = "0.1.8" // x-release-please-version
val wasmoVersion =
  appVersion.split(".").map(String::toLong).let { (major, minor, patch) ->
    major * 1_000_000 + minor * 1_000 + patch
  }
val wasmoAppDirectory =
  tasks.register<Sync>("wasmoAppDirectory") {
    val version = wasmoVersion
    inputs.property("version", version)
    into(layout.buildDirectory.dir("wasmo/dir/meshcore.wasmo"))
    from("wasmo/wasmo-manifest.toml") { expand("version" to version) }
    // The app page is www/app.html, not index.html: www-public/index.html answers "/" for every
    // visitor (Wasmo serves www-public/ first) and sends the owner on to app.html.
    into("www") {
      from(tasks.named("wasmJsBrowserDistribution"))
      from("wasmo/wasm-mime-fallback.js", "wasmo/meshcore-backend.js")
      filesMatching("index.html") {
        filter { line ->
          line
            // meshcore-backend.js seeds the radios list from the Wasmo install, then loads
            // meshcore.js.
            .replace(
              "<script src=\"meshcore.js\" defer></script>",
              "<script src=\"meshcore-backend.js\" defer></script>",
            )
            // Runs before the deferred scripts, so meshcore.js's streaming compile sees the
            // fallback.
            .replace(
              "<script src=\"bootstrap.js\" defer></script>",
              "<script src=\"wasm-mime-fallback.js\"></script>\n" +
                "  <script src=\"bootstrap.js\" defer></script>",
            )
            .replace(
              "<title>MeshCore</title>",
              "<title>MeshCore</title>\n" +
                "  <link rel=\"manifest\" href=\"/manifest.webmanifest\">\n" +
                "  <link rel=\"icon\" href=\"/icon-192.png\">\n" +
                "  <link rel=\"apple-touch-icon\" href=\"/apple-touch-icon.png\">\n" +
                "  <meta name=\"theme-color\" content=\"#006a60\">",
            )
        }
        name = "app.html"
      }
    }
    val icon = rootProject.file("app/src/main/play/listings/en-GB/graphics/icon/icon.png")
    into("www/assets") { from(icon) }
    // Public: the landing page, the web app manifest and its icons. The browser fetches a manifest
    // without credentials, so it must not sit behind the owner-only www/.
    into("www-public") {
      from("wasmo/www-public")
      from(icon) { rename { "icon-512.png" } }
    }
  }

// A plain Zip task writes a directory entry for every folder ("www/", "www/assets/", …). Wasmo's
// installer writes each zip entry to its object store as-is, so a directory entry lands as a file
// that blocks the files beneath it and the install fails. Write only file entries, as Wasmo's own
// packager does.
tasks.register("wasmoApp") {
  description = "Packages the Wasm web build as an installable .wasmo archive."
  // Locals, not top-level script vals: a doLast action that captures a top-level `val` pulls in the
  // build-script object, which the configuration cache cannot serialize.
  val inputDir = layout.buildDirectory.dir("wasmo/dir/meshcore.wasmo")
  val archiveFile = layout.buildDirectory.file("wasmo/meshcore.wasmo")
  dependsOn(wasmoAppDirectory)
  inputs.dir(inputDir)
  outputs.file(archiveFile)
  doLast {
    val root = inputDir.get().asFile
    val archive = archiveFile.get().asFile
    archive.parentFile.mkdirs()
    ZipOutputStream(archive.outputStream().buffered()).use { zip ->
      root
        .walkTopDown()
        .filter { it.isFile }
        .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
        .forEach { file ->
          // A fixed timestamp keeps the archive byte-for-byte reproducible across builds.
          val entry = ZipEntry(file.relativeTo(root).invariantSeparatorsPath)
          entry.time = 0L
          zip.putNextEntry(entry)
          file.inputStream().use { it.copyTo(zip) }
          zip.closeEntry()
        }
    }
  }
}
