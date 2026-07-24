plugins { alias(libs.plugins.kotlinJvm) }

// Java 17 bytecode so the JDK-17 preview render daemon can load this module —
// :app pulls it in transitively via :meshcore-session's androidMain. The Java
// toolchain below stays on JDK 21 (build JDK).
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

// Pin javac to release 17 to match the Kotlin target above (the kotlin-jvm plugin
// registers a compileJava task; the JDK-21 toolchain would otherwise target 21
// and trip the Kotlin/Java JVM-target consistency check).
tasks.withType<JavaCompile>().configureEach { options.release.set(17) }

dependencies {
  api(projects.meshcoreCore)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.ktor.network)
}
