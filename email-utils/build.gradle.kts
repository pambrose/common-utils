plugins {
    alias(libs.plugins.kotlin.serialization)
}

description = "Email sending utilities with template support"

dependencies {
    implementation(project(":core-utils"))

    implementation(libs.resend)
    implementation(libs.kotlinx.html)
    implementation(libs.ktor.http)
}
