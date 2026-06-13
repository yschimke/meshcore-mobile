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
