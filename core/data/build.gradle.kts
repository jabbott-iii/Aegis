plugins {
    alias(libs.plugins.aegis.android.library)
    alias(libs.plugins.aegis.hilt)
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:security"))
    implementation(libs.room3.runtime)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)

    androidTestImplementation(project(":testing:common"))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlin.test)
    // Only the test database is generated so far. app.db's entities arrive with Phase 1.
    kspAndroidTest(libs.room3.compiler)
}
