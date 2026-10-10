plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKotlinMultiplatformLibrary)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.kotlinSerialization)
  // compose-preview (0.15.3) does NOT auto-inject into
  // com.android.kotlin.multiplatform.library modules — it skips them so a
  // non-renderable one (e.g. :meshcore-mobile) can't fail the desktop render —
  // so a KMP-Android library that *does* have previews must apply the plugin
  // explicitly to expose its desktop previews (DeviceBody) to design-parity.
  alias(libs.plugins.composePreview)
}

kotlin {
  jvmToolchain(21)

  android {
    namespace = "ee.schimke.meshcore.components"
    compileSdk {
      version = release(libs.versions.android.compileSdk.get().toInt()) { minorApiLevel = 1 }
    }
    minSdk = libs.versions.android.minSdk.get().toInt()
    // Generate R for the Google Fonts cert array used by the Android font actual.
    androidResources.enable = true
  }

  // Desktop (JVM) target for shared presentation and design-parity. Android
  // activity and platform integrations stay in androidMain.
  jvm("desktop")
  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class) wasmJs { browser() }

  sourceSets {
    commonMain.dependencies {
      api(projects.meshcoreCore)
      implementation(libs.kotlinx.coroutines.core)
      // Reusable Compose UI primitives, multiplatform (Android + desktop).
      api(libs.compose.runtime)
      api(libs.compose.foundation)
      api(libs.compose.material3)
      api(libs.compose.ui)
      api(libs.compose.uiToolingPreview)
      api(libs.cmp.navigation3.runtime)
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.cmp.material.icons.extended)
      implementation(libs.cmp.navigation3.ui)
      implementation(libs.cmp.adaptive)
      implementation(libs.cmp.navigation.suite)
      api(libs.rc.player.compose)
      // Compose Multiplatform string resources: UI copy resolves from the shared
      // commonMain/composeResources/values*/strings.xml, so the daemon localizes per locale.
      // Exposed as api (not implementation) so consumers get the generated public Res +
      // stringResource API on their compile classpath — :app reuses these same strings.
      api(libs.compose.components.resources)
    }
    // Catalog annotations currently publish Android/JVM variants, but no Wasm variant.
    val catalogMain by creating {
      dependsOn(commonMain.get())
      dependencies { implementation(libs.compose.preview.annotations) }
    }
    getByName("desktopMain").dependsOn(catalogMain)
    val androidMain by getting {
      dependsOn(catalogMain)
      dependencies {
        implementation(libs.kotlinx.coroutines.android)
        // Android-only Compose extras (extended icons live on Android; the
        // shared composables use the vendored MeshIcons instead).
        api(libs.compose.material.icons.extended)
        api(libs.androidx.activity.compose)
        api(libs.androidx.core.ktx)
        // Downloadable Google Fonts provider for the branded faces (Android).
        implementation(libs.compose.ui.text.google.fonts)
      }
    }
  }
}

// Generate a PUBLIC `Res` accessor so :app catalog previews (and any consumer) resolve
// the shared string resources too — mirrors :samples:design-catalog-m3-shared.
compose.resources {
  publicResClass = true
  packageOfResClass = "ee.schimke.meshcore.components.generated.resources"
}
