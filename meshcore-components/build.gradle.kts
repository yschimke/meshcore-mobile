plugins {
  // AGP 9 has built-in Kotlin support, so `com.android.library` alone covers
  // both Android and Kotlin compilation (no `org.jetbrains.kotlin.android`).
  alias(libs.plugins.androidLibrary)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
}

android {
  namespace = "ee.schimke.meshcore.components"
  compileSdk = libs.versions.android.compileSdk.get().toInt()
  defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
}

kotlin { jvmToolchain(21) }

dependencies {
  api(projects.meshcoreCore)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)
  // Reusable Compose UI primitives for mobile apps. These are part of the
  // public surface (callers theme/host these components), so `api`.
  // Note: this module deliberately has no `meshcore-transport-*` dependency —
  // scanner/port panels take transport-agnostic DTOs so a TCP-only consumer
  // never pulls in BLE/USB transports.
  api(libs.compose.runtime)
  api(libs.compose.foundation)
  api(libs.compose.material3)
  api(libs.compose.material.icons.extended)
  api(libs.compose.ui)
  api(libs.compose.uiToolingPreview)
  api(libs.androidx.activity.compose)
  api(libs.androidx.core.ktx)
}
