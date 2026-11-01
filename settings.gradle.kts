// The aegis.* plugins come from build-logic, but their own dependencies (AGP, KGP, detekt, ...)
// resolve through these repositories. Keep them in step with build-logic/settings.gradle.kts. The
// Plugin Portal serves only the one group that it alone publishes.
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("com\\.google\\.testing\\.platform.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        exclusiveContent {
            forRepository { gradlePluginPortal() }
            filter { includeGroup("org.jlleitschuh.gradle") }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("com\\.google\\.testing\\.platform.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "Aegis"

// Module layout: docs/ARCHITECTURE.md §3. Add modules with /new-module as their phase begins.
include(":app")
include(":core:common")
include(":core:data")
include(":core:model")
include(":core:security")
include(":core:ui")
include(":testing:common")
