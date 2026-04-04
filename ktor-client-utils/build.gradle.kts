description = "Ktor HTTP client extension utilities"

dependencies {
    implementation(project(":core-utils"))

    implementation(libs.ktor.client.core)

    testImplementation(libs.kotest)
    testImplementation(libs.ktor.client.cio)
    testImplementation(libs.ktor.client.mock)
    testImplementation(kotlin("test"))
}
