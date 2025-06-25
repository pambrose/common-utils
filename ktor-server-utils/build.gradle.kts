description = "ktor-server-utils"

dependencies {
    implementation(project(":core-utils"))

    implementation(libs.kotlin.reflect)
    implementation(libs.ktor.server.core)
}
