import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask
import java.nio.file.Files

plugins {
    alias(libs.plugins.kotlinJvm)
    application
    alias(libs.plugins.graalvmNative)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

dependencies {
    implementation(projects.meshcoreCore)
    implementation(projects.meshcoreTransportTcp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.clikt)
    implementation(libs.mordant)
    implementation(libs.mordant.coroutines)
}

application {
    mainClass.set("ee.schimke.meshcore.tui.MainKt")
}

graalvmNative {
    binaries {
        named("main") {
            mainClass.set("ee.schimke.meshcore.tui.MainKt")
            imageName.set("meshcore-tui")
            buildArgs.addAll(
                "--no-fallback",
                "-H:+UnlockExperimentalVMOptions",
                "-H:+ReportExceptionStackTraces",
            )
            javaLauncher.set(
                javaToolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(25))
                    vendor.set(JvmVendorSpec.GRAAL_VM)
                },
            )
        }
    }
    metadataRepository { enabled.set(true) }
}

// square/okhttp#9393 — recreate symlinks broken by Gradle toolchain provisioning
// (https://github.com/gradle/gradle/issues/28583).
tasks.named<BuildNativeImageTask>("nativeCompile") {
    val toolchainDir = options.get().javaLauncher.get().executablePath.asFile.parentFile.run {
        if (name == "bin") parentFile else this
    }
    val toolchainFiles = toolchainDir.walkTopDown().filter { it.isFile }
    val emptyFiles = toolchainFiles.filter { it.length() == 0L }
    val links = toolchainFiles.mapNotNull { file ->
        emptyFiles.singleOrNull { it != file && it.name == file.name }?.let { file to it }
    }
    links.forEach { (target, link) ->
        logger.quiet("Fixing up '$link' to link to '$target'.")
        if (link.delete()) {
            Files.createSymbolicLink(link.toPath(), target.toPath())
        } else {
            logger.warn("Unable to delete '$link'.")
        }
    }
}
