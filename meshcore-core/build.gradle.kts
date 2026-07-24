plugins {
  // Unified AGP-9 plugin replacing com.android.library + kotlinMultiplatform.
  // Android target is configured via `kotlin { android { ... } }` below.
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKotlinMultiplatformLibrary)
}

// Emit Java 17 bytecode (v61) so the JDK-17 preview render daemon (Robolectric,
// preview.coo.ee) can load these classes; the build toolchain stays on JDK 21.
// v65 (JDK 21) bytecode throws UnsupportedClassVersionError in the render daemon.
// On the KMP extension jvmTarget isn't on the shared (common) compilerOptions, so
// pin it on the JVM/Android Kotlin compile tasks directly.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "ee.schimke.meshcore.core"
    //noinspection GradleDependency
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()
  }
  jvm()

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
