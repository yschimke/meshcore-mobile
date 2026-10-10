import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
}

kotlin {
  jvmToolchain(21)
  jvm()
  sourceSets {
    jvmMain.dependencies {
      implementation(projects.meshcoreComponents)
      implementation(projects.meshcoreTransportTcp)
      implementation(compose.desktop.currentOs)
      implementation(libs.kotlinx.coroutines.swing)
    }
  }
}

// Release jobs pass the tag version; local builds follow Android's release-please version.
val desktopVersion =
  providers
    .gradleProperty("desktopVersion")
    .orElse(
      providers
        .fileContents(rootProject.layout.projectDirectory.file("app/build.gradle.kts"))
        .asText
        .map { Regex("""val appVersionName = "([^"]+)"""").find(it)!!.groupValues[1] }
    )
    .get()

require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+").matches(desktopVersion)) {
  "desktopVersion must be MAJOR.MINOR.PATCH"
}

// jpackage requires a positive major on macOS. Offset the major on every OS
// to keep installer upgrades monotonic, including the eventual 1.0 release.
val installerVersion = desktopVersion.split(".").let { "${it[0].toInt() + 1}.${it[1]}.${it[2]}" }

compose.desktop {
  application {
    mainClass = "ee.schimke.meshcore.desktop.MainKt"
    nativeDistributions {
      targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
      packageName = "MeshCore"
      packageVersion = installerVersion
      includeAllModules = true
      description = "MeshCore desktop client"
      vendor = "Yuri Schimke"
      macOS { bundleID = "ee.schimke.meshcore.desktop" }
      windows {
        menu = true
        shortcut = true
        upgradeUuid = "cc07e2ce-0ef4-4359-b51e-e662e345cb03"
      }
      linux { menuGroup = "Network" }
    }
  }
}
