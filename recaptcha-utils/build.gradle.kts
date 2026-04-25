plugins {
    alias(libs.plugins.kotlin.serialization)
}

description = "Google reCAPTCHA verification utilities"

dependencies {
    implementation(project(":core-utils"))

    implementation(libs.bundles.ktor.client.json)
    api(libs.ktor.server.core)
    api(libs.kotlinx.html)
}
