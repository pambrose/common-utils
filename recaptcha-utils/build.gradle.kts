description = "recaptcha-utils"

plugins {
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core-utils"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.html.builder)

    testImplementation(libs.kotest)
    testImplementation(kotlin("test"))
}
