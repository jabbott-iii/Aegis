package io.github.jabbottiii.aegis.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

/** Base package and applicationId (decided at the Phase 0 scaffold, recorded in AGENTS.md and
 * ADR-0007). */
internal const val BASE_PACKAGE = "io.github.jabbottiii.aegis"

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.versionOf(alias: String): String = findVersion(alias).get().requiredVersion

internal fun VersionCatalog.intVersionOf(alias: String): Int = versionOf(alias).toInt()

internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> = findLibrary(alias).get()

/** `:core:ui` becomes `io.github.jabbottiii.aegis.core.ui`. */
internal val Project.aegisNamespace: String
    get() = BASE_PACKAGE + path.replace(':', '.')
