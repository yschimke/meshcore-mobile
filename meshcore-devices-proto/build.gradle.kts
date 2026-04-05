plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.wire)
}

kotlin {
    jvmToolchain(21)
}

wire {
    // Pure-Kotlin output: Wire generates idiomatic Kotlin data classes
    // with ADAPTER companions that DataStore's Serializer can use
    // directly.
    kotlin {}
}

dependencies {
    api(libs.wire.runtime)
}
