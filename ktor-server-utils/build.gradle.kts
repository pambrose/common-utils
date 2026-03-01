description = "ktor-server-utils"

dependencies {
    implementation(project(":core-utils"))

    implementation(libs.kotlin.reflect)
    implementation(libs.ktor.server.core)

    compileOnly(libs.jakarta.servlet.api)

    testImplementation(libs.jakarta.servlet.api)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotest)
    testImplementation(kotlin("test"))
}
