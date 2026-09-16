plugins {
    alias(libs.plugins.kotlin.serialization)
}

description = "Email sending utilities with template support"

dependencies {
    api(project(":core-utils"))

    api(libs.resend)
    api(libs.kotlinx.html)
    api(libs.ktor.http)
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.mockk)
    testImplementation(libs.logback.classic)
}
