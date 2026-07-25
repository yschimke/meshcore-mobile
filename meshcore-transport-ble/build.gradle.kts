plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKotlinMultiplatformLibrary)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "ee.schimke.meshcore.transport.ble"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()
  }
  jvm()

  sourceSets {
    commonMain.dependencies {
      api(projects.meshcoreCore)
      implementation(libs.kotlinx.coroutines.core)
      api(libs.kable.core)
    }
  }
}
