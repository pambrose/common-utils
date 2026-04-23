plugins {
    alias(libs.plugins.kotlin.serialization)
}

description = "Google reCAPTCHA verification utilities"

dependencies {
    implementation(project(":core-utils"))

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.core)
    implementation(libs.kotlinx.html)
}
