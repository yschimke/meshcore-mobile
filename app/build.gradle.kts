import com.github.triplet.gradle.androidpublisher.ReleaseStatus

val appVersionName = "0.1.0" // x-release-please-version

// Pack MAJOR.MINOR.PATCH into a monotonic int. Caps at major < 22.
val appVersionCode: Int =
  run {
      val parts = appVersionName.split(".", "-").mapNotNull { it.toIntOrNull() }
      val major = parts.getOrNull(0) ?: 0
      val minor = parts.getOrNull(1) ?: 0
      val patch = parts.getOrNull(2) ?: 0
      major * 10_000 + minor * 100 + patch
    }
    .coerceAtLeast(1)

plugins {
  // AGP 9 has built-in Kotlin support, so `com.android.application`
  // alone covers both Android and Kotlin compilation — don't add
  // `org.jetbrains.kotlin.android` on top (it's rejected as redundant).
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.metro)
  alias(libs.plugins.ksp)
  alias(libs.plugins.kotlinSerialization)
  alias(libs.plugins.composePreview)
  alias(libs.plugins.playPublisher)
}

play {
  track.set("internal")
  defaultToAppBundles.set(true)
  releaseStatus.set(ReleaseStatus.COMPLETED)
  // Skip API calls in CI runs that build but don't publish (e.g. PRs).
  enabled.set(System.getenv("ANDROID_PUBLISHER_CREDENTIALS") != null)
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) } }

android {
  namespace = "ee.schimke.meshcore.app"
  compileSdk = libs.versions.android.compileSdk.get().toInt()
  defaultConfig {
    applicationId = "ee.schimke.meshcore"
    minSdk = libs.versions.android.minSdk.get().toInt()
    targetSdk = libs.versions.android.targetSdk.get().toInt()
    versionCode = appVersionCode
    versionName = appVersionName
  }
  val releaseKeystorePath = System.getenv("MESHCORE_KEYSTORE_PATH")
  signingConfigs {
    if (releaseKeystorePath != null) {
      create("release") {
        storeFile = file(releaseKeystorePath)
        storePassword = System.getenv("MESHCORE_KEYSTORE_PASSWORD")
        keyAlias = System.getenv("MESHCORE_KEY_ALIAS")
        keyPassword = System.getenv("MESHCORE_KEY_PASSWORD")
      }
    }
  }
  buildTypes {
    getByName("release") {
      isMinifyEnabled = false
      if (releaseKeystorePath != null) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
  testOptions.unitTests { isIncludeAndroidResources = true }
}

dependencies {
  implementation(projects.meshcoreGrpcService)
  implementation(libs.grpc.binder)
  implementation(libs.androidx.lifecycle.service)
  implementation(libs.horologist.datalayer.grpc)
  implementation(libs.horologist.datalayer.phone)
  implementation(libs.playservices.wearable)
  implementation(projects.meshcoreCore)
  implementation(projects.meshcoreTransportTcp)
  implementation(projects.meshcoreTransportBle)
  implementation(projects.meshcoreTransportUsb)
  implementation(projects.meshcoreComponents)
  implementation(projects.meshcoreMobile)
  implementation(projects.meshcoreData)
  implementation(libs.metro.runtime)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.androidx.activity.compose)
  implementation(libs.compose.runtime)
  implementation(libs.compose.foundation)
  implementation(libs.compose.material3)
  implementation(libs.compose.material.icons.extended)
  implementation(libs.compose.ui.text.google.fonts)
  implementation(libs.compose.ui)
  implementation(libs.compose.uiToolingPreview)
  implementation(libs.androidx.lifecycle.runtimeCompose)
  implementation(libs.androidx.lifecycle.viewmodelCompose)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.navigation3.ui)
  implementation(projects.meshcoreDevicesProto)
  implementation(libs.androidx.datastore)
  implementation(libs.androidx.remote.core)
  implementation(libs.androidx.remote.creation)
  implementation(libs.androidx.remote.creation.compose)
  implementation(libs.androidx.remote.tooling.preview)
  implementation(libs.androidx.work.runtime)
  implementation(libs.wear.compose.remote.material3)
  implementation(libs.androidx.appfunctions)
  implementation(libs.androidx.appfunctions.service)
  implementation(libs.aboutlibraries.compose.m3)
  ksp(libs.androidx.appfunctions.compiler)
  debugImplementation(libs.compose.uiTooling)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.testExt.junit)
}
