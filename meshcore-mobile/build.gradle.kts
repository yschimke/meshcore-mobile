plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKotlinMultiplatformLibrary)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
}

// Java 17 bytecode so the JDK-17 preview render daemon can load these classes;
// build toolchain stays on JDK 21. See :meshcore-core for the full rationale.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
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
        // The Android integration layer: reusable UI + transports + the
        // shared session factory wired together for host apps.
        api(projects.meshcoreComponents)
        api(projects.meshcoreSession)
        implementation(projects.meshcoreData)
        implementation(libs.kotlinx.coroutines.android)
        api(projects.meshcoreTransportBle)
        api(projects.meshcoreTransportUsb)
      }
    }
  }
}
