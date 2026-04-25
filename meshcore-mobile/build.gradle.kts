plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKotlinMultiplatformLibrary)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.metro)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "ee.schimke.meshcore.mobile"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()
  }

  sourceSets {
    commonMain.dependencies {
      api(projects.meshcoreCore)
      implementation(libs.kotlinx.coroutines.core)
    }
    val androidMain by getting {
      dependencies {
        api(projects.meshcoreComponents)
        implementation(libs.kotlinx.coroutines.android)
        api(projects.meshcoreTransportBle)
        api(projects.meshcoreTransportUsb)
      }
    }
  }
}
