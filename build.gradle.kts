// Match the app module's root-relative check, including when Gradle starts elsewhere.
// Builds without Firebase configuration resolve neither its tooling nor SDKs.
buildscript {
  if (rootDir.resolve("app/google-services.json").isFile) {
    repositories {
      google()
      mavenCentral()
      gradlePluginPortal()
    }
    dependencies {
      classpath("com.google.gms:google-services:4.5.0")
      classpath("com.google.firebase:firebase-crashlytics-gradle:3.0.8")
    }
  }
}

plugins {
  // this is necessary to avoid the plugins to be loaded multiple times
  // in each subproject's classloader
  alias(libs.plugins.androidApplication) apply false
  alias(libs.plugins.androidLibrary) apply false
  alias(libs.plugins.composeMultiplatform) apply false
  alias(libs.plugins.composeCompiler) apply false
  alias(libs.plugins.kotlinMultiplatform) apply false
  alias(libs.plugins.kotlinJvm) apply false
  alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
  alias(libs.plugins.metro) apply false
  alias(libs.plugins.graalvmNative) apply false
  alias(libs.plugins.protobuf) apply false
  alias(libs.plugins.ktfmt) apply false
  alias(libs.plugins.tapmoc) apply false
}

subprojects {
  apply(plugin = rootProject.libs.plugins.ktfmt.get().pluginId)
  extensions.configure<com.ncorti.ktfmt.gradle.KtfmtExtension> {
    googleStyle()
  }
}

// CI can provide the pinned Yarn from npm when GitHub release downloads are unavailable.
if (providers.environmentVariable("USE_SYSTEM_YARN").orNull == "true") {
  plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
    extensions.configure<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec> {
      download.set(false)
    }
  }
  plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin> {
    extensions.configure<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec> {
      download.set(false)
    }
  }
}

// Wires .githooks/ as the repository's hooks directory. One-time bootstrap:
// `./gradlew installGitHooks`.
tasks.register<Exec>("installGitHooks") {
  group = "git hooks"
  description = "Configure git to use the .githooks directory in this repo."
  workingDir = rootDir
  commandLine("git", "config", "core.hooksPath", ".githooks")
}

abstract class VerifyYschimkeBomsTask : DefaultTask() {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val versionCatalog: RegularFileProperty

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val buildFiles: ConfigurableFileCollection

  @TaskAction
  fun verify() {
    val composeAiGroup = "ee.schimke.composeai"
    val catalogText = versionCatalog.get().asFile.readText()
    var inLibraries = false
    val bomsByCoordinate = mutableMapOf<String, String>()
    val libraries = mutableListOf<Triple<String, String, Boolean>>()

    catalogText.lineSequence().forEachIndexed { index, rawLine ->
      val line = rawLine.substringBefore('#').trim()
      if (line.startsWith('[') && line.endsWith(']')) {
        inLibraries = line == "[libraries]"
        return@forEachIndexed
      }
      if (!inLibraries || '=' !in line) return@forEachIndexed
      val alias = line.substringBefore('=').trim()
      val rhs = line.substringAfter('=').trim()
      val coordinate =
        Regex("""module\s*=\s*"(ee\.schimke\.[^"]+)"""").find(rhs)?.groupValues?.get(1)
          ?: run {
            val group =
              Regex("""group\s*=\s*"(ee\.schimke\.[^"]+)"""").find(rhs)?.groupValues?.get(1)
            val name = Regex("""name\s*=\s*"([^"]+)"""").find(rhs)?.groupValues?.get(1)
            if (group != null && name != null) "$group:$name" else null
          }
          ?: return@forEachIndexed
      val hasVersion = Regex("""\bversion(?:\.ref)?\s*=""").containsMatchIn(rhs)
      if (coordinate.endsWith("-bom")) {
        check(hasVersion) {
          "gradle/libs.versions.toml:${index + 1}: BOM '$alias' ($coordinate) must declare version or version.ref."
        }
        bomsByCoordinate[coordinate] = alias
      } else {
        libraries += Triple(alias, coordinate, hasVersion)
      }
    }

    val contractArtifacts =
      setOf(
        "agent-grant-protocol",
        "common-io",
        "component-catalog-protocol",
        "daemon-bta",
        "daemon-devices",
        "daemon-protocol",
        "data-layoutinspector-core",
        "data-preview-overrides-core",
        "data-render-core",
        "data-theme-core",
        "design-guidelines-protocol",
        "parity-issues-protocol",
        "screen-document",
        "ui-builder-protocol",
      )
    val daemonArtifacts =
      setOf(
        "daemon-android",
        "daemon-client",
        "daemon-connector-api",
        "daemon-core",
        "daemon-desktop",
        "lottie-preview-runtime",
        "preview-annotations",
        "preview-data-api",
        "slot-preview-runtime",
        "svg-preview-runtime",
      )

    fun expectedBomFor(coordinate: String): String {
      val group = coordinate.substringBefore(':')
      val artifact = coordinate.substringAfter(':')
      check(group == composeAiGroup) {
        "Unmapped BOM for '$coordinate' in gradle/libs.versions.toml; declare and wire its BOM."
      }
      val bomArtifact =
        when {
          artifact.startsWith("rc-player-") ||
            artifact.startsWith("rc-widget-") ||
            artifact.startsWith("rc-creation-") ||
            artifact.startsWith("third-party-rc-embedded-player") ||
            artifact.startsWith("third-party-remote-creation-") ||
            artifact == "remote-compose-player-js-dist" -> "rc-players-bom"
          artifact.startsWith("livery-") -> "livery-bom"
          artifact.startsWith("compose-preview-ui-builder-") -> "compose-preview-ui-builder-bom"
          artifact in contractArtifacts -> "compose-preview-contracts-bom"
          artifact in daemonArtifacts ||
            artifact.startsWith("data-") ||
            artifact.startsWith("renderer-") -> "compose-preview-daemon-bom"
          else -> "compose-ai-tools-bom"
        }
      return "$composeAiGroup:$bomArtifact"
    }

    val requiredBomByLibAccessor = mutableMapOf<String, Pair<String, String>>()
    for ((alias, coordinate, hasVersion) in libraries) {
      val expectedBom = expectedBomFor(coordinate)
      check(!hasVersion) {
        "gradle/libs.versions.toml: '$alias' ($coordinate) must be versionless and resolved via $expectedBom."
      }
      val bomAlias =
        bomsByCoordinate[expectedBom]
          ?: error(
            "gradle/libs.versions.toml: '$alias' ($coordinate) requires '$expectedBom' in [libraries]."
          )
      val libAccessor = alias.replace('-', '.').replace('_', '.')
      val bomAccessor = bomAlias.replace('-', '.').replace('_', '.')
      requiredBomByLibAccessor[libAccessor] = expectedBom to bomAccessor
    }

    val inlineCoordRegex =
      Regex(""""(ee\.schimke\.[A-Za-z0-9._-]+:[A-Za-z0-9._-]+(?::[^"\s]+)?)\"""")
    val allowedInlineCoordinates = setOf("$composeAiGroup:ui-builder-renderer-sdk:0.0.0")

    for (file in buildFiles.files.sortedBy { it.path }) {
      val stripped =
        file
          .readText()
          .replace(Regex("""/\*[\s\S]*?\*/"""), "")
          .lineSequence()
          .map { it.substringBefore("//") }
          .joinToString("\n")

      for (match in inlineCoordRegex.findAll(stripped)) {
        val coord = match.groupValues[1]
        if (coord in allowedInlineCoordinates) continue
        error(
          "${file.path}: inline coordinate \"$coord\" is forbidden; declare versionless in gradle/libs.versions.toml and import its platform BOM."
        )
      }

      for ((libAccessor, bomSpec) in requiredBomByLibAccessor) {
        val (expectedBom, bomAccessor) = bomSpec
        if (Regex("""\blibs\.${Regex.escape(libAccessor)}\b""").containsMatchIn(stripped)) {
          val importsBom =
            Regex("""platform\(\s*libs\.${Regex.escape(bomAccessor)}(?:\.get\(\))?\s*\)""")
              .containsMatchIn(stripped)
          check(importsBom) {
            "${file.path} uses libs.$libAccessor without importing platform(libs.$bomAccessor) ($expectedBom)."
          }
        }
      }
    }
  }
}

val verifyYschimkeBoms by
  tasks.registering(VerifyYschimkeBomsTask::class) {
    group = "verification"
    description = "Verifies that every ee.schimke.* dependency resolves versionless through a BOM."
    versionCatalog.set(layout.projectDirectory.file("gradle/libs.versions.toml"))
    buildFiles.from(
      fileTree(rootDir) {
        include("build.gradle.kts", "**/build.gradle.kts")
        exclude(
          "**/build/**",
          "**/.gradle/**",
          "**/node_modules/**",
          ".claude/**",
          ".worktrees/**",
        )
      }
    )
  }

subprojects {
  tasks
    .matching { it.name == "preBuild" || it.name == "check" || it.name == "test" }
    .configureEach { dependsOn(verifyYschimkeBoms) }
}
