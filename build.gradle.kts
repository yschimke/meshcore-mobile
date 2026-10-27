// Match the app module's root-relative check, including when Gradle starts elsewhere.
// Builds without Firebase configuration resolve neither its tooling nor SDKs.
buildscript {
  if (rootDir.resolve("app/google-services.json").isFile) {
    repositories {
      google()
      mavenCentral()
      gradlePluginPortal()
    }
    dependencies {
      classpath("com.google.gms:google-services:4.5.0")
      classpath("com.google.firebase:firebase-crashlytics-gradle:3.0.8")
    }
  }
}

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.metro) apply false
    alias(libs.plugins.graalvmNative) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.ktfmt) apply false
    alias(libs.plugins.tapmoc) apply false
}

subprojects {
    apply(plugin = rootProject.libs.plugins.ktfmt.get().pluginId)
    extensions.configure<com.ncorti.ktfmt.gradle.KtfmtExtension> {
        googleStyle()
    }
}

// Wires .githooks/ as the repository's hooks directory. One-time bootstrap:
// `./gradlew installGitHooks`.
tasks.register<Exec>("installGitHooks") {
    group = "git hooks"
    description = "Configure git to use the .githooks directory in this repo."
    workingDir = rootDir
    commandLine("git", "config", "core.hooksPath", ".githooks")
}
