plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "ee.schimke.meshcore.components"
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
                implementation(libs.kotlinx.coroutines.android)
                api(projects.meshcoreTransportBle)
                api(projects.meshcoreTransportUsb)
                implementation(libs.mcarr.usb.android)
                // Reusable Compose UI primitives for mobile apps.
                api(libs.compose.runtime)
                api(libs.compose.foundation)
                api(libs.compose.material3)
                api(libs.compose.material.icons.extended)
                api(libs.compose.ui)
                api(libs.compose.uiToolingPreview)
                api(libs.androidx.activity.compose)
                api(libs.androidx.core.ktx)
            }
        }
    }
}
