// Every artifact here runs with full privileges in every build, so each repository serves only the
// groups it must. The Plugin Portal is restricted to the two groups that only it publishes.
pluginManagement {
    repositories {
        exclusiveContent {
            forRepository { gradlePluginPortal() }
            filter { includeGroupByRegex("org\\.gradle\\.kotlin.*") }
        }
        mavenCentral()
    }
}

dependencyResolutionManagement {
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
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
