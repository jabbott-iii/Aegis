plugins {
    alias(libs.plugins.aegis.jvm.library)
}

dependencies {
    api(project(":core:common"))
    api(libs.kotlinx.coroutines.test)
}
