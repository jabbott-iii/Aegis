plugins {
    `kotlin-dsl`
}

group = "io.github.jabbottiii.aegis.buildlogic"

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.compose.compiler.gradle.plugin)
    implementation(libs.ksp.gradle.plugin)
    implementation(libs.hilt.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.ktlint.gradle.plugin)
    implementation(libs.kover.gradle.plugin)
    implementation(libs.licensee.gradle.plugin)

    // Plain JUnit assertions: kotlin-test would have to match Gradle's embedded Kotlin version.
    testImplementation(platform(libs.junit5.bom))
    testImplementation(libs.junit5.jupiter)
    testRuntimeOnly(libs.junit5.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "aegis.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "aegis.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "aegis.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("jvmLibrary") {
            id = "aegis.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("jvmEngine") {
            id = "aegis.jvm.engine"
            implementationClass = "JvmEngineConventionPlugin"
        }
        register("hilt") {
            id = "aegis.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("root") {
            id = "aegis.root"
            implementationClass = "RootConventionPlugin"
        }
    }
}
