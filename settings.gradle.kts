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
// Auth: the token is resolved from the first non-blank of, in order:
//   1. env  BUILDFETCH_MESHCORE_GRADLE_REMOTE_CACHE_TOKEN   (project-specific)
//   2. prop BUILDFETCH_MESHCORE_GRADLE_REMOTE_CACHE_TOKEN
//   3. env  BUILDFETCH_GRADLE_REMOTE_CACHE_TOKEN            (shared / general fallback)
//   4. prop BUILDFETCH_GRADLE_REMOTE_CACHE_TOKEN
// The project-specific name lets a developer hold a separate readonly token per BuildFetch project
// in a single ~/.gradle/gradle.properties (this repo and compose-ai-tools point at different caches,
// so one shared token can't authenticate both). The general name stays as a fallback — it's what CI
// exports (see .github/workflows/ci.yml) and what a single-project setup can use. Env wins over a
// gradle property of the same name so CI overrides a stray local property.
//
// The token is treated as absent unless it is non-blank. CI declares the env var unconditionally
// (`BUILDFETCH_GRADLE_REMOTE_CACHE_TOKEN: ${{ secrets.… }}`), so an unprovisioned secret or a fork
// PR exports it as an empty string — which Gradle's `environmentVariable(...)` still reports as
// *present*. Filtering each source for blanks independently preserves the intended no-op (empty ⇒
// cache disabled) and lets a later source take over even when an earlier one is present-but-empty.
// When nothing resolves the cache disables itself (isEnabled below), so fork PRs and un-provisioned
// checkouts fall back to the local cache with no error.
//
// Push: writes are restricted to trusted CI builds. CI sets ON_CI=true only on main-branch runs, so
// PRs and developer machines are read-only. The gate is value-based (not env-var presence) so an
// explicit ON_CI=false is honoured as read-only.
val onCi = providers.environmentVariable("ON_CI").orElse("false").get().toBoolean()
buildCache {
    // On the trusted main-branch runs, CI is the sole writer of the BuildFetch remote cache — and the
    // only thing that populates it for every other consumer (PRs, developer machines). Gradle never
    // re-uploads a *local* build-cache hit to the remote; it pushes to the remote only when a task
    // actually executes. setup-gradle restores a warm local build cache (caches/build-cache-1) from
    // the GitHub Actions cache, so without this every task resolves as FROM-CACHE (local), nothing is
    // pushed, and the remote stays empty (dev machines then see 0 remote hits). Disabling the local
    // cache on the pushing runs forces tasks to execute-and-push, or to hit the remote directly, so
    // BuildFetch actually gets seeded. Off-CI and on PRs the local cache stays on (harmless — those
    // runs don't push, and a redundant local cache just makes them faster).
    local {
        isEnabled = !onCi
    }
    remote<HttpBuildCache> {
        url = uri("https://cache.eu-central-a.buildfetch.com/vuFQad/gradle/")

        credentials {
            username = "token-auth"
            // Non-blank view of a single env var / gradle property: trims and drops empties so a
            // present-but-empty source doesn't shadow a later fallback (see the header comment).
            val nonBlank = { source: Provider<String> ->
                source.map { it.trim() }.filter { it.isNotEmpty() }
            }
            password =
                nonBlank(providers.environmentVariable("BUILDFETCH_MESHCORE_GRADLE_REMOTE_CACHE_TOKEN"))
                    .orElse(nonBlank(providers.gradleProperty("BUILDFETCH_MESHCORE_GRADLE_REMOTE_CACHE_TOKEN")))
                    .orElse(nonBlank(providers.environmentVariable("BUILDFETCH_GRADLE_REMOTE_CACHE_TOKEN")))
                    .orElse(nonBlank(providers.gradleProperty("BUILDFETCH_GRADLE_REMOTE_CACHE_TOKEN")))
                    .orNull
        }

        isPush = onCi

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
