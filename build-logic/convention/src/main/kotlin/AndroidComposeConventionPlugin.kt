import com.android.build.api.dsl.CommonExtension
import io.github.jabbottiii.aegis.buildlogic.library
import io.github.jabbottiii.aegis.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/** Compose compiler, the Compose BOM, and preview tooling. Apply after an aegis.android.* plugin. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.withPlugin("com.android.base") {
                pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
                extensions.getByType<CommonExtension>().buildFeatures.compose = true
                dependencies {
                    val bom = platform(libs.library("androidx-compose-bom"))
                    "implementation"(bom)
                    "androidTestImplementation"(bom)
                    "implementation"(libs.library("androidx-compose-ui-tooling-preview"))
                    "debugImplementation"(libs.library("androidx-compose-ui-tooling"))
                }
            }
        }
    }
}
