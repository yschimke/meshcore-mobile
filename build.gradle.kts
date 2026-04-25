plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.metro) apply false
    alias(libs.plugins.graalvmNative) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.ktfmt) apply false
}

subprojects {
    apply(plugin = rootProject.libs.plugins.ktfmt.get().pluginId)
    extensions.configure<com.ncorti.ktfmt.gradle.KtfmtExtension> {
        googleStyle()
    }
}

tasks.register("installGitHooks") {
    group = "git hooks"
    description = "Installs the project's git hooks into .git/hooks"
    val source = file("scripts/git-hooks/pre-commit")
    val target = file(".git/hooks/pre-commit")
    inputs.file(source)
    outputs.file(target)
    doLast {
        target.parentFile.mkdirs()
        source.copyTo(target, overwrite = true)
        target.setExecutable(true)
        println("Installed git pre-commit hook -> ${target.relativeTo(rootDir)}")
    }
}
