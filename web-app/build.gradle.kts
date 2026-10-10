plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
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
    }
  }
}
