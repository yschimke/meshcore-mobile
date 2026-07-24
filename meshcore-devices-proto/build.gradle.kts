plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.wire)
}

kotlin {
  jvmToolchain(21)
  // Java 17 bytecode so the JDK-17 preview render daemon can load these classes
  // (:app depends on this module); build toolchain stays on JDK 21.
  compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

// The kotlin-jvm plugin registers a compileJava task; pin javac to release 17 so
// it matches the Kotlin target above (avoids the Kotlin/Java JVM-target mismatch).
tasks.withType<JavaCompile>().configureEach { options.release.set(17) }

wire {
  // Pure-Kotlin output: Wire generates idiomatic Kotlin data classes
  // with ADAPTER companions that DataStore's Serializer can use
  // directly.
  kotlin {}
}

dependencies { api(libs.wire.runtime) }
