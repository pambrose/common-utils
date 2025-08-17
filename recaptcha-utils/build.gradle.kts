description = "recaptcha-utils"

dependencies {
    implementation(project(":core-utils"))

    implementation(libs.kotlinx.serialization)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.html.builder)
}
