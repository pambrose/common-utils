description = "Ktor HTTP client extension utilities"

dependencies {
    implementation(project(":core-utils"))

    api(libs.ktor.client.core)

    testImplementation(libs.ktor.client.cio)
    testImplementation(libs.ktor.client.mock)
}
