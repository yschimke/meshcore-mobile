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
    }
  }
}

compose.desktop {
  application {
    mainClass = "ee.schimke.meshcore.desktop.MainKt"
    nativeDistributions {
      targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
      packageName = "MeshCore"
      packageVersion = "1.0.0"
    }
  }
}
