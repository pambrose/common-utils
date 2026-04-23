description = "Embedded Jetty server configuration utilities"

dependencies {
    implementation(project(":core-utils"))

    api(libs.jetty.servlet)

    testImplementation(libs.kotest)
    testImplementation(libs.mockk)
    testImplementation(kotlin("test"))
}
