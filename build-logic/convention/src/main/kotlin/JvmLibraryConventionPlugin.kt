import io.github.jabbottiii.aegis.buildlogic.configureJvmTests
import io.github.jabbottiii.aegis.buildlogic.configureKotlinJvm
import io.github.jabbottiii.aegis.buildlogic.configureQualityGates
import org.gradle.api.Plugin
import org.gradle.api.Project

/** Plain Kotlin/JVM modules (for example `testing:common`). No Android framework. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("java-library")
            configureKotlinJvm()
            configureJvmTests()
            configureQualityGates()
        }
    }
}
