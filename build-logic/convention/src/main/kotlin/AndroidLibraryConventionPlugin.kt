import com.android.build.api.dsl.LibraryExtension
import io.github.jabbottiii.aegis.buildlogic.aegisNamespace
import io.github.jabbottiii.aegis.buildlogic.configureAndroidCommon
import io.github.jabbottiii.aegis.buildlogic.configureQualityGates
import io.github.jabbottiii.aegis.buildlogic.intVersionOf
import io.github.jabbottiii.aegis.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Android library modules. The namespace follows the Gradle path (`:core:ui` → `…aegis.core.ui`). */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            extensions.configure<LibraryExtension> {
                configureAndroidCommon(this)
                namespace = aegisNamespace
                testOptions.targetSdk = libs.intVersionOf("sdk-target")
                lint.targetSdk = libs.intVersionOf("sdk-target")
                if (file("consumer-rules.pro").exists()) {
                    defaultConfig.consumerProguardFiles("consumer-rules.pro")
                }
            }
            configureQualityGates()
        }
    }
}
