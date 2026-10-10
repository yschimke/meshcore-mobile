plugins {
  // Unified AGP-9 plugin replacing com.android.library + kotlinMultiplatform.
  // Android target is configured via `kotlin { android { ... } }` below.
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKotlinMultiplatformLibrary)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "ee.schimke.meshcore.core"
    //noinspection GradleDependency
    compileSdk {
      version = release(libs.versions.android.compileSdk.get().toInt()) { minorApiLevel = 1 }
    }
    minSdk = libs.versions.android.minSdk.get().toInt()
  }
  jvm()
  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class) wasmJs { browser() }

  sourceSets {
    commonMain.dependencies {
      implementation(libs.kotlinx.coroutines.core)
      api(libs.kotlinx.io.core)
      api(libs.kotlinx.io.bytestring)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
    }
  }
}
