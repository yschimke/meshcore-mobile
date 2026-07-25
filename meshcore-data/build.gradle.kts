plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKotlinMultiplatformLibrary)
  alias(libs.plugins.ksp)
  alias(libs.plugins.room)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "ee.schimke.meshcore.data"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()
  }
  jvm()

  sourceSets {
    commonMain.dependencies {
      api(projects.meshcoreCore)
      implementation(libs.room.runtime)
      implementation(libs.sqlite.bundled)
      implementation(libs.kotlinx.coroutines.core)
    }
    jvmTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
      implementation(libs.room.runtime)
      implementation(libs.sqlite.bundled)
    }
  }
}

room { schemaDirectory("$projectDir/schemas") }

dependencies {
  add("kspAndroid", libs.room.compiler)
  add("kspJvm", libs.room.compiler)
}
