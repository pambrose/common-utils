plugins {
    alias(libs.plugins.kotlin.serialization)
}

description = "Google reCAPTCHA verification utilities"

dependencies {
    implementation(project(":core-utils"))

    implementation(libs.bundles.ktor.client.json)
    implementation(libs.ktor.server.core)
    implementation(libs.kotlinx.html)
}
