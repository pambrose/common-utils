description = "Email sending utilities with template support"

plugins {
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core-utils"))

    implementation(libs.resend)
    implementation(libs.ktor.server.html.builder)

    testImplementation(libs.kotest)
    testImplementation(kotlin("test"))
}
