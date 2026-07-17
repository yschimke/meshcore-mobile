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
        mavenCentral()
        maven("https://jitpack.io")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// BuildFetch remote Gradle build cache. Complements the local build cache (org.gradle.caching=true
// in gradle.properties) by sharing task outputs across CI runs and developer machines.
//
// Auth: the token is read from the BUILDFETCH_GRADLE_REMOTE_CACHE_TOKEN environment variable (best
// for CI — see .github/actions/setup / .github/workflows/ci.yml) or a gradle property of the same
// name (best for a mixed IDE + terminal dev setup — put it in ~/.gradle/gradle.properties). When no
// token is resolvable the cache disables itself (isEnabled below), so fork PRs and un-provisioned
// checkouts fall back to the local cache with no error.
//
// Push: writes are restricted to trusted CI builds. CI sets ON_CI=true only on main-branch runs, so
// PRs and developer machines are read-only. The gate is value-based (not env-var presence) so an
// explicit ON_CI=false is honoured as read-only.
buildCache {
    remote<HttpBuildCache> {
        url = uri("https://cache.eu-central-a.buildfetch.com/vuFQad/gradle/")

        credentials {
            username = "token-auth"
            password =
                providers
                    .environmentVariable("BUILDFETCH_GRADLE_REMOTE_CACHE_TOKEN")
                    .orElse(providers.gradleProperty("BUILDFETCH_GRADLE_REMOTE_CACHE_TOKEN"))
                    .orNull
        }

        isPush = providers.environmentVariable("ON_CI").orElse("false").get().toBoolean()

        isEnabled = credentials.password != null
    }
}

include(":meshcore-core")
include(":meshcore-transport-tcp")
include(":meshcore-transport-ble")
include(":meshcore-transport-usb")
include(":meshcore-session")
include(":meshcore-data")
include(":meshcore-devices-proto")
include(":meshcore-components")
include(":meshcore-mobile")
include(":meshcore-cli")
include(":meshcore-tui")
include(":meshcore-grpc-service")
include(":app")
include(":wear")
