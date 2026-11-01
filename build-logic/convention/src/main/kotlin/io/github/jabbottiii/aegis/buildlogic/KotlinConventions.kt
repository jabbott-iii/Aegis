package io.github.jabbottiii.aegis.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

internal fun Project.javaVersion(): JavaVersion = JavaVersion.toVersion(libs.versionOf("jvm-target"))

/** Kotlin compiler settings shared by JVM and Android modules. */
internal fun Project.configureKotlinCompiler() {
    val target = JvmTarget.fromTarget(libs.versionOf("jvm-target"))
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(target)
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }
}

/**
 * Pure JVM modules compile against the JDK [jvm-target] API only, even when Gradle runs on a newer
 * JDK, so that nothing slips in that Android can't run.
 */
internal fun Project.configureKotlinJvm() {
    val release = libs.versionOf("jvm-target")
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = javaVersion()
        targetCompatibility = javaVersion()
    }
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(release.toInt())
    }
    configureKotlinCompiler()
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions.freeCompilerArgs.add("-Xjdk-release=$release")
    }
}

/** JUnit 5 on the JVM. Android modules keep JUnit 4, which Robolectric and AndroidX Test need. */
internal fun Project.configureJvmTests() {
    dependencies {
        "testImplementation"(platform(libs.library("junit5-bom")))
        "testImplementation"(libs.library("junit5-jupiter"))
        "testImplementation"(libs.library("kotlin-test"))
        "testRuntimeOnly"(libs.library("junit5-platform-launcher"))
    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
