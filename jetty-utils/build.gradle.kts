description = "jetty-utils"

dependencies {
    implementation(project(":core-utils"))

    // implementation(libs.jetty.server)
    implementation(libs.jetty.servlet)

    testImplementation(libs.kotest)
    testImplementation(libs.mockk)
    testImplementation(kotlin("test"))
}
