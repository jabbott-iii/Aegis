plugins {
    alias(libs.plugins.aegis.android.library)
    alias(libs.plugins.aegis.hilt)
}

dependencies {
    implementation(project(":core:common"))
    // Keep Tink an implementation detail: checkModuleGraph fails if it leaks onto another
    // module's compile classpath.
    implementation(libs.tink.android)

    testImplementation(project(":testing:common"))
    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)

    androidTestImplementation(project(":testing:common"))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlin.test)
}
