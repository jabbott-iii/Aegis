import io.github.jabbottiii.aegis.buildlogic.library
import io.github.jabbottiii.aegis.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Hilt with KSP for Android modules. Apply after an aegis.android.* plugin. */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.withPlugin("com.android.base") {
                pluginManager.apply("com.google.devtools.ksp")
                pluginManager.apply("com.google.dagger.hilt.android")
                dependencies {
                    "implementation"(libs.library("hilt-android"))
                    "ksp"(libs.library("hilt-compiler"))
                }
            }
        }
    }
}
