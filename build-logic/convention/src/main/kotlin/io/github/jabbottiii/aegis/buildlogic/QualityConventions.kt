package io.github.jabbottiii.aegis.buildlogic

import app.cash.licensee.LicenseeExtension
import app.cash.licensee.UnusedAction
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/**
 * detekt, ktlint, and the license allowlist (DEVELOPMENT.md § CI gates). These gates are not
 * configurable per module: never add baselines, suppressions, or `ignoreFailures` here.
 */
internal fun Project.configureQualityGates() {
    configureDetekt()
    configureKtlint()
    configureLicensee()
}

private fun Project.configureDetekt() {
    pluginManager.apply("io.gitlab.arturbosch.detekt")
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootDir.resolve("config/detekt/detekt.yml"))
        // Every source set, including src/*/java and future flavors.
        source.setFrom(fileTree("src") { include("**/*.kt", "**/*.kts") })
        parallel = true
    }
    // detekt 1.23 must run on the Kotlin compiler it was built with, not the project's version.
    val detektKotlin = libs.versionOf("detekt-kotlin")
    configurations.matching { it.name == "detekt" }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") useVersion(detektKotlin)
        }
    }
    tasks.withType<Detekt>().configureEach {
        jvmTarget = libs.versionOf("jvm-target")
        reports {
            html.required.set(true)
            sarif.required.set(true)
            xml.required.set(false)
            txt.required.set(false)
            md.required.set(false)
        }
    }
}

private fun Project.configureKtlint() {
    pluginManager.apply("org.jlleitschuh.gradle.ktlint")
    // Generated sources (KSP, Hilt) live under the module's build directory.
    val generated = layout.buildDirectory.get().asFile
    extensions.configure<KtlintExtension> {
        version.set(libs.versionOf("ktlint"))
        android.set(false)
        filter {
            exclude { it.file.startsWith(generated) }
        }
    }
}

/** Licenses allowed in shipped dependencies. Adding one needs the owner's approval. */
private val ALLOWED_SPDX = listOf("Apache-2.0", "MIT", "BSD-2-Clause", "BSD-3-Clause")

private fun Project.configureLicensee() {
    pluginManager.apply("app.cash.licensee")
    extensions.configure<LicenseeExtension> {
        ALLOWED_SPDX.forEach { allow(it) }
        // The allowlist is project-wide, so most modules use only part of it.
        unusedAction(UnusedAction.IGNORE)
    }
}
