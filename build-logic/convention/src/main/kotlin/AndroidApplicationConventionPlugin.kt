import com.android.build.api.dsl.ApplicationExtension
import io.github.jabbottiii.aegis.buildlogic.BASE_PACKAGE
import io.github.jabbottiii.aegis.buildlogic.configureAndroidCommon
import io.github.jabbottiii.aegis.buildlogic.configureQualityGates
import io.github.jabbottiii.aegis.buildlogic.intVersionOf
import io.github.jabbottiii.aegis.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * The `app` module: SDK levels, R8, and release signing from the environment.
 *
 * Release signing reads `AEGIS_SIGNING_STORE_FILE`, `AEGIS_SIGNING_STORE_PASSWORD`,
 * `AEGIS_SIGNING_KEY_ALIAS`, and `AEGIS_SIGNING_KEY_PASSWORD`. Without them the release build is
 * unsigned. Key material never lives in the repo (DEVELOPMENT.md § Secrets and signing).
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // It sets Aegis's applicationId and release signing. Fixture APKs (testing/fixtures) need
            // their own plugin, with their own IDs and a test-named key (TESTING §2).
            require(path == ":app") { "aegis.android.application is only for :app, not $path" }
            pluginManager.apply("com.android.application")
            extensions.configure<ApplicationExtension> {
                configureAndroidCommon(this)
                namespace = BASE_PACKAGE
                defaultConfig {
                    applicationId = BASE_PACKAGE
                    targetSdk = libs.intVersionOf("sdk-target")
                    versionCode = providers.gradleProperty("aegis.versionCode").get().toInt()
                    versionName = providers.gradleProperty("aegis.versionName").get()
                }
                buildFeatures {
                    buildConfig = true
                }
                val releaseSigning = releaseSigningFromEnvironment()
                if (releaseSigning != null) {
                    signingConfigs.create("release") {
                        storeFile = file(releaseSigning.storeFile)
                        storePassword = releaseSigning.storePassword
                        keyAlias = releaseSigning.keyAlias
                        keyPassword = releaseSigning.keyPassword
                    }
                }
                buildTypes {
                    release {
                        isMinifyEnabled = true
                        isShrinkResources = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro",
                        )
                        if (releaseSigning != null) {
                            signingConfig = signingConfigs.getByName("release")
                        }
                    }
                }
                lint {
                    checkDependencies = true
                }
            }
            configureQualityGates()
        }
    }

    private class ReleaseSigning(
        val storeFile: String,
        val storePassword: String,
        val keyAlias: String,
        val keyPassword: String,
    )

    private fun Project.releaseSigningFromEnvironment(): ReleaseSigning? {
        fun env(name: String): String? = providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
        val storeFile = env("AEGIS_SIGNING_STORE_FILE") ?: return null
        return ReleaseSigning(
            storeFile = storeFile,
            storePassword = env("AEGIS_SIGNING_STORE_PASSWORD") ?: error("AEGIS_SIGNING_STORE_PASSWORD is not set"),
            keyAlias = env("AEGIS_SIGNING_KEY_ALIAS") ?: error("AEGIS_SIGNING_KEY_ALIAS is not set"),
            keyPassword = env("AEGIS_SIGNING_KEY_PASSWORD") ?: error("AEGIS_SIGNING_KEY_PASSWORD is not set"),
        )
    }
}
