@file:Suppress("UnstableApiUsage")

rootProject.name = "Meshcore"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
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
        // androidx.dev snapshots for Remote Compose / Wear remote-material3.
        // Stable releases (alpha11 / alpha04) ship a remote-creation-compose
        // whose Action.Companion.getEmpty() the compose-ai-tools renderer calls
        // at runtime, so previews fail with NoSuchMethodError. The tip-of-tree
        // snapshot has it; scoped to the two remote groups + snapshots only so
        // nothing else resolves from here.
        maven("https://androidx.dev/snapshots/latest/artifacts/repository") {
            mavenContent {
                includeGroupAndSubgroups("androidx.compose.remote")
                includeGroupAndSubgroups("androidx.wear.compose.remote")
                snapshotsOnly()
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
include(":meshcore-data")
include(":meshcore-devices-proto")
include(":meshcore-components")
include(":meshcore-mobile")
include(":meshcore-cli")
include(":meshcore-tui")
include(":meshcore-grpc-service")
include(":app")
include(":wear")
