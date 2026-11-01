import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/**
 * Pure Kotlin engine and core modules (ARCHITECTURE §3): explicit API mode, warnings as errors,
 * and the ≥ 80% line-coverage gate (NFR-MAINT-01).
 */
class JvmEngineConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("aegis.jvm.library")
            pluginManager.apply("org.jetbrains.kotlinx.kover")

            extensions.configure<KotlinJvmProjectExtension> {
                explicitApi()
            }
            tasks.withType<KotlinJvmCompile>().configureEach {
                compilerOptions.allWarningsAsErrors.set(true)
            }
            extensions.configure<KoverProjectExtension> {
                reports {
                    verify {
                        rule {
                            minBound(MIN_LINE_COVERAGE_PERCENT)
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val MIN_LINE_COVERAGE_PERCENT = 80
    }
}
