plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKotlinMultiplatformLibrary)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
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
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()
    // Generate R for the Google Fonts cert array used by the Android font actual.
    androidResources.enable = true
  }

  // Desktop (JVM) target: lets the shared presentational composables render on
  // the cheaper CMP/Skiko path (design-parity). Android-only deps (extended
  // icons, transports, activity) stay in androidMain.
  jvm("desktop")

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
      // MeshCore's alternative themes are declared in shared code via @ThemeCatalog
      // (MeshcoreThemeCatalogs), so the annotations must be reachable from commonMain — hence the
      // KMP variant of preview-annotations (0.16.14+). Reflection-only markers, no runtime
      // footprint.
      implementation(libs.compose.preview.annotations)
      // Compose Multiplatform string resources: UI copy resolves from the shared
      // commonMain/composeResources/values*/strings.xml, so the daemon localizes per locale.
      // Exposed as api (not implementation) so consumers get the generated public Res +
      // stringResource API on their compile classpath — :app reuses these same strings.
      api(libs.compose.components.resources)
    }
    val androidMain by getting {
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
