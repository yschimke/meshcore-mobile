plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKotlinMultiplatformLibrary)
}

// Java 17 bytecode so the JDK-17 preview render daemon can load these classes;
// build toolchain stays on JDK 21. See :meshcore-core for the full rationale.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
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
