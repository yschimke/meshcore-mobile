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
    id("ee.schimke.composepreview.plugin") version "0.1.0"
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

android {
    namespace = "ee.schimke.meshcore.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        // Keep the original applicationId so existing installs upgrade
        // in place instead of landing side-by-side as a second icon.
        applicationId = "ee.schimke.meshcore.sample"
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
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.remote.core)
    implementation(libs.androidx.remote.creation)
    implementation(libs.androidx.remote.creation.compose)
    implementation(libs.androidx.remote.tooling.preview)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.appfunctions)
    implementation(libs.androidx.appfunctions.service)
    ksp(libs.androidx.appfunctions.compiler)
    debugImplementation(libs.compose.uiTooling)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.testExt.junit)
}
