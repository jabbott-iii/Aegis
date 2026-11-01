package io.github.jabbottiii.aegis.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project

/** SDK levels, Java level, lint, and test options shared by every Android module. */
internal fun Project.configureAndroidCommon(android: CommonExtension) {
    android.compileSdk = libs.intVersionOf("sdk-compile")
    android.defaultConfig.minSdk = libs.intVersionOf("sdk-min")
    android.defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    android.compileOptions.sourceCompatibility = javaVersion()
    android.compileOptions.targetCompatibility = javaVersion()

    android.lint.abortOnError = true
    android.lint.checkReleaseBuilds = true
    android.lint.sarifReport = true
    // Security checks that are only warnings by default. In Aegis they fail the build.
    android.lint.error +=
        setOf(
            "TrustAllX509TrustManager",
            "BadHostnameVerifier",
            "AllowAllHostnameVerifier",
            "SSLCertificateSocketFactoryGetInsecure",
            "SSLCertificateSocketFactoryCreateSocket",
            "UnsafeImplicitIntentLaunch",
            "ExportedService",
            "ExportedReceiver",
            "ExportedContentProvider",
            "ExportedPreferenceActivity",
            "SetJavaScriptEnabled",
            "UnspecifiedRegisterReceiverFlag",
            "WorldReadableFiles",
            "WorldWriteableFiles",
            // Network Security Config (NFR-SEC-04) and dynamic code loading.
            "AcceptsUserCertificates",
            "InsecureBaseConfiguration",
            "UnsafeDynamicallyLoadedCode",
        )

    android.testOptions.unitTests.isReturnDefaultValues = false
    android.testOptions.animationsDisabled = true

    configureKotlinCompiler()
}
