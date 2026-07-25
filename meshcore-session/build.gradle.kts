plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKotlinMultiplatformLibrary)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "ee.schimke.meshcore.session"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()
  }
  jvm()

  sourceSets {
    commonMain.dependencies {
      api(projects.meshcoreCore)
      api(projects.meshcoreData)
      // Transport implementations whose construction this module centralizes.
      // BLE and USB expose their core types from commonMain; TCP is JVM-only
      // and is wired per platform below.
      api(projects.meshcoreTransportBle)
      api(projects.meshcoreTransportUsb)
      implementation(libs.kotlinx.coroutines.core)
    }
    val androidMain by getting { dependencies { implementation(projects.meshcoreTransportTcp) } }
    val jvmMain by getting {
      dependencies {
        implementation(projects.meshcoreTransportTcp)
        implementation(libs.jserialcomm)
      }
    }
  }
}
