description = "email-utils"

dependencies {
    implementation(project(":core-utils"))

    implementation(libs.resend)
    implementation(libs.ktor.server.html.builder)
}
