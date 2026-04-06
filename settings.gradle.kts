@file:Suppress("UnstableApiUsage")

rootProject.name = "Meshcore"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/yschimke/precognition")
            credentials {
                username = "yschimke"
                password = "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"
            }
        }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven("https://jitpack.io")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":meshcore-core")
include(":meshcore-transport-tcp")
include(":meshcore-transport-ble")
include(":meshcore-transport-usb")
include(":meshcore-devices-proto")
include(":meshcore-mobile")
include(":meshcore-cli")
include(":meshcore-tui")
include(":app")