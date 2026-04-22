plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.aboutlibraries)
    id("ee.schimke.composeai.preview") version "0.7.5"
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

android {
    namespace = "ee.schimke.meshcore.wear"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "ee.schimke.meshcore.wear"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1"
    }
    buildTypes {
        getByName("release") { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    testOptions.unitTests {
        isIncludeAndroidResources = true
    }
}

dependencies {
    // gRPC proto stubs — the watch is a pure gRPC consumer
    implementation(projects.meshcoreGrpcService)

    // Horologist: gRPC transport over Wearable Data Layer (NOT Compose)
    implementation(libs.horologist.datalayer.grpc)
    implementation(libs.horologist.datalayer.watch)
    implementation(libs.playservices.wearable)

    // Wear Compose Material 3 (direct, no Horologist UI)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.wear.compose.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)

    // Remote Compose (for widget, same libraries as phone widget)
    implementation(libs.androidx.remote.core)
    implementation(libs.androidx.remote.creation)
    implementation(libs.androidx.remote.creation.compose)
    implementation(libs.androidx.remote.tooling.preview)
    // Wear Remote Compose Material 3 components
    implementation(libs.wear.compose.remote.material3)

    // Standard Compose + lifecycle
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.uiToolingPreview)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.text.google.fonts)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.aboutlibraries.compose.wear.m3)
    implementation(libs.kotlinx.coroutines.android)

    implementation("androidx.wear.compose:compose-ui-tooling:1.6.0")
    debugImplementation(libs.compose.uiTooling)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.testExt.junit)
}
