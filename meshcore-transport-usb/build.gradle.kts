plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKotlinMultiplatformLibrary)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "ee.schimke.meshcore.transport.usb"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()
  }
  jvm()

  sourceSets {
    commonMain.dependencies {
      api(projects.meshcoreCore)
      implementation(libs.kotlinx.coroutines.core)
      api(libs.mcarr.usb)
    }
    val androidMain by getting { dependencies { implementation(libs.mcarr.usb.android) } }
    val jvmMain by getting { dependencies { implementation(libs.jserialcomm) } }
  }
}
